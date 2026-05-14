# MCP Extension Demo: Start New IntelliJ Project Tool

This plugin extends the [JetBrains MCP Server Plugin](https://github.com/JetBrains/mcp-server-plugin) by adding a custom MCP tool named `start_new_intellij_project`.

It serves as a demonstration of how to register custom tools in IntelliJ via the MCP Server Plugin, complete with an IDE settings page to enable or disable the tool dynamically without restarting the IDE.

## Features

- **`start_new_intellij_project` Tool**: Allows AI agents (acting as MCP clients) to initialize and open a new Java Gradle project from scratch.
  - Automatically generates boilerplate files: `build.gradle.kts`, `settings.gradle.kts`, `Main.java`, `MainTest.java`, `.gitignore`, and `README.md`.
  - Configures the IntelliJ project structure, properly marking `src/main/java` and `src/test/java` as source and test roots.
  - Opens the newly generated project directly in IntelliJ IDEA.

## Tool Arguments

The `start_new_intellij_project` tool accepts the following arguments:
- `projectPath` (String, Required): Absolute path where the new project should be created.
- `projectName` (String, Optional): The name of the project. Defaults to the final directory in the path.
- `packageName` (String, Optional): The base Java package name for the starter classes. Defaults to `app`.
- `openInNewWindow` (Boolean, Optional): Whether to open the new project in a new window. Defaults to `true`.
- `useDefaultProjectSettings` (Boolean, Optional): Whether to use the IDE's default project settings template. Defaults to `true`.
- `overwriteExistingFiles` (Boolean, Optional): Whether to overwrite files if they already exist at the path. Defaults to `false`.

## Development & Building

This project is built using Gradle and the IntelliJ Platform Gradle Plugin.

**Build the plugin:**
```bash
./gradlew buildPlugin
```

**Test the plugin in a sandbox IDE instance:**
```bash
./gradlew runIde
```

## Important Code Points
- `StartNewIntellijProjectTool.kt`: Defines the MCP tool itself and handles the logic for writing files and configuring the IDEA project via `ProjectManagerEx`.
- `McpToolSettings.kt`: Defines the persistent settings state and the UI configurable for the IDE settings page. It also handles registering and unregistering the tool dynamically using `McpToolRegistrar`.
- `plugin.xml`: Declares the dependency on `com.intellij.mcpServer`, registers the settings configurable, and configures the `postStartupActivity` that ensures tools are synchronized on startup.
