package com.vukan.agentskillcomposer.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "AgentSkillComposerSettings",
    storages = [Storage("AgentSkillComposer.xml")],
)
class PluginSettings : PersistentStateComponent<PluginSettings.State> {

    private var state = State()

    class State {
        var providerType: String = ProviderType.ANTHROPIC.name
        var baseUrl: String = ProviderType.ANTHROPIC.defaultBaseUrl
        var modelName: String = ""
    }

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    val providerType: ProviderType
        get() = try {
            ProviderType.valueOf(state.providerType)
        } catch (_: IllegalArgumentException) {
            ProviderType.ANTHROPIC
        }

    var apiKey: String?
        get() = PasswordSafe.instance.getPassword(CREDENTIAL_ATTRIBUTES)
        set(value) {
            PasswordSafe.instance.setPassword(CREDENTIAL_ATTRIBUTES, value)
        }

    companion object {
        fun getInstance(): PluginSettings = service()

        private val CREDENTIAL_ATTRIBUTES = CredentialAttributes(
            generateServiceName("AgentSkillComposer", "apiKey"),
        )
    }
}
