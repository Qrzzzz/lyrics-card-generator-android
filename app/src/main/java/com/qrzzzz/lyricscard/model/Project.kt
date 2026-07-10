package com.qrzzzz.lyricscard.model

data class Project(
    val id: String,
    val name: String,
    val spec: RenderSpec,
    val thumbnailPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExportedAt: Long? = null,
) {
    val coverAssetId: String?
        get() = spec.song.coverAssetId
}

data class ProjectSummary(
    val id: String,
    val name: String,
    val schemaVersion: Int,
    val rendererVersion: String,
    val coverAssetId: String?,
    val thumbnailPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExportedAt: Long?,
)

data class ProjectViolation(
    val path: String,
    val message: String,
)

class InvalidProjectException(
    val violations: List<ProjectViolation>,
) : IllegalArgumentException(
    violations.joinToString(
        prefix = "Invalid project: ",
        separator = "; ",
    ) { "${it.path} ${it.message}" },
)

fun Project.validate(): List<ProjectViolation> = buildList {
    if (!id.matches(PROJECT_ID_PATTERN)) {
        add(ProjectViolation("id", "must be a stable logical identifier"))
    }
    if (name.isBlank() || name.length > MAX_PROJECT_NAME_LENGTH) {
        add(ProjectViolation("name", "must contain 1..$MAX_PROJECT_NAME_LENGTH characters"))
    }
    if (name.any { it == '\u0000' }) {
        add(ProjectViolation("name", "must not contain NUL characters"))
    }
    if (createdAt < 0L) {
        add(ProjectViolation("createdAt", "must not be negative"))
    }
    if (updatedAt < createdAt) {
        add(ProjectViolation("updatedAt", "must not be earlier than createdAt"))
    }
    if (lastExportedAt != null && lastExportedAt < createdAt) {
        add(ProjectViolation("lastExportedAt", "must not be earlier than createdAt"))
    }
    spec.validate().forEach { violation ->
        add(ProjectViolation("spec.${violation.path}", violation.message))
    }
}

fun Project.requireValid(): Project {
    val violations = validate()
    if (violations.isNotEmpty()) {
        throw InvalidProjectException(violations)
    }
    return this
}

private val PROJECT_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
private const val MAX_PROJECT_NAME_LENGTH = 120
