package com.vukan.agentskillcomposer.generation.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vukan.agentskillcomposer.generation.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

abstract class HttpAiProvider(
    protected val apiKey: String,
    protected val baseUrl: String,
    protected val modelName: String,
) : AiProvider {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build()

    final override suspend fun generate(systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            val json = sendRequest(buildGenerateRequest(systemPrompt, userPrompt))
            extractContent(json)
        }

    final override suspend fun listModels(): List<String> =
        withContext(Dispatchers.IO) {
            listModelsBlocking()
        }

    fun listModelsBlocking(): List<String> {
        val json = sendRequest(buildListModelsRequest())
        return extractModels(json)
    }

    protected abstract fun buildGenerateRequest(systemPrompt: String, userPrompt: String): HttpRequest

    protected abstract fun extractContent(json: JsonObject): String

    protected abstract fun buildListModelsRequest(): HttpRequest

    protected abstract fun extractModels(json: JsonObject): List<String>

    protected open fun providerName(): String = "AI provider"

    protected fun requestTimeout(): Duration = REQUEST_TIMEOUT

    protected fun listModelsTimeout(): Duration = LIST_MODELS_TIMEOUT

    private fun sendRequest(request: HttpRequest): JsonObject {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body()
        if (response.statusCode() != 200) {
            val errorMessage = tryExtractErrorMessage(body) ?: "HTTP ${response.statusCode()}"
            throw IllegalStateException("${providerName()} error: ${sanitize(errorMessage)}")
        }
        return JsonParser.parseString(body).asJsonObject
    }

    private fun sanitize(message: String): String =
        message.replace(apiKey, "***")

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(120)
        private val LIST_MODELS_TIMEOUT: Duration = Duration.ofSeconds(15)

        // Intentional default: low temperature for deterministic, grounded output.
        // Not exposed as a setting — generation quality depends on consistency.
        const val TEMPERATURE = 0.3

        fun tryExtractErrorMessage(body: String): String? = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        } catch (_: Exception) {
            null
        }
    }
}
