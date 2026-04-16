package com.vukan.agentskillcomposer.generation.impl

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpRequest

class GeminiProvider(
    apiKey: String,
    baseUrl: String,
    modelName: String,
) : HttpAiProvider(apiKey, baseUrl, modelName) {

    override fun providerName(): String = "Gemini"

    override fun buildGenerateRequest(systemPrompt: String, userPrompt: String): HttpRequest {
        val body = JsonObject().apply {
            add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", systemPrompt) })
                })
            })
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", userPrompt) })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", HttpAiProvider.TEMPERATURE)
            })
        }
        return HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/v1beta/models/$modelName:generateContent"))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .timeout(requestTimeout())
            .build()
    }

    override fun extractContent(json: JsonObject): String {
        val candidates = json.getAsJsonArray("candidates")
            ?: throw IllegalStateException("Gemini returned no candidates")
        check(candidates.size() > 0) { "Gemini returned empty candidates array" }
        val firstCandidate = candidates[0]?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalStateException("Gemini returned malformed candidate")
        val content = firstCandidate.getAsJsonObject("content")
            ?: throw IllegalStateException("Gemini returned no content on candidate")
        val parts = content.getAsJsonArray("parts")
            ?: throw IllegalStateException("Gemini returned no parts on content")
        check(parts.size() > 0) { "Gemini returned empty parts array" }
        val firstPart = parts[0]?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalStateException("Gemini returned malformed part")
        return firstPart.get("text")?.takeIf { !it.isJsonNull }?.asString
            ?: throw IllegalStateException("Gemini returned no text on part")
    }

    override fun buildListModelsRequest(): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/v1beta/models"))
            .header("x-goog-api-key", apiKey)
            .GET()
            .timeout(listModelsTimeout())
            .build()

    override fun extractModels(json: JsonObject): List<String> {
        val models = json.getAsJsonArray("models") ?: return emptyList()
        return models.mapNotNull { el ->
            val obj = el.asJsonObject
            val methods = obj.getAsJsonArray("supportedGenerationMethods")
            val supportsGenerate = methods?.any { it.asString == "generateContent" } == true
            if (!supportsGenerate) null
            else obj.get("name")?.asString?.removePrefix("models/")
        }.sorted()
    }
}
