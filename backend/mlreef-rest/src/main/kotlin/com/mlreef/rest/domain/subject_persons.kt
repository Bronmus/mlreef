package com.mlreef.rest.domain

import com.mlreef.rest.domain.converters.AccessLevelConverter
import com.mlreef.rest.domain.helpers.GroupOfUser
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import javax.persistence.*

@Table(name = "subject")
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Deprecated("To be deleted", replaceWith = ReplaceWith("Account and Group"))
abstract class Subject(
    id: UUID,
    val slug: String,
    val name: String,
    @Column(name = "gitlab_id")
    val gitlabId: Long?,
    version: Long? = null,
    createdAt: ZonedDateTime? = null,
    updatedAt: ZonedDateTime? = null
) : AuditEntity(id, version, createdAt, updatedAt)

@Entity
@Table(name = "membership")
// This is a speculative Entity, based on early ideas.
// It most probably needs to be refactored when implementing new features
class Membership(
    @Id @Column(name = "id", length = 16, unique = true, nullable = false) val id: UUID,
    @Column(name = "person_id")
    val personId: UUID,
    @Column(name = "group_id")
    val groupId: UUID,

    @Column(name = "access_level")
    @Convert(converter = AccessLevelConverter::class)
    val accessLevel: AccessLevel?
) {
    fun copy(accessLevel: AccessLevel?): Membership =
        Membership(this.id, this.personId, this.groupId, accessLevel)
}

@Entity
@Deprecated("To be deleted", replaceWith = ReplaceWith("Account"))
class Person(
    id: UUID,
    override val slug: String,
    override val name: String,
    override val gitlabId: Long?,
//    @OneToOne(mappedBy = "person", fetch = FetchType.LAZY) //, cascade = [CascadeType.ALL])
//    var account: Account? = null,
    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    @JoinColumn(
        name = "person_id",
        foreignKey = ForeignKey(name = "membership_person_person_id_fkey")
    )
    @Fetch(value = FetchMode.SUBSELECT)
//    @LazyCollection(LazyCollectionOption.FALSE)
    val memberships: List<Membership> = listOf(),
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role")
    val userRole: UserRole = UserRole.UNDEFINED,
    @Column(name = "terms_accepted_at")
    val termsAcceptedAt: Instant? = null,
    @Column(name = "has_newsletters")
    val hasNewsletters: Boolean = false,
    version: Long? = null,
    createdAt: ZonedDateTime? = null,
    updatedAt: ZonedDateTime? = null
) : Subject(id, slug, name, gitlabId, version, createdAt, updatedAt) {
    fun copy(
        slug: String? = null,
        name: String? = null,
        memberships: List<Membership>? = null,
        userRole: UserRole? = null,
        termsAcceptedAt: Instant? = null,
        hasNewsletters: Boolean? = null,
        account: Account? = null,
    ): Person = Person(
        id = this.id,
        slug = slug ?: this.slug,
        name = name ?: this.name,
        gitlabId = gitlabId,
        memberships = memberships ?: this.memberships,
        termsAcceptedAt = termsAcceptedAt ?: this.termsAcceptedAt,
        userRole = userRole ?: this.userRole,
        hasNewsletters = hasNewsletters ?: this.hasNewsletters,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
}

@Entity
class Group(
    id: UUID,
    override val slug: String,
    override val name: String,
    override val gitlabId: Long?,
    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.REMOVE])
    @JoinColumn(
        name = "group_id",
        foreignKey = ForeignKey(name = "membership_group_group_id_fkey")
    )
    @Fetch(value = FetchMode.SUBSELECT)
//    @LazyCollection(LazyCollectionOption.FALSE)
    val members: List<Membership> = listOf(),
    version: Long? = null,
    createdAt: ZonedDateTime? = null,
    updatedAt: ZonedDateTime? = null
) : Subject(id, slug, name, gitlabId, version, createdAt, updatedAt) {
    fun copy(
        slug: String? = null,
        name: String? = null,
        members: List<Membership>? = null
    ): Group = Group(
        id = this.id,
        slug = slug ?: this.slug,
        name = name ?: this.name,
        gitlabId = gitlabId,
        members = members ?: this.members,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )


    fun toGroupOfUser(accessLevel: AccessLevel?) = GroupOfUser(
        this.id,
        this.gitlabId,
        this.name,
        accessLevel
    )
}
