package com.vukan.agentskillcomposer.generation

interface AiProvider {
    suspend fun generate(systemPrompt: String, userPrompt: String): String

    suspend fun listModels(): List<String>
}
