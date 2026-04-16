package com.vukan.agentskillcomposer.generation

import com.vukan.agentskillcomposer.generation.impl.AnthropicProvider
import com.vukan.agentskillcomposer.generation.impl.GeminiProvider
import com.vukan.agentskillcomposer.generation.impl.OpenAiCompatibleProvider
import com.vukan.agentskillcomposer.settings.PluginSettings
import com.vukan.agentskillcomposer.settings.ProviderType

object AiProviderFactory {

    fun create(settings: PluginSettings): AiProvider {
        val apiKey = settings.apiKey
        check(!apiKey.isNullOrBlank()) { "API key is not configured" }
        return create(
            providerType = settings.providerType,
            apiKey = apiKey,
            baseUrl = settings.state.baseUrl,
            modelName = settings.state.modelName,
        )
    }

    fun create(
        providerType: ProviderType,
        apiKey: String,
        baseUrl: String,
        modelName: String,
    ): AiProvider = when (providerType) {
        ProviderType.ANTHROPIC -> AnthropicProvider(apiKey, baseUrl, modelName)
        ProviderType.GEMINI -> GeminiProvider(apiKey, baseUrl, modelName)
        ProviderType.OPENAI, ProviderType.OPENAI_COMPATIBLE ->
            OpenAiCompatibleProvider(apiKey, baseUrl, modelName, providerType.displayName)
    }
}
