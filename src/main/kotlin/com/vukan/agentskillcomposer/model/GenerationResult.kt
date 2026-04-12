package com.vukan.agentskillcomposer.model

sealed class GenerationResult {
    data class Success(val artifact: GeneratedArtifact) : GenerationResult()
    data class Failure(val message: String, val cause: Throwable? = null) : GenerationResult()
}
