package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.vfs.VirtualFile
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention

class PackageStructureAnalyzer {

    fun analyze(sourceRoots: List<VirtualFile>): List<DetectedConvention> {
        val conventions = mutableListOf<DetectedConvention>()

        for (root in sourceRoots) {
            val basePackageDir = findBasePackageDir(root) ?: root
            val childDirs = basePackageDir.children?.filter { it.isDirectory } ?: continue

            val childNames = childDirs.map { it.name }.toSet()
            val layerMatches = childNames.intersect(LAYER_NAMES)

            if (layerMatches.size >= 3) {
                conventions += DetectedConvention(
                    type = ConventionType.PACKAGE_STRUCTURE,
                    summary = "Layer-based package layout: ${layerMatches.sorted().joinToString(", ")}",
                    confidence = ConventionConfidence.HIGH,
                    evidence = childDirs
                        .filter { it.name in LAYER_NAMES }
                        .map { relativePath(root, it) },
                )
                continue
            }

            val hierarchicalEvidence = detectHierarchicalFeature(childDirs)
            if (hierarchicalEvidence.isNotEmpty()) {
                conventions += DetectedConvention(
                    type = ConventionType.PACKAGE_STRUCTURE,
                    summary = "Feature-based package layout with sub-packages",
                    confidence = if (hierarchicalEvidence.size >= 3) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
                    evidence = hierarchicalEvidence.take(MAX_EVIDENCE),
                )
                continue
            }

            val flatEvidence = detectFlatFeature(childDirs)
            if (flatEvidence.isNotEmpty()) {
                conventions += DetectedConvention(
                    type = ConventionType.PACKAGE_STRUCTURE,
                    summary = "Feature-based package layout: ${flatEvidence.map { it.substringBefore(" ") }.joinToString(", ")}",
                    confidence = if (flatEvidence.size >= 3) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
                    evidence = flatEvidence.take(MAX_EVIDENCE),
                )
            }
        }

        return conventions.distinctBy { it.summary }
    }

    /**
     * Walks down single-child directories to find the first directory with multiple children,
     * which is typically the base package (e.g., com/example/app -> app is the base).
     */
    private fun findBasePackageDir(root: VirtualFile): VirtualFile? {
        var current = root
        while (true) {
            val subdirs = current.children?.filter { it.isDirectory } ?: return current
            if (subdirs.size == 1) {
                current = subdirs.single()
            } else {
                return current
            }
        }
    }

    /**
     * Detects hierarchical feature-based layout: multiple sibling directories that each
     * contain similarly-named sub-packages (e.g., user/service + order/service).
     */
    private fun detectHierarchicalFeature(siblingDirs: List<VirtualFile>): List<String> {
        if (siblingDirs.size < 2) return emptyList()

        val dirToChildren = siblingDirs.associateWith { dir ->
            dir.children?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()
        }

        val commonSubNames = dirToChildren.values
            .filter { it.isNotEmpty() }
            .flatMap { it }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 2 }
            .keys

        if (commonSubNames.isEmpty()) return emptyList()

        return dirToChildren
            .filter { (_, children) -> children.any { it in commonSubNames } }
            .map { (dir, children) ->
                val shared = children.intersect(commonSubNames)
                "${dir.name}/ (contains: ${shared.sorted().joinToString(", ")})"
            }
    }

    /**
     * Flat feature-based layout: sibling directories where each mixes architectural roles
     * (e.g. owner/ contains OwnerController + OwnerRepository + Owner).
     */
    private fun detectFlatFeature(siblingDirs: List<VirtualFile>): List<String> {
        if (siblingDirs.size < 2) return emptyList()

        val featurePackages = mutableListOf<String>()

        for (dir in siblingDirs) {
            val files = dir.children?.filter { !it.isDirectory } ?: continue
            val fileNames = files.map { it.nameWithoutExtension }.toSet()

            val rolesFound = ROLE_SUFFIXES.count { suffix ->
                fileNames.any { it.endsWith(suffix) }
            }

            if (rolesFound >= 2) {
                val roles = ROLE_SUFFIXES.filter { suffix -> fileNames.any { it.endsWith(suffix) } }
                featurePackages += "${dir.name} (${roles.joinToString(", ") { "*$it" }})"
            }
        }

        return featurePackages
    }

    private fun relativePath(root: VirtualFile, child: VirtualFile): String {
        val rootPath = root.path
        val childPath = child.path
        return if (childPath.startsWith(rootPath)) {
            childPath.removePrefix(rootPath).removePrefix("/")
        } else {
            child.name
        }
    }

    companion object {
        private const val MAX_EVIDENCE = 5

        private val LAYER_NAMES = setOf(
            "controller", "controllers",
            "service", "services",
            "repository", "repositories",
            "model", "models", "domain", "entity", "entities",
            "config", "configuration",
            "dto",
            "mapper", "mappers",
            "util", "utils", "common",
        )

        private val ROLE_SUFFIXES = listOf(
            "Controller", "RestController",
            "Service",
            "Repository",
            "Entity",
            "Config", "Configuration",
        )
    }
}
