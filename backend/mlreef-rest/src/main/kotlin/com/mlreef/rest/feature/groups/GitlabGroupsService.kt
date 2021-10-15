package com.mlreef.rest.feature.groups

import com.mlreef.rest.AccountRepository
import com.mlreef.rest.GroupRepository
import com.mlreef.rest.MembershipRepository
import com.mlreef.rest.annotations.RefreshUserInformation
import com.mlreef.rest.api.CurrentUserService
import com.mlreef.rest.domain.AccessLevel
import com.mlreef.rest.domain.Account
import com.mlreef.rest.domain.Group
import com.mlreef.rest.domain.VisibilityScope
import com.mlreef.rest.domain.helpers.GroupOfUser
import com.mlreef.rest.domain.helpers.UserInGroup
import com.mlreef.rest.exceptions.*
import com.mlreef.rest.external_api.gitlab.*
import com.mlreef.rest.feature.auth.UserResolverService
import com.mlreef.rest.feature.system.ReservedNamesService
import com.mlreef.rest.utils.Slugs
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.*
import java.util.UUID.randomUUID
import javax.transaction.Transactional

interface GroupsService {
    fun getUserGroupsList(token: String, userId: UUID? = null): List<GroupOfUser>
    fun getGroupById(groupId: UUID): Group?
    fun getUsersInGroup(groupId: UUID): List<UserInGroup>
    fun createGroup(ownerToken: String, groupName: String, path: String? = null, visibility: VisibilityScope?): Group
    fun updateGroup(groupId: UUID, groupName: String? = null, path: String? = null): Group
    fun deleteGroup(groupId: UUID)
    fun addUserToGroup(groupId: UUID, userId: UUID, accessLevel: AccessLevel? = AccessLevel.GUEST): List<UserInGroup>
    fun editUserInGroup(groupId: UUID, userId: UUID, accessLevel: AccessLevel): List<UserInGroup>
    fun addUsersToGroup(groupId: UUID, users: List<UserInGroup>): List<UserInGroup>
    fun deleteUserFromGroup(groupId: UUID, userId: UUID): List<UserInGroup>
    fun deleteUsersFromGroup(groupId: UUID, users: List<UserInGroup>): List<UserInGroup>
    fun checkUserInGroup(groupId: UUID, userToken: String? = null, userId: UUID? = null, userName: String? = null, email: String? = null, userGitlabId: Long? = null, personId: UUID? = null): Boolean
    fun checkAvailability(userToken: String, creatorId: UUID, groupName: String): String
}

