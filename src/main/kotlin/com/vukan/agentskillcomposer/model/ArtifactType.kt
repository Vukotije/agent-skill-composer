package com.vukan.agentskillcomposer.model

sealed class ArtifactType {
    abstract val id: String
    abstract val displayName: String
    abstract val description: String

    fun applicableTo(target: GenerationTarget): Boolean = when (this) {
        is ReviewChangesCommand -> target == GenerationTarget.CLAUDE
        is ProjectGuidance, is Skill -> true
    }

    data object ProjectGuidance : ArtifactType() {
        override val id = "project-guidance"
        override val displayName = "Project Guidance"
        override val description = "High-level project instructions and conventions for the agent"
    }

    data class Skill(val suggestion: SkillSuggestion) : ArtifactType() {
        override val id get() = suggestion.id
        override val displayName get() = suggestion.displayName
        override val description get() = suggestion.description
    }

    data object ReviewChangesCommand : ArtifactType() {
        override val id = "review-changes"
        override val displayName = "Review Changes Command"
        override val description = "Command for reviewing code changes against project conventions"
    }

    override fun toString(): String = displayName

    companion object {
        fun forTarget(
            target: GenerationTarget,
            suggestedSkills: List<SkillSuggestion> = emptyList(),
        ): List<ArtifactType> = buildList {
            add(ProjectGuidance)
            suggestedSkills.forEach { add(Skill(it)) }
            if (target == GenerationTarget.CLAUDE) add(ReviewChangesCommand)
        }
    }
}
