package com.vukan.agentskillcomposer.generation

import com.vukan.agentskillcomposer.model.GenerationRequest
import com.vukan.agentskillcomposer.model.GenerationResult

interface ArtifactGenerator {
    suspend fun generate(request: GenerationRequest): GenerationResult
}
