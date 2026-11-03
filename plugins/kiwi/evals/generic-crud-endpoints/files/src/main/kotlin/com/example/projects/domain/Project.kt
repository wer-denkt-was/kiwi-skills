package com.example.projects.domain

import de.kiwi.api.domain.BaseEntity
import de.kiwi.api.domain.EntityId
import jakarta.persistence.*

@Embeddable
class ProjectId(value: Long) : EntityId(value)

enum class ProjectStatus { Planned, Active, Done }

@Entity
@Table(name = "project")
class Project(
    id: ProjectId,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "description", nullable = true)
    var description: String?,

    @Column(name = "owner", nullable = false)
    val owner: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ProjectStatus = ProjectStatus.Planned,
) : BaseEntity<ProjectId>(id) {
    companion object {
        fun create(title: String, description: String?, owner: String) = Project(
            id = EntityId.generate(),
            title = title,
            description = description,
            owner = owner,
        )
    }
}
