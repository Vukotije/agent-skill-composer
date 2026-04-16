package com.vukan.agentskillcomposer.generation.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import com.vukan.agentskillcomposer.generation.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import kotlin.random.Random

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
            val json = sendRequest(buildListModelsRequest())
            extractModels(json)
        }

    private suspend fun sendRequest(request: HttpRequest): JsonObject {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return executeOnce(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isRetryable(e) || attempt >= MAX_ATTEMPTS) throw e
                val waitMs = backoffMs(attempt)
                thisLogger().warn(
                    "${providerName()} transient failure (attempt $attempt/$MAX_ATTEMPTS): ${e.message}; retrying in ${waitMs}ms",
                )
                delay(waitMs)
            }
        }
    }

    private fun executeOnce(request: HttpRequest): JsonObject {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body()
        val status = response.statusCode()
        if (status == 200) {
            return JsonParser.parseString(body).asJsonObject
        }
        val errorMessage = tryExtractErrorMessage(body) ?: "HTTP $status"
        throw HttpStatusException(status, "${providerName()} error: ${sanitize(errorMessage)}")
    }

    private fun isRetryable(e: Exception): Boolean = when (e) {
        is HttpStatusException -> e.status == 429 || e.status in 500..599
        // Timeout means we already waited the full requestTimeout once — the server is slow
        // or unresponsive, not flaky. Retrying would multiply the wait (3 × 120s for generate).
        is HttpTimeoutException -> false
        // Fast network failures (connection refused, reset, DNS blip) — retry helps.
        is IOException -> true
        else -> false
    }

    private fun backoffMs(attempt: Int): Long {
        // 500, 1000, 2000, ... capped at 8000. Adds up to 250ms jitter to avoid thundering herd.
        val base = (500L shl (attempt - 1)).coerceAtMost(8_000L)
        return base + Random.nextLong(0, 250)
    }

    private class HttpStatusException(val status: Int, message: String) : IllegalStateException(message)

    protected abstract fun buildGenerateRequest(systemPrompt: String, userPrompt: String): HttpRequest

    protected abstract fun extractContent(json: JsonObject): String

    protected abstract fun buildListModelsRequest(): HttpRequest

    protected abstract fun extractModels(json: JsonObject): List<String>

    protected open fun providerName(): String = "AI provider"

    protected fun requestTimeout(): Duration = REQUEST_TIMEOUT

    protected fun listModelsTimeout(): Duration = LIST_MODELS_TIMEOUT

    private fun sanitize(message: String): String =
        message.replace(apiKey, "***")

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(120)
        private val LIST_MODELS_TIMEOUT: Duration = Duration.ofSeconds(15)

        private const val MAX_ATTEMPTS = 3

        // Intentional default: low temperature for deterministic, grounded output.
        // Not exposed as a setting — generation quality depends on consistency.
        const val TEMPERATURE = 0.3

        // Narrow: parseString throws JsonParseException, asJsonObject throws IllegalStateException
        // when the body isn't a JSON object. Anything else (OOM, etc.) should propagate.
        fun tryExtractErrorMessage(body: String): String? = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        } catch (_: JsonParseException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }
}
