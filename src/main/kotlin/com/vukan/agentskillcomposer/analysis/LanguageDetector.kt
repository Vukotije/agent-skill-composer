package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.vukan.agentskillcomposer.model.ProjectLanguage
import java.util.concurrent.Callable

data class LanguageResult(
    val languages: Set<ProjectLanguage>,
    val sourceRoots: List<VirtualFile>,
    val testRoots: List<VirtualFile>,
)

class LanguageDetector {

    fun detect(project: Project): LanguageResult =
        ReadAction.nonBlocking(Callable {
            val sourceRoots = mutableListOf<VirtualFile>()
            val testRoots = mutableListOf<VirtualFile>()

            for (module in ModuleManager.getInstance(project).modules) {
                val rootManager = ModuleRootManager.getInstance(module)
                for (entry in rootManager.contentEntries) {
                    for (sourceFolder in entry.sourceFolders) {
                        val root = sourceFolder.file ?: continue
                        if (sourceFolder.isTestSource) {
                            testRoots += root
                        } else {
                            sourceRoots += root
                        }
                    }
                }
            }

            val scope = GlobalSearchScope.projectScope(project)
            val languages = buildSet {
                if (FilenameIndex.getAllFilesByExt(project, "kt", scope).isNotEmpty()) {
                    add(ProjectLanguage.KOTLIN)
                }
                if (FilenameIndex.getAllFilesByExt(project, "java", scope).isNotEmpty()) {
                    add(ProjectLanguage.JAVA)
                }
            }

            LanguageResult(languages, sourceRoots, testRoots)
        }).inSmartMode(project).executeSynchronously()
}
