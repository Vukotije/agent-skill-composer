package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import java.util.concurrent.Callable

class ModuleStructureAnalyzer {

    fun analyze(project: Project): DetectedConvention? =
        ReadAction.nonBlocking(Callable {
            analyzeModules(project)
        }).inSmartMode(project).executeSynchronously()

    private fun analyzeModules(project: Project): DetectedConvention? {
        val moduleManager = ModuleManager.getInstance(project)
        val modules = moduleManager.modules

        if (modules.size <= 1) return null

        val meaningfulModules = modules.filter { module ->
            val name = module.name
            !name.endsWith(".main") && !name.endsWith(".test") && !name.endsWith(".integrationTest")
        }

        if (meaningfulModules.size <= 1) return null

        val evidence = mutableListOf<String>()

        for (module in meaningfulModules.take(MAX_MODULES)) {
            val rootManager = ModuleRootManager.getInstance(module)

            // Find module dependencies (other project modules this one depends on)
            val moduleDeps = rootManager.orderEntries
                .filterIsInstance<ModuleOrderEntry>()
                .mapNotNull { it.moduleName }
                .filter { depName ->
                    meaningfulModules.any { it.name == depName }
                }

            val sourceCount = rootManager.sourceRoots.size
            val description = buildString {
                append(module.name)
                if (moduleDeps.isNotEmpty()) {
                    append(" → depends on: ${moduleDeps.joinToString(", ")}")
                }
                append(" ($sourceCount source roots)")
            }
            evidence += description
        }

        if (evidence.isEmpty()) return null

        return DetectedConvention(
            type = ConventionType.MODULE_STRUCTURE,
            summary = "${meaningfulModules.size}-module project",
            confidence = ConventionConfidence.HIGH,
            evidence = evidence,
        )
    }

    companion object {
        private const val MAX_MODULES = 15
    }
}
