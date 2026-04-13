package com.vukan.agentskillcomposer.generation.impl

import com.vukan.agentskillcomposer.generation.AiProvider
import com.vukan.agentskillcomposer.generation.ArtifactGenerator
import com.vukan.agentskillcomposer.generation.PromptFactory
import com.vukan.agentskillcomposer.model.GeneratedArtifact
import com.vukan.agentskillcomposer.model.GenerationRequest
import com.vukan.agentskillcomposer.model.GenerationResult
import com.vukan.agentskillcomposer.output.TargetPathResolver
import kotlinx.coroutines.CancellationException

class DefaultArtifactGenerator(
    private val aiProvider: AiProvider,
    private val promptFactory: PromptFactory,
    private val pathResolver: TargetPathResolver,
) : ArtifactGenerator {

    override suspend fun generate(request: GenerationRequest): GenerationResult =
        try {
            val systemPrompt = promptFactory.buildSystemPrompt(request.target, request.artifactType)
            val userPrompt = promptFactory.buildUserPrompt(request)
            val content = aiProvider.generate(systemPrompt, userPrompt)

            val artifact = GeneratedArtifact(
                target = request.target,
                artifactType = request.artifactType,
                title = "${request.artifactType.displayName} for ${request.target.displayName}",
                content = content,
                defaultPath = pathResolver.resolveRelativePath(request.target, request.artifactType),
            )
            GenerationResult.Success(artifact)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            GenerationResult.Failure(
                message = e.message ?: "Generation failed",
                cause = e,
            )
        }
}
