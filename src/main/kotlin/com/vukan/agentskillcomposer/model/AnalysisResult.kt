package com.vukan.agentskillcomposer.model

sealed class AnalysisResult {
    data class Success(val facts: ProjectFacts) : AnalysisResult()
    data class Failure(val message: String, val cause: Throwable? = null) : AnalysisResult()
}
