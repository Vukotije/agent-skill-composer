package com.vukan.agentskillcomposer.output.impl

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget
import com.vukan.agentskillcomposer.output.TargetPathResolver

class DefaultTargetPathResolver : TargetPathResolver {

    override fun resolveRelativePath(target: GenerationTarget, artifactType: ArtifactType): String {
        require(artifactType.applicableTo(target)) {
            "${artifactType.displayName} is not applicable to ${target.displayName}"
        }

        return when (target) {
            GenerationTarget.JUNIE -> juniePath(artifactType)
            GenerationTarget.CLAUDE -> claudePath(artifactType)
        }
    }

    private fun juniePath(artifactType: ArtifactType): String = when (artifactType) {
        is ArtifactType.ProjectGuidance -> ".junie/AGENTS.md"
        is ArtifactType.Skill -> ".junie/skills/${artifactType.id}/SKILL.md"
        is ArtifactType.ReviewChangesCommand -> error("ReviewChangesCommand is not applicable to Junie")
    }

    private fun claudePath(artifactType: ArtifactType): String = when (artifactType) {
        is ArtifactType.ProjectGuidance -> "CLAUDE.md"
        is ArtifactType.Skill -> ".claude/skills/${artifactType.id}/SKILL.md"
        is ArtifactType.ReviewChangesCommand -> ".claude/commands/review-changes.md"
    }
}
