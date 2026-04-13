package com.vukan.agentskillcomposer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.vukan.agentskillcomposer.MyMessageBundle
import com.vukan.agentskillcomposer.generation.AiProviderFactory
import com.vukan.agentskillcomposer.generation.impl.HttpAiProvider
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PluginSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private var suspendProviderListener = false

    private val providerCombo = ComboBox(ProviderType.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { it.displayName }
    }
    private val apiKeyField = JBPasswordField()
    private val baseUrlField = JBTextField()
    private val modelCombo = ComboBox<String>().apply { isEditable = true }
    private val refreshModelsButton = JButton(MyMessageBundle.message("action.refreshModels"))
    private val spinnerIcon = AnimatedIcon.Default()
    private val modelStatusLabel = JBLabel(" ")

    @Volatile
    private var fetching = false

    init {
        providerCombo.addActionListener { onProviderChanged() }
        refreshModelsButton.addActionListener { fetchModels() }
    }

    override fun getDisplayName(): String =
        MyMessageBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        val root = panel {
            group(MyMessageBundle.message("settings.group.provider")) {
                row(MyMessageBundle.message("settings.label.provider")) {
                    cell(providerCombo).align(AlignX.FILL)
                }
                row(MyMessageBundle.message("settings.label.apiKey")) {
                    cell(apiKeyField)
                        .align(AlignX.FILL)
                        .comment(MyMessageBundle.message("settings.comment.apiKey"))
                }
                row(MyMessageBundle.message("settings.label.baseUrl")) {
                    cell(baseUrlField)
                        .align(AlignX.FILL)
                        .comment(MyMessageBundle.message("settings.comment.baseUrl"))
                }
                row(MyMessageBundle.message("settings.label.modelName")) {
                    cell(modelCombo).align(AlignX.FILL)
                    cell(refreshModelsButton)
                }
                row("") {
                    cell(modelStatusLabel)
                }
            }
        }
        panel = root
        reset()
        return root
    }

    override fun isModified(): Boolean {
        val settings = PluginSettings.getInstance()
        val selectedProvider = providerCombo.selectedItem as? ProviderType ?: ProviderType.ANTHROPIC
        return currentApiKey() != (settings.apiKey ?: "") ||
            selectedProvider.name != settings.state.providerType ||
            baseUrlField.text != settings.state.baseUrl ||
            currentModel() != settings.state.modelName
    }

    override fun apply() {
        val settings = PluginSettings.getInstance()
        val selectedProvider = providerCombo.selectedItem as? ProviderType ?: ProviderType.ANTHROPIC
        settings.apiKey = currentApiKey().ifBlank { null }
        settings.state.providerType = selectedProvider.name
        settings.state.baseUrl = baseUrlField.text.ifBlank { selectedProvider.defaultBaseUrl }
        settings.state.modelName = currentModel()
    }

    override fun reset() {
        val settings = PluginSettings.getInstance()
        withoutProviderListener {
            providerCombo.selectedItem = settings.providerType
            apiKeyField.text = settings.apiKey ?: ""
            baseUrlField.text = settings.state.baseUrl
            setModelList(emptyList(), settings.state.modelName)
        }
        if (!settings.apiKey.isNullOrBlank()) fetchModels()
    }

    override fun disposeUIResources() {
        panel = null
    }

    private inline fun withoutProviderListener(block: () -> Unit) {
        suspendProviderListener = true
        try {
            block()
        } finally {
            suspendProviderListener = false
        }
    }

    private fun onProviderChanged() {
        if (suspendProviderListener) return
        val provider = providerCombo.selectedItem as? ProviderType ?: return
        baseUrlField.text = provider.defaultBaseUrl
        setModelList(emptyList(), "")
        modelStatusLabel.icon = null
        modelStatusLabel.text = " "
        if (currentApiKey().isNotBlank()) fetchModels()
    }

    private fun fetchModels() {
        if (fetching) return
        val provider = providerCombo.selectedItem as? ProviderType ?: return
        val apiKey = currentApiKey()
        if (apiKey.isBlank()) {
            modelStatusLabel.icon = null
            modelStatusLabel.text = MyMessageBundle.message("settings.status.apiKeyRequired")
            return
        }
        val baseUrl = baseUrlField.text.ifBlank { provider.defaultBaseUrl }
        if (baseUrl.isBlank()) {
            modelStatusLabel.icon = null
            modelStatusLabel.text = MyMessageBundle.message("settings.status.baseUrlRequired")
            return
        }

        fetching = true
        refreshModelsButton.isEnabled = false
        modelStatusLabel.icon = spinnerIcon
        modelStatusLabel.text = MyMessageBundle.message("settings.status.loading")

        val priorSelection = currentModel()

        // No coroutines — Dispatchers.EDT doesn't reliably resume in an application-level
        // Configurable. Plain pool thread + invokeLater is guaranteed to work.
        ApplicationManager.getApplication().executeOnPooledThread {
            val result: Result<List<String>> = try {
                val aiProvider = AiProviderFactory.create(provider, apiKey, baseUrl, "")
                val models = (aiProvider as HttpAiProvider).listModelsBlocking()
                Result.success(models)
            } catch (e: Exception) {
                Result.failure(e)
            }

            SwingUtilities.invokeLater {
                if (panel == null) return@invokeLater
                fetching = false
                refreshModelsButton.isEnabled = true
                modelStatusLabel.icon = null

                result.fold(
                    onSuccess = { models ->
                        val chosen = priorSelection.ifBlank { models.firstOrNull() ?: "" }
                        setModelList(models, chosen)
                        modelStatusLabel.text =
                            MyMessageBundle.message("settings.status.loaded", models.size)
                    },
                    onFailure = { e ->
                        setModelList(emptyList(), priorSelection)
                        modelStatusLabel.text = MyMessageBundle.message(
                            "settings.status.loadFailed",
                            e.message ?: e.javaClass.simpleName,
                        )
                    },
                )
            }
        }
    }

    private fun setModelList(models: List<String>, selection: String) {
        val items = when {
            selection.isNotBlank() && selection !in models -> models + selection
            else -> models
        }
        modelCombo.model = DefaultComboBoxModel(items.toTypedArray())
        if (selection.isNotBlank()) modelCombo.selectedItem = selection
        else if (items.isNotEmpty()) modelCombo.selectedIndex = 0
        else modelCombo.selectedItem = ""
    }

    private fun currentApiKey(): String = String(apiKeyField.password)

    private fun currentModel(): String =
        (modelCombo.editor.item as? String ?: modelCombo.selectedItem as? String).orEmpty()
}
