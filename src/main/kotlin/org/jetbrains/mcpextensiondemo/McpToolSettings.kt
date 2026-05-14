package org.jetbrains.mcpextensiondemo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class McpToolSettingsState : BaseState() {
    var startNewIntellijProjectEnabled by property(true)
}

@State(
    name = "McpExtensionDemoSettings",
    storages = [Storage("mcpExtensionDemo.xml")]
)
class McpToolSettings : SimplePersistentStateComponent<McpToolSettingsState>(McpToolSettingsState()) {
    companion object {
        fun getInstance(): McpToolSettings =
            ApplicationManager.getApplication().getService(McpToolSettings::class.java)
    }
}

object McpToolRegistrar {
    private val mcpToolExtensionPoint =
        ExtensionPointName.create<AbstractMcpTool<*>>("com.intellij.mcpServer.mcpTool")
    private var startNewIntellijProjectRegistration: Disposable? = null

    fun syncRegisteredTools() {
        val isEnabled = McpToolSettings.getInstance().state.startNewIntellijProjectEnabled
        val isRegistered = startNewIntellijProjectRegistration != null

        if (isEnabled && !isRegistered) {
            val registration = Disposer.newDisposable("start_new_intellij_project MCP tool")
            mcpToolExtensionPoint.point.registerExtension(StartNewIntellijProjectTool(), registration)
            startNewIntellijProjectRegistration = registration
        } else if (!isEnabled && isRegistered) {
            Disposer.dispose(startNewIntellijProjectRegistration!!)
            startNewIntellijProjectRegistration = null
        }
    }
}

class McpToolStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        McpToolRegistrar.syncRegisteredTools()
    }
}

class McpToolSettingsConfigurable : SearchableConfigurable {
    private var startNewProjectToolCheckBox: JCheckBox? = null
    private var panel: JPanel? = null

    override fun getId(): String = "org.jetbrains.mcpextensiondemo.mcpTools"

    override fun getDisplayName(): String = "MCP Extension Demo"

    override fun createComponent(): JComponent {
        val checkBox = JCheckBox(
            "start_new_intellij_project",
            McpToolSettings.getInstance().state.startNewIntellijProjectEnabled
        )
        startNewProjectToolCheckBox = checkBox

        val content = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(12, 12, 12, 12)
            add(JLabel("Available MCP tools"), BorderLayout.NORTH)
            add(checkBox, BorderLayout.CENTER)
        }
        panel = content
        return content
    }

    override fun isModified(): Boolean {
        val checkBox = startNewProjectToolCheckBox ?: return false
        return checkBox.isSelected != McpToolSettings.getInstance().state.startNewIntellijProjectEnabled
    }

    override fun apply() {
        startNewProjectToolCheckBox?.let { checkBox ->
            McpToolSettings.getInstance().state.startNewIntellijProjectEnabled = checkBox.isSelected
            McpToolRegistrar.syncRegisteredTools()
        }
    }

    override fun reset() {
        startNewProjectToolCheckBox?.isSelected =
            McpToolSettings.getInstance().state.startNewIntellijProjectEnabled
    }

    override fun disposeUIResources() {
        startNewProjectToolCheckBox = null
        panel = null
    }
}
