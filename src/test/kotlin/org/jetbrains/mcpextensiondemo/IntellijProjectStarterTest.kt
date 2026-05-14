package org.jetbrains.mcpextensiondemo

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntellijProjectStarterTest {
    @Test
    fun `creates Gradle Java starter project`() {
        val projectPath = Files.createTempDirectory("starter-project")

        val createdFiles = IntellijProjectStarter.createStarterFiles(
            projectPath = projectPath,
            projectName = "SampleApp",
            args = StartNewIntellijProjectArgs(
                projectPath = projectPath.toString(),
                projectName = "SampleApp",
                packageName = "com.example.sample"
            )
        )

        assertEquals(
            setOf(
                "settings.gradle.kts",
                "build.gradle.kts",
                "src/main/java/com/example/sample/Main.java",
                "src/test/java/com/example/sample/MainTest.java",
                ".gitignore",
                "README.md"
            ),
            createdFiles.map { projectPath.relativize(it).toString() }.toSet()
        )
        assertTrue(projectPath.resolve("settings.gradle.kts").readText().contains("rootProject.name = \"SampleApp\""))
        assertTrue(projectPath.resolve("build.gradle.kts").readText().contains("mainClass = \"com.example.sample.Main\""))
        assertTrue(projectPath.resolve("src/main/java/com/example/sample/Main.java").readText().contains("package com.example.sample;"))
        assertTrue(projectPath.resolve("src/test/java/com/example/sample/MainTest.java").readText().contains("assertEquals(\"Hello from SampleApp\""))
    }

    @Test
    fun `does not overwrite existing files by default`() {
        val projectPath = Files.createTempDirectory("starter-project")
        val buildFile = projectPath.resolve("build.gradle.kts")
        buildFile.writeText("custom build\n")

        val createdFiles = IntellijProjectStarter.createStarterFiles(
            projectPath = projectPath,
            projectName = "NoOverwrite",
            args = StartNewIntellijProjectArgs(projectPath = projectPath.toString())
        )

        assertEquals("custom build\n", buildFile.readText())
        assertFalse(createdFiles.contains(buildFile))
    }

    @Test
    fun `overwrites existing files when requested`() {
        val projectPath = Files.createTempDirectory("starter-project")
        val buildFile = projectPath.resolve("build.gradle.kts")
        buildFile.writeText("custom build\n")

        val createdFiles = IntellijProjectStarter.createStarterFiles(
            projectPath = projectPath,
            projectName = "Overwrite",
            args = StartNewIntellijProjectArgs(
                projectPath = projectPath.toString(),
                overwriteExistingFiles = true
            )
        )

        assertTrue(createdFiles.contains(buildFile))
        assertTrue(buildFile.readText().contains("mainClass = \"app.Main\""))
    }

    @Test
    fun `sanitizes invalid package names`() {
        assertEquals("com.example_1.app", IntellijProjectStarter.sanitizePackageName("9com.exa-mple_1...app!"))
        assertEquals("app", IntellijProjectStarter.sanitizePackageName("123.---"))
    }

    @Test
    fun `escapes project name in generated string literals`() {
        val projectPath = Files.createTempDirectory("starter-project")

        IntellijProjectStarter.createStarterFiles(
            projectPath = projectPath,
            projectName = "Quoted \"App\" \\ Name",
            args = StartNewIntellijProjectArgs(projectPath = projectPath.toString())
        )

        val settings = projectPath.resolve("settings.gradle.kts").readText()
        val main = projectPath.resolve(Path.of("src/main/java/app/Main.java")).readText()
        assertTrue(settings.contains("rootProject.name = \"Quoted \\\"App\\\" \\\\ Name\""))
        assertTrue(main.contains("return \"Hello from Quoted \\\"App\\\" \\\\ Name\";"))
    }
}
