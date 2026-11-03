// The skills ship the documentation of the Kiwi projects as reference files, because a plugin is installed directly
// from the repository without a build. The documentation is checked out from the source repositories configured in
// gradle.properties, and copied into the skills:
// - fetchDocs: shallow, sparse checkout of the documentation of each source into build/sources/
// - syncDocs: copies the documentation into the skills (commit the result)
// - checkDocs (part of `check`): fails if the copies in the skills differ from the sources

import java.io.ByteArrayOutputStream

plugins {
    base
}

/** Git repository providing documentation, configured by the properties `<name>.repository` and `<name>.ref` */
class DocSource(val name: String, val sparsePaths: List<String>) {
    val checkoutDir = layout.buildDirectory.dir("sources/$name")
}

val kiwiServer = DocSource(name = "kiwiServer", sparsePaths = listOf("docs"))
val kiwiVue = DocSource(name = "kiwiVue", sparsePaths = emptyList()) // Files in the repository root only

val skillsDir = layout.projectDirectory.dir("plugins/kiwi/skills")
val expectedDocsDir = layout.buildDirectory.dir("expected-docs")

/** Directories within the skills consisting of copied documentation only; other files in them are reported as stale */
val copiedDirs = listOf("kiwi-server/references", "kiwi-vue/references")

// Chapter links of the migration guide point to the documentation of the kiwi-server skill
val chapterLinkTarget = "](../../kiwi-server/references/"

fun CopySpec.skillDocs() {
    into("kiwi-server/references") {
        from(kiwiServer.checkoutDir.map { it.dir("docs") }) {
            include("*.md", "img/**")
            exclude("00-index.md") // Replaced by the table of the SKILL.md
        }
    }
    into("kiwi-upgrade/references") {
        from(kiwiServer.checkoutDir.map { it.file("docs/98-migration-1.5.md") })
        rename { "migration-1.5.md" }
        val linkTarget = chapterLinkTarget // Local copy: the configuration cache can't serialize the script object
        filter { line -> line.replace("](./", linkTarget) }
    }
    into("kiwi-vue/references") {
        from(kiwiVue.checkoutDir) {
            include("README.md", "CHANGELOG.md")
        }
    }
}

/** Services for task actions: tasks of build scripts can't inject them (scripts can't declare task classes) */
interface InjectedServices {
    @get:Inject
    val exec: ExecOperations

    @get:Inject
    val files: FileSystemOperations
}

private fun String.titlecase() = replaceFirstChar { it.uppercase() }

val docSources = listOf(kiwiServer, kiwiVue)
val fetchTasks = docSources.map { source ->
    val sourceName = source.name.titlecase()
    tasks.register("fetch${sourceName}Docs") {
        group = "documentation"
        description = "Checks out the documentation of ${source.name} (shallow, sparse checkout)"

        // Task actions may only capture local values, not the script, for the configuration cache
        val services = objects.newInstance<InjectedServices>()
        val projectDir = layout.projectDirectory
        val ref = providers.gradleProperty("${source.name}.ref")
        val repository = providers.gradleProperty("${source.name}.repository").map { value ->
            // Local paths are resolved relative to the project directory, as file URL for a shallow clone
            projectDir.dir(value).asFile
                .takeIf { it.isDirectory }
                ?.let { "file://${it.canonicalPath}" }
                ?: value
        }

        val sparsePaths = source.sparsePaths
        val checkoutDir = source.checkoutDir

        inputs.property("repository", repository)
        inputs.property("ref", ref)
        outputs.dir(checkoutDir)

        // The branch may have moved, so the checkout is never up-to-date
        outputs.upToDateWhen { false }

        doLast {
            val dir = checkoutDir.get().asFile
            services.files.delete { delete(dir) }
            services.exec.exec {
                commandLine(
                    "git",
                    "clone",
                    "--quiet",
                    "--depth=1",
                    "--filter=blob:none",
                    "--sparse",
                    "--branch", ref.get(),
                    repository.get(),
                    dir.absolutePath,
                )
            }
            services.exec.exec {
                commandLine(
                    "git",
                    "-C", dir.absolutePath,
                    "sparse-checkout",
                    "set",
                    *sparsePaths.toTypedArray(),
                )
            }

            val commit = ByteArrayOutputStream()
            services.exec.exec {
                standardOutput = commit
                commandLine(
                    "git",
                    "-C", dir.absolutePath,
                    "log", "-1",
                    "--format=%h %cs %s",
                )
            }

            logger.lifecycle("${repository.get()} ${ref.get()}: ${commit.toString().trim()}")
        }
    }
}

tasks.register("fetchDocs") {
    group = "documentation"
    description = "Checks out the documentation of all sources"
    dependsOn(fetchTasks)
}

val syncDocs = tasks.register<Sync>("syncDocs") {
    group = "documentation"
    description = "Copies the documentation of the sources into the skills"
    dependsOn(fetchTasks)

    into(skillsDir)
    skillDocs()

    // Only the copied documentation is replaced, everything else in the skills is kept
    preserve {
        include("**")
        copiedDirs.forEach { exclude("$it/**") }
    }

    // The skills directory contains other files, which Gradle ignores when checking whether the task is up-to-date
    outputs.upToDateWhen { false }
}

val prepareDocsCheck = tasks.register<Sync>("prepareDocsCheck") {
    group = "verification"
    description = "Collects the documentation of the sources for checkDocs"
    dependsOn(fetchTasks)

    into(expectedDocsDir)
    skillDocs()

    // Gradle doesn't track the filter lambda, so a changed link target must rerun the task
    inputs.property("chapterLinkTarget", chapterLinkTarget)
}

val checkDocs = tasks.register("checkDocs") {
    group = "verification"
    description = "Checks that the documentation in the skills is up to date with the sources"

    // Not a Sync: without a source of its own, a copy task would be skipped as NO-SOURCE
    dependsOn(prepareDocsCheck)
    val expectedDir = expectedDocsDir.get().asFile
    val skills = skillsDir.asFile
    val copied = copiedDirs

    doLast {
        val outdated = expectedDir.walk()
            .filter { it.isFile }
            .map { it.relativeTo(expectedDir).invariantSeparatorsPath }
            .filterNot { path ->
                skills.resolve(path).let { file ->
                    file.isFile && (file.readBytes() contentEquals expectedDir.resolve(path).readBytes())
                }
            }

        val stale = copied.asSequence()
            .flatMap { dir -> skills.resolve(dir).walk().filter { it.isFile } }
            .map { it.relativeTo(skills).invariantSeparatorsPath }
            .filterNot { path -> expectedDir.resolve(path).isFile }

        val differences = (outdated + stale).toList()
        check(differences.isEmpty()) {
            "Documentation in the skills differs from the sources, run ./gradlew syncDocs: $differences"
        }
    }
}

tasks.check {
    dependsOn(checkDocs)
}

tasks.build {
    dependsOn(syncDocs)
}
