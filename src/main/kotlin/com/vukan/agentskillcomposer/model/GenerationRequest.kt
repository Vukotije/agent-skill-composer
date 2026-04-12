package com.vukan.agentskillcomposer.model

data class GenerationRequest(
    val target: GenerationTarget,
    val artifactType: ArtifactType,
    val projectFacts: ProjectFacts,
    val userInstructions: String? = null,
) {
    init {
        require(artifactType.applicableTo(target)) {
            "${artifactType.displayName} is not applicable to ${target.displayName}"
        }
    }
}
