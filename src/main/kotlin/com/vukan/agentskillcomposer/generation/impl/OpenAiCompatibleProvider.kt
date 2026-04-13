package com.vukan.agentskillcomposer.generation.impl

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpRequest

class OpenAiCompatibleProvider(
    apiKey: String,
    baseUrl: String,
    modelName: String,
) : HttpAiProvider(apiKey, baseUrl, modelName) {

    override fun providerName(): String = "OpenAI"

    override fun buildGenerateRequest(systemPrompt: String, userPrompt: String): HttpRequest {
        val messages = JsonArray().apply {
            add(message("system", systemPrompt))
            add(message("user", userPrompt))
        }
        val body = JsonObject().apply {
            addProperty("model", modelName)
            addProperty("temperature", HttpAiProvider.TEMPERATURE)
            add("messages", messages)
        }
        return HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .timeout(requestTimeout())
            .build()
    }

    override fun extractContent(json: JsonObject): String {
        val choices = json.getAsJsonArray("choices")
            ?: throw IllegalStateException("OpenAI returned no choices")
        check(choices.size() > 0) { "OpenAI returned empty choices array" }
        return choices[0].asJsonObject
            .getAsJsonObject("message")
            .get("content")
            .asString
    }

    override fun buildListModelsRequest(): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/models"))
            .header("Authorization", "Bearer $apiKey")
            .GET()
            .timeout(listModelsTimeout())
            .build()

    override fun extractModels(json: JsonObject): List<String> {
        val data = json.getAsJsonArray("data") ?: return emptyList()
        return data.mapNotNull { el -> el.asJsonObject.get("id")?.asString }.sorted()
    }

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }
}
