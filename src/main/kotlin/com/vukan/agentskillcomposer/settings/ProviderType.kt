package com.vukan.agentskillcomposer.settings

enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
) {
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com",
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI-Compatible",
        defaultBaseUrl = "",
    );

    override fun toString(): String = displayName
}
