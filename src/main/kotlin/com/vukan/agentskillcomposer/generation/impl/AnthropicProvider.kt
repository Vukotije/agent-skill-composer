package com.vukan.agentskillcomposer.generation.impl

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpRequest

class AnthropicProvider(
    apiKey: String,
    baseUrl: String,
    modelName: String,
) : HttpAiProvider(apiKey, baseUrl, modelName) {

    override fun providerName(): String = "Anthropic"

    override fun buildGenerateRequest(systemPrompt: String, userPrompt: String): HttpRequest {
        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", userPrompt)
            })
        }
        val body = JsonObject().apply {
            addProperty("model", modelName)
            addProperty("max_tokens", MAX_TOKENS)
            addProperty("temperature", HttpAiProvider.TEMPERATURE)
            addProperty("system", systemPrompt)
            add("messages", messages)
        }
        return HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .timeout(requestTimeout())
            .build()
    }

    override fun extractContent(json: JsonObject): String {
        val content = json.getAsJsonArray("content")
            ?: throw IllegalStateException("Anthropic returned no content")
        check(content.size() > 0) { "Anthropic returned empty content array" }
        return content[0].asJsonObject
            .get("text")
            .asString
    }

    override fun buildListModelsRequest(): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/v1/models"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .GET()
            .timeout(listModelsTimeout())
            .build()

    override fun extractModels(json: JsonObject): List<String> {
        val data = json.getAsJsonArray("data") ?: return emptyList()
        return data.mapNotNull { el -> el.asJsonObject.get("id")?.asString }.sorted()
    }

    companion object {
        // Last verified: 2026-04-17, source: https://docs.anthropic.com/en/api/versioning
        private const val API_VERSION = "2023-06-01"
        private const val MAX_TOKENS = 8192
    }
}
