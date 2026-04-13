package com.vukan.agentskillcomposer.generation

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationRequest
import com.vukan.agentskillcomposer.model.GenerationTarget

interface PromptFactory {
    fun buildSystemPrompt(target: GenerationTarget, artifactType: ArtifactType): String
    fun buildUserPrompt(request: GenerationRequest): String
}
