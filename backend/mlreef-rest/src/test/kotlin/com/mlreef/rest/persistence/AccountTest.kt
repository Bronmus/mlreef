package com.mlreef.rest.persistence

import com.mlreef.rest.AccountRepository
import com.mlreef.rest.domain.Account
import com.mlreef.rest.domain.AccountToken
import com.mlreef.rest.domain.UserRole
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*
import java.util.UUID.randomUUID


class AccountTest : AbstractRepositoryTest() {

    @Autowired
    private lateinit var repository: AccountRepository

    private fun createEntity(
        slug: String = "slug",
        username: String = "username",
        email: String = "email",
        changeAccountToken: String? = null
    ): Pair<UUID, Account> {
        val id = randomUUID()
        AccountToken(randomUUID(), id, "token")
        val entity = Account(
            id = id,
            username = username,
            passwordEncrypted = "enc",
            email = email,
            lastLogin = null,
            changeAccountToken = changeAccountToken,
            slug = slug,
            name = "name",
            gitlabId = 1L,
            hasNewsletters = true,
            userRole = UserRole.DEVELOPER,
            termsAcceptedAt = Instant.now()
        )
        return Pair(id, entity)
    }

    @BeforeEach
    fun prepare() {
        truncateDbTables(listOf("account", "account_token"), cascade = true)
    }

    @Transactional
    @Rollback
    @Test
    fun `find works`() {
        val (id, entity) = createEntity()
        Assertions.assertThat(repository.findByIdOrNull(id)).isNull()
        repository.save(entity)
        Assertions.assertThat(repository.findByIdOrNull(id)).isNotNull
    }

    @Transactional
    @Rollback
    @Test
    fun `save works`() {
        val (id, entity) = createEntity()
        Assertions.assertThat(repository.findByIdOrNull(id)).isNull()
        val saved = repository.save(entity)
        Assertions.assertThat(saved).isNotNull
        checkAfterCreated(saved)
        Assertions.assertThat(repository.findByIdOrNull(id)).isNotNull
    }

    @Transactional
    @Rollback
    @Test
    fun `must not save duplicate id`() {
        val (_, entity1) = createEntity("slug1", "username1", "email1@dot.com")
        val (_, entity2) = createEntity("slug2", "username2", "email2@dot.com")
        repository.save(entity1)
        commitAndFail {
            repository.save(entity2)
        }
    }

    @Transactional
    @Rollback
    @Test
    fun `must not save duplicate slug`() {
        commitAndFail {
            repository.save(createEntity("slug1", "username1", "email1@dot.com").second)
            repository.save(createEntity("slug1", "username2", "email2@dot.com").second)
        }
    }

    @Transactional
    @Rollback
    @Test
    fun `must not save duplicate username`() {
        commitAndFail {
            repository.save(createEntity("slug1", "username1", "email1@dot.com").second)
            repository.save(createEntity("slug2", "username1", "email2@dot.com").second)
        }
    }

    @Transactional
    @Rollback
    @Test
    fun `must not save duplicate email`() {
        commitAndFail {
            repository.save(createEntity("slug1", "username1", "email1@dot.com").second)
            repository.save(createEntity("slug2", "username2", "email1@dot.com").second)
        }
    }

    @Transactional
    @Rollback
    @Test
    fun `update works`() {
        val (_, entity) = createEntity()
        val saved = repository.save(entity)
        val newValue = "newname"
        val copy = saved.copy(username = newValue)
        val updated = repository.save(copy)
        Assertions.assertThat(updated).isNotNull
        Assertions.assertThat(updated.username).isEqualTo(newValue)
    }

    @Transactional
    @Rollback
    @Test
    fun `delete works`() {
        val (_, entity) = createEntity()
        val saved = repository.save(entity)
        repository.delete(saved)
        Assertions.assertThat(saved).isNotNull
    }
}
