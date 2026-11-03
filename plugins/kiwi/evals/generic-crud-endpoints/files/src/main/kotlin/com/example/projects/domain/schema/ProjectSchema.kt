package com.example.projects.domain.schema

import com.example.projects.domain.Project
import com.example.projects.domain.ProjectStatus
import de.kiwi.api.schema.ColumnType
import de.kiwi.api.schema.EntitySchema
import de.kiwi.api.schema.FieldsetsAvailable

object ProjectSchema : EntitySchema<Project>() {
    val id by entityId()
    val title by column<String>(Project::title)
    val description by column<String?>(Project::description)
    val owner by column<String>(Project::owner)
    val status by column<ProjectStatus, String>(Project::status, type = ColumnType.enum())
}

object ProjectFieldsets : FieldsetsAvailable<Project>(ProjectSchema) {
    override val default by defineFieldset { includeAllColumns() }
    val short by defineFieldset { include(ProjectSchema.id, ProjectSchema.title, ProjectSchema.status) }
}
