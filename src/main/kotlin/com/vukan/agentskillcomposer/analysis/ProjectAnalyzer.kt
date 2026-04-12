package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.progress.ProgressIndicator
import com.vukan.agentskillcomposer.model.AnalysisResult

interface ProjectAnalyzer {
    fun analyze(indicator: ProgressIndicator? = null): AnalysisResult
}