@Service
class GitlabGroupsService(
    private val reservedNamesService: ReservedNamesService,
    private val groupsRepository: GroupRepository,
    private val membershipRepository: MembershipRepository,
    private val accountRepository: AccountRepository,
    private val gitlabRestClient: GitlabRestClient,
    private val currentUserService: CurrentUserService,
    private val userResolverService: UserResolverService,
) : GroupsService {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    override fun getUserGroupsList(token: String, userId: UUID?): List<GroupOfUser> {
        val account = userResolverService.resolveAccount(userId = userId)
            ?: throw UserNotFoundException(userId = userId)

        val gitlabsGroupsIds = gitlabRestClient
            .userGetUserGroups(token)
            .map { it.id }

        return gitlabsGroupsIds.map {
            try {
                val gitlabAccessLevel = gitlabRestClient.adminGetGroupMembers(it).first { user -> user.id == account.gitlabId }.accessLevel
                val groupInDb = groupsRepository.findByGitlabId(it) ?: throw GroupNotFoundException(gitlabId = it)
                groupInDb.toGroupOfUser(gitlabAccessLevel.toAccessLevel())
            } catch (ex: Exception) {
                log.error("Unable to get user's group membership for gitlab group $it . Exception: $ex.")
                null
            }
        }.filterNotNull()
    }

    override fun getGroupById(groupId: UUID): Group? {
        return groupsRepository.findByIdOrNull(groupId)
    }

    override fun getUsersInGroup(groupId: UUID): List<UserInGroup> {
        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)
        val groupGitlabId = group.gitlabId ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab")

        val gitlabUsersMap = gitlabRestClient
            .adminGetGroupMembers(groupGitlabId)
            .map { Pair(it.id, it.accessLevel) }.toMap()

        val accounts = gitlabUsersMap.map {
            accountRepository.findAccountByGitlabId(it.key)
        }.filterNotNull()

        return accounts.map { it.toUserInGroup(gitlabUsersMap[it.gitlabId].toAccessLevel()) }
    }

    @Transactional
    @RefreshUserInformation(userId = "#ownerId")
    override fun createGroup(ownerToken: String, groupName: String, path: String?, visibility: VisibilityScope?): Group {
        userResolverService.resolveAccount(userToken = ownerToken)
            ?: throw IncorrectCredentialsException("Not authenticated")

        val slug = Slugs.toSlug(groupName)
        reservedNamesService.assertGroupNameIsNotReserved(groupName)

        //FIXME: Where is dtype?
        if (groupsRepository.findByName(groupName).size > 0) throw ConflictException("Group $groupName already exists")

        val gitlabGroup = gitlabRestClient.userCreateGroup(
            token = ownerToken,
            groupName = groupName,
            path = path ?: slug,
            visibility = visibility?.toGitlabVisibility() ?: GitlabVisibility.PRIVATE
        )

        val group = Group(id = randomUUID(), slug = slug, name = groupName, gitlabId = gitlabGroup.id)

        return groupsRepository.save(group)
    }

    override fun updateGroup(groupId: UUID, groupName: String?, path: String?): Group {
        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)
        val gitlabId = group.gitlabId ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab")

        reservedNamesService.assertGroupNameIsNotReserved(groupName ?: group.name)

        val gitlabGroup = gitlabRestClient.adminUpdateGroup(
            groupId = gitlabId,
            groupName = groupName,
            path = path
        )

        val newGroup = group.copy(name = gitlabGroup.name, slug = gitlabGroup.name)
        groupsRepository.save(newGroup)

        return newGroup
    }

    override fun deleteGroup(groupId: UUID) {
        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)
        val gitlabId = group.gitlabId ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab")

        gitlabRestClient.adminDeleteGroup(groupId = gitlabId)

        groupsRepository.delete(group)
    }

    @RefreshUserInformation(userId = "#userId")
    override fun addUserToGroup(groupId: UUID, userId: UUID, accessLevel: AccessLevel?): List<UserInGroup> {
        val user = userResolverService.resolveAccount(userId = userId)
            ?: throw UserNotFoundException(userId = userId)

        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)

        gitlabRestClient.adminAddUserToGroup(
            groupId = group.gitlabId
                ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab"),
            userId = user.gitlabId
                ?: throw UnknownUserException("Person ${user.id} is not connected to gitlab"),
            accessLevel = (accessLevel ?: AccessLevel.GUEST).toGitlabAccessLevel()
        )

        return getUsersInGroup(groupId)
    }

    @RefreshUserInformation(list = "#users")
    override fun addUsersToGroup(groupId: UUID, users: List<UserInGroup>): List<UserInGroup> {
        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)
        val gitlabGroupId = group.gitlabId ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab")

        val usersIds = users.map {
            val account = userResolverService.resolveAccount(
                userId = it.id,
                email = it.userName,
                userName = it.userName,
                gitlabId = it.gitlabId
            )

            if (account == null || account.gitlabId == null) {
                null
            } else {
                Pair(account.gitlabId!!, it.accessLevel)
            }
        }.filterNotNull().toMap()

        val accountsAdded = usersIds.map {
            try {
                val gitlabUserInGroup = gitlabRestClient
                    .adminAddUserToGroup(groupId = gitlabGroupId, userId = it.key, accessLevel = it.value.toGitlabAccessLevel())
                val account = accountRepository.findAccountByGitlabId(gitlabUserInGroup.id)
                    ?: throw UserNotFoundException(gitlabId = gitlabUserInGroup.id)
                Pair(account, gitlabUserInGroup.accessLevel.toAccessLevel())
            } catch (ex: Exception) {
                log.error("Unable to add user to the group ${group.name}. Exception: $ex.")
                null
            }
        }.filterNotNull()

        return accountsAdded.map { it.first.toUserInGroup(it.second) }
    }

    @RefreshUserInformation(userId = "#userId")
    override fun editUserInGroup(groupId: UUID, userId: UUID, accessLevel: AccessLevel): List<UserInGroup> {
        val userToBeEdit = userResolverService.resolveAccount(userId = userId)
            ?: throw UserNotFoundException(userId = userId)

        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)

        gitlabRestClient.adminEditUserInGroup(
            groupId = group.gitlabId
                ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab"),
            userId = userToBeEdit.gitlabId
                ?: throw UnknownUserException("Person ${userToBeEdit.id} is not connected to gitlab"),
            accessLevel = accessLevel.toGitlabAccessLevel()!!
        )

        return getUsersInGroup(groupId)
    }

    @RefreshUserInformation(userId = "#userId")
    override fun deleteUserFromGroup(groupId: UUID, userId: UUID): List<UserInGroup> {
        val userToBeDeleted = userResolverService.resolveAccount(userId = userId)
            ?: throw UserNotFoundException(userId = userId)

        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)

        gitlabRestClient.adminDeleteUserFromGroup(
            groupId = group.gitlabId ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab"),
            userId = userToBeDeleted.gitlabId
                ?: throw UnknownUserException("Person ${userToBeDeleted.id} is not connected to gitlab")
        )

        return getUsersInGroup(groupId)
    }

    @RefreshUserInformation(list = "#users")
    override fun deleteUsersFromGroup(groupId: UUID, users: List<UserInGroup>): List<UserInGroup> {
        val group = groupsRepository.findByIdOrNull(groupId) ?: throw GroupNotFoundException(groupId = groupId)
        val gitlabGroupId = group.gitlabId ?: throw UnknownGroupException("Group $groupId is not connected to Gitlab")

        val usersIds = users.map {
            try {
                when {
                    it.gitlabId != null -> it.gitlabId
                    it.email != null -> accountRepository.findOneByEmail(it.email!!)?.gitlabId
                    it.userName != null -> accountRepository.findOneByUsername(it.userName ?: "")?.gitlabId
                    else -> null
                }
            } catch (ex: Exception) {
                null
            }
        }.filterNotNull()

        usersIds.forEach {
            try {
                gitlabRestClient.adminDeleteUserFromGroup(groupId = gitlabGroupId, userId = it)
            } catch (ex: Exception) {
                log.error("Unable to delete user $it from the group ${group.name}. Exception: $ex.")
            }
        }

        return getUsersInGroup(groupId)
    }

    override fun checkUserInGroup(groupId: UUID, userToken: String?, userId: UUID?, userName: String?, email: String?, userGitlabId: Long?, personId: UUID?): Boolean {
        try {
            val account: Account = userResolverService.resolveAccount(userName, userId, userGitlabId, userToken, email)
                ?: return false
            assertMemberOfGroup(groupId, accountId = account.id)
            return true
        } catch (ex: Exception) {
            return false
        }
    }

    override fun checkAvailability(userToken: String, creatorId: UUID, groupName: String): String {
        val possibleSlug = Slugs.toSlug(groupName)
        reservedNamesService.assertGroupNameIsNotReserved(groupName)
        val existingGroups = groupsRepository.findBySlug(possibleSlug)
        if (existingGroups != null) {
            throw ConflictException(ErrorCode.GroupAlreadyExists, "Group exists for owner $possibleSlug $groupName")
        }
        return possibleSlug
    }


    private fun assertMemberOfGroup(groupUUID: UUID, accountId: UUID? = null) {
        val currentPersonId = userResolverService.resolveAccount(userId = accountId)?.id
            ?: currentUserService.account().id
        membershipRepository.findByPersonIdAndGroupId(currentPersonId, groupUUID)
            ?: throw AccessDeniedException("User is not in group or not allowed to view the group")
    }
}

