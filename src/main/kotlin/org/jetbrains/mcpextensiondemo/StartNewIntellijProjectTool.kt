package org.jetbrains.mcpextensiondemo

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.Serializable

class StartNewIntellijProjectTool : AbstractMcpTool<StartNewIntellijProjectArgs>(StartNewIntellijProjectArgs.serializer()) {
    override val name: String = "start_new_intellij_project"
    override val description: String = "Creates a starter Gradle Java project, opens it in IntelliJ, and configures source roots"

    override fun handle(project: Project, args: StartNewIntellijProjectArgs): Response {
        val projectPath = Paths.get(args.projectPath).toAbsolutePath().normalize()
        val projectName = args.projectName?.takeIf { it.isNotBlank() } ?: projectPath.fileName?.toString()
        if (projectName.isNullOrBlank()) {
            return Response("Cannot create IntelliJ project: projectName is required when the path has no file name")
        }

        return try {
            if (Files.exists(projectPath) && !Files.isDirectory(projectPath)) {
                return Response("Cannot create IntelliJ project: path is not a directory: $projectPath")
            }

            Files.createDirectories(projectPath)
            val createdFiles = IntellijProjectStarter.createStarterFiles(projectPath, projectName, args)

            val newProject = ProjectManagerEx.getInstanceEx().newProject(
                projectPath,
                OpenProjectTask {
                    forceOpenInNewFrame = args.openInNewWindow
                    isNewProject = true
                    useDefaultProjectAsTemplate = args.useDefaultProjectSettings
                    this.projectName = projectName
                }
            )

            if (newProject == null) {
                Response("IntelliJ did not create a project at $projectPath")
            } else {
                configureProjectModel(newProject, projectPath, projectName)
                val fileSummary = createdFiles.joinToString(", ") { projectPath.relativize(it).toString() }
                    .ifBlank { "no starter files were changed" }
                Response(
                    "Started IntelliJ project '${newProject.name}' at ${newProject.basePath ?: projectPath}. " +
                            "Prepared starter files: $fileSummary"
                )
            }
        } catch (e: Exception) {
            Response("Failed to start IntelliJ project at $projectPath: ${e.message}")
        }
    }

    private fun configureProjectModel(project: Project, projectPath: Path, projectName: String) {
        val localFileSystem = LocalFileSystem.getInstance()
        VfsUtil.markDirtyAndRefresh(false, true, true, projectPath.toFile())
        val contentRoot = localFileSystem.refreshAndFindFileByNioFile(projectPath) ?: return
        val mainSourceRoot = localFileSystem.refreshAndFindFileByNioFile(projectPath.resolve("src/main/java"))
        val testSourceRoot = localFileSystem.refreshAndFindFileByNioFile(projectPath.resolve("src/test/java"))

        ApplicationManager.getApplication().runWriteAction {
            val moduleModel = ModuleManager.getInstance(project).getModifiableModel()
            val module = moduleModel.findModuleByName(projectName)
                ?: moduleModel.newModule(projectPath.resolve("$projectName.iml").toString(), ModuleType.EMPTY.id)
            moduleModel.commit()

            val rootModel = ModuleRootManager.getInstance(module).getModifiableModel()
            val existingContentEntry = rootModel.contentEntries.firstOrNull { it.file == contentRoot }
            val contentEntry = existingContentEntry ?: rootModel.addContentEntry(contentRoot)
            mainSourceRoot?.let { sourceRoot ->
                if (contentEntry.sourceFolders.none { it.file == sourceRoot }) {
                    contentEntry.addSourceFolder(sourceRoot, false)
                }
            }
            testSourceRoot?.let { sourceRoot ->
                if (contentEntry.sourceFolders.none { it.file == sourceRoot }) {
                    contentEntry.addSourceFolder(sourceRoot, true)
                }
            }
            rootModel.commit()
        }
    }
}

internal object IntellijProjectStarter {
    fun createStarterFiles(
        projectPath: Path,
        projectName: String,
        args: StartNewIntellijProjectArgs
    ): List<Path> {
        val packageName = sanitizePackageName(args.packageName ?: "app")
        val packagePath = packageName.replace('.', '/')
        val escapedProjectName = escapeStringLiteral(projectName)
        val files = linkedMapOf(
            projectPath.resolve("settings.gradle.kts") to """
                pluginManagement {
                    repositories {
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        mavenCentral()
                    }
                }

                rootProject.name = "$escapedProjectName"
            """.trimIndent(),
            projectPath.resolve("build.gradle.kts") to """
                plugins {
                    application
                }

                group = "$packageName"
                version = "1.0-SNAPSHOT"

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
                }

                application {
                    mainClass = "$packageName.Main"
                }

                tasks.test {
                    useJUnitPlatform()
                }
            """.trimIndent(),
            projectPath.resolve("src/main/java/$packagePath/Main.java") to """
                package $packageName;

                public class Main {
                    public static void main(String[] args) {
                        System.out.println(greeting());
                    }

                    public static String greeting() {
                        return "Hello from $escapedProjectName";
                    }
                }
            """.trimIndent(),
            projectPath.resolve("src/test/java/$packagePath/MainTest.java") to """
                package $packageName;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class MainTest {
                    @Test
                    void greetingIncludesProjectName() {
                        assertEquals("Hello from $escapedProjectName", Main.greeting());
                    }
                }
            """.trimIndent(),
            projectPath.resolve(".gitignore") to """
                .gradle/
                build/
                out/
                .idea/
                *.iml
            """.trimIndent(),
            projectPath.resolve("README.md") to """
                # $projectName

                Starter Gradle project created by IntelliJ MCP.

                ## Run

                ```bash
                gradle run
                ```
            """.trimIndent()
        )

        return files.mapNotNull { (path, content) ->
            if (Files.exists(path) && !args.overwriteExistingFiles) {
                null
            } else {
                Files.createDirectories(path.parent)
                Files.writeString(path, "$content\n")
                path
            }
        }
    }

    fun sanitizePackageName(packageName: String): String {
        val sanitized = packageName
            .split('.')
            .mapNotNull { segment ->
                val cleaned = buildString {
                    segment.forEach { char ->
                        if (char == '_' || char.isLetter() || isNotEmpty() && char.isDigit()) {
                            append(char)
                        }
                    }
                }
                cleaned.takeIf { it.isNotBlank() }
            }
            .joinToString(".")
        return sanitized.ifBlank { "app" }
    }

    fun escapeStringLiteral(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}

@Serializable
data class StartNewIntellijProjectArgs(
    val projectPath: String,
    val projectName: String? = null,
    val openInNewWindow: Boolean = true,
    val useDefaultProjectSettings: Boolean = true,
    val packageName: String? = null,
    val overwriteExistingFiles: Boolean = false
)
