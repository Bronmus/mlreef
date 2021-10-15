package com.mlreef.rest.domain

import com.mlreef.rest.domain.helpers.ProjectOfUser
import com.mlreef.rest.domain.marketplace.Searchable
import com.mlreef.rest.domain.marketplace.SearchableTag
import com.mlreef.rest.domain.marketplace.Star
import com.mlreef.rest.exceptions.ParsingException
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.ZonedDateTime
import java.util.UUID
import javax.persistence.*

enum class ProjectType {
    DATA_PROJECT,
    CODE_PROJECT;

    companion object {
        fun valueOf(typeStr: String): ProjectType {
            return when (typeStr.trim().toUpperCase()) {
                "DATA", "DATA-PROJECT", "DATAPROJECT", "DATA_PROJECT" -> DATA_PROJECT
                "CODE", "CODE-PROJECT", "CODEPROJECT", "CODE_PROJECT" -> CODE_PROJECT
                else -> throw ParsingException("Cannot parse project type $typeStr")
            }
        }

        fun valueOfOrNull(typeStr: String?): ProjectType? {
            return when (typeStr?.trim()?.toUpperCase()) {
                "DATA", "DATA-PROJECT", "DATAPROJECT", "DATA_PROJECT" -> DATA_PROJECT
                "CODE", "CODE-PROJECT", "CODEPROJECT", "CODE_PROJECT" -> CODE_PROJECT
                else -> null
            }
        }
    }
}

@Table(name = "mlreef_project")
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "PROJECT_TYPE")
abstract class Project(
    id: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "PROJECT_TYPE", insertable = false, updatable = false)
    val type: ProjectType,

    override val slug: String,
    val url: String,

    @Column(unique = true)
    override val name: String,

    override val description: String,

    @Column(name = "owner_id")
    override val ownerId: UUID,

    @Column(name = "gitlab_namespace")
    val gitlabNamespace: String,

    @Column(name = "gitlab_path")
    val gitlabPath: String,

    @Column(name = "gitlab_path_with_namespace")
    val gitlabPathWithNamespace: String = "$gitlabNamespace/$gitlabPath",

    @Column(name = "gitlab_id")
    val gitlabId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_scope")
    override val visibilityScope: VisibilityScope = VisibilityScope.default(),

    @Column(name = "forks_count")
    override val forksCount: Int = 0,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_inputdatatypes",
        joinColumns = [JoinColumn(name = "project_id")],
        inverseJoinColumns = [JoinColumn(name = "data_type_id")])
    override val inputDataTypes: MutableSet<DataType> = mutableSetOf(),

    @Column(name = "global_slug", length = 64)
    override val globalSlug: String?,

    @ManyToMany(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    @Cascade(org.hibernate.annotations.CascadeType.SAVE_UPDATE)
    @JoinTable(
        name = "projects_tags",
        joinColumns = [JoinColumn(name = "project_id", referencedColumnName = "id", updatable = false, foreignKey = ForeignKey(name = "project_tags_project_id"))],
        inverseJoinColumns = [JoinColumn(name = "tag_id", referencedColumnName = "id", updatable = false, foreignKey = ForeignKey(name = "project_tags_tag_id"))]
    )
    @Fetch(value = FetchMode.SELECT)
    override val tags: MutableSet<SearchableTag> = mutableSetOf(),

    @Column(name = "stars_count")
    private var _starsCount: Int = 0,

    @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "project_id", foreignKey = ForeignKey(name = "project_star_entry_id"), updatable = false)
    @Fetch(value = FetchMode.JOIN)
    override val stars: MutableSet<Star> = mutableSetOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forked_from_id")
    var forkParent: Project? = null,

    @OneToMany(mappedBy = "forkParent", fetch = FetchType.LAZY)
    val forkChildren: MutableSet<Project> = mutableSetOf(),

    @OneToOne(fetch = FetchType.LAZY) //, cascade = [CascadeType.ALL])
    @JoinColumn(name = "cover_picture_id")
    var cover: MlreefFile? = null,

    version: Long? = null,
    createdAt: ZonedDateTime? = null,
    updatedAt: ZonedDateTime? = null,
) : AuditEntity(id, version, createdAt, updatedAt), Searchable {

    fun toProjectOfUser(accessLevel: AccessLevel?) = ProjectOfUser(
        id = this.id,
        name = this.name,
        accessLevel = accessLevel
    )

    abstract fun <T : Project> copy(
        id: UUID = this.id,
        url: String? = null,
        slug: String? = null,
        name: String? = null,
        description: String? = null,
        gitlabNamespace: String? = null,
        gitlabPathWithNamespace: String? = null,
        gitlabPath: String? = null,
        gitlabId: Long? = null,
        globalSlug: String? = null,
        stars: Collection<Star>? = null,
        inputDataTypes: Collection<DataType>? = null,
        tags: Collection<SearchableTag>? = null,
        version: Long? = null,
        createdAt: ZonedDateTime? = null,
        updatedAt: ZonedDateTime? = null,
        visibilityScope: VisibilityScope? = null,
        forkParent: Project? = null,
        forkChildren: MutableSet<Project>? = null,
        cover: MlreefFile? = null,
    ): T

    fun addStar(account: Account): Project {
        val existing = stars.find { it.subjectId == account.id }
        return if (existing != null) {
            this
        } else {
            val newStars = this.stars.toMutableList().apply {
                add(Star(
                    subjectId = account.id,
                    projectId = this@Project.id
                ))
            }

            this.clone(
                stars = newStars
            )
        }
    }

    fun removeStar(account: Account): Project {
        val existing = stars.find { it.subjectId == account.id }
        return if (existing != null) {
            val newStars = this.stars.filter { it.subjectId != account.id }
            this.clone(
                stars = newStars
            )
        } else {
            this
        }
    }

    override val starsCount: Int
        get() = _starsCount

    @PrePersist
    fun prePersist() {
        @Suppress("SENSELESS_COMPARISON")
        // Because of a mocking error 'this.stars' sometimes is null during tests
        this._starsCount = if (this.stars != null) this.stars.size else 0
    }


    fun addTags(tags: List<SearchableTag>): Project {
        return this.clone(
            tags = this.tags.toMutableSet().apply { addAll(tags) }
        )
    }

    fun addInputDataTypes(dataTypes: List<DataType>): Project {
        return this.clone(
            inputDataTypes = this.inputDataTypes.toMutableSet().apply { addAll(dataTypes) }
        )
    }

    fun removeInputDataTypes(dataTypes: List<DataType>): Project {
        return this.clone(
            inputDataTypes = this.inputDataTypes.toMutableSet().apply { removeAll(dataTypes) }
        )
    }

    abstract fun clone(
        name: String? = null,
        description: String? = null,
        visibilityScope: VisibilityScope? = null,
        stars: Collection<Star>? = null,
        outputDataTypes: Collection<DataType>? = null,
        inputDataTypes: Collection<DataType>? = null,
        tags: Collection<SearchableTag>? = null,
    ): Project

    fun isPublic(): Boolean {
        return this.visibilityScope == VisibilityScope.PUBLIC
    }
}
