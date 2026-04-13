package com.vukan.agentskillcomposer.output

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget
import java.nio.file.Path

typealias PathResolverFn = (GenerationTarget, ArtifactType) -> String

interface TargetPathResolver {

    fun resolveRelativePath(target: GenerationTarget, artifactType: ArtifactType): String

    fun resolveOutputPath(
        projectRoot: Path,
        target: GenerationTarget,
        artifactType: ArtifactType,
    ): Path = projectRoot.resolve(resolveRelativePath(target, artifactType))
}
