package com.vukan.agentskillcomposer.output.impl

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.vukan.agentskillcomposer.output.ArtifactMetadata
import com.vukan.agentskillcomposer.output.ArtifactWriter
import com.vukan.agentskillcomposer.output.SaveResult
import com.vukan.agentskillcomposer.output.SaveStatus
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class DefaultArtifactWriter : ArtifactWriter {

    override fun save(
        projectRoot: Path,
        metadata: ArtifactMetadata,
        content: String,
    ): SaveResult {
        val normalizedRoot = projectRoot.toAbsolutePath().normalize()
        val absolutePath = normalizedRoot.resolve(metadata.relativePath).normalize()
        // Defense in depth: today's TargetPathResolver hardcodes safe paths, but a future
        // change (or an attacker-controlled metadata source) must not escape project root.
        require(absolutePath.startsWith(normalizedRoot)) {
            "Resolved path escapes project root: $absolutePath"
        }
        return try {
            WriteAction.computeAndWait<SaveResult, IOException> {
                val rootVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(normalizedRoot)
                    ?: throw IOException("Project root not found in VFS: $normalizedRoot")

                val relativePath = Path.of(metadata.relativePath)
                val parentRelative = relativePath.parent?.joinToString("/") { it.toString() }
                val parentDir = if (parentRelative.isNullOrEmpty()) {
                    rootVf
                } else {
                    VfsUtil.createDirectoryIfMissing(rootVf, parentRelative)
                        ?: throw IOException("Could not create directory: $parentRelative")
                }

                val bytes = content.toByteArray(StandardCharsets.UTF_8)
                val existing = parentDir.findChild(metadata.fileName)

                if (existing != null) {
                    existing.setBinaryContent(bytes)
                    SaveResult.Saved(absolutePath, SaveStatus.OVERWRITTEN)
                } else {
                    parentDir.createChildData(this, metadata.fileName).setBinaryContent(bytes)
                    SaveResult.Saved(absolutePath, SaveStatus.CREATED)
                }
            }
        } catch (e: IOException) {
            SaveResult.Failed(absolutePath, e.message ?: "Write failed", e)
        }
    }
}
