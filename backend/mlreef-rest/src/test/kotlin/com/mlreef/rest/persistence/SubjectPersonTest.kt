package com.mlreef.rest.persistence

import com.mlreef.rest.PersonRepository
import com.mlreef.rest.domain.Person
import com.mlreef.rest.domain.UserRole
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.annotation.Rollback
import java.time.Instant
import java.util.*
import java.util.UUID.randomUUID
import javax.transaction.Transactional

@Disabled("Will be deleted in the next commit")
class SubjectPersonTest : AbstractRepositoryTest() {
    @Autowired
    private lateinit var repository: PersonRepository

    private fun createEntity(): Pair<UUID, Person> {
        val id = randomUUID()
        val entity = Person(id, "slug", "name", 1L, hasNewsletters = true,
            userRole = UserRole.DEVELOPER,
            termsAcceptedAt = Instant.now())
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
    fun `update works`() {
        val (_, entity) = createEntity()
        val saved = repository.save(entity)
        val newValue = "newname"
        val copy = saved.copy(slug = newValue)
        val updated = repository.save(copy)
        Assertions.assertThat(updated).isNotNull
//        checkAfterUpdated(updated)
        Assertions.assertThat(updated.slug).isEqualTo(newValue)
    }

    @Transactional
    @Rollback
    @Test
    fun `delete works`() {
        val (_, entity) = createEntity()
        val saved = repository.save(entity)
        repository.delete(saved)
        Assertions.assertThat(saved).isNotNull
        checkAfterCreated(saved)
    }
}
