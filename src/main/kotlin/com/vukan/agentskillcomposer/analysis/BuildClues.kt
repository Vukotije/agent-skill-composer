package com.vukan.agentskillcomposer.analysis

import com.vukan.agentskillcomposer.model.BuildSystem
import com.vukan.agentskillcomposer.model.DetectedFramework
import com.vukan.agentskillcomposer.model.TestFramework

data class BuildClues(
    val buildSystem: BuildSystem,
    val rawDependencies: List<String>,
    val detectedFrameworks: List<DetectedFramework>,
    val detectedTestFrameworks: List<TestFramework>,
)
