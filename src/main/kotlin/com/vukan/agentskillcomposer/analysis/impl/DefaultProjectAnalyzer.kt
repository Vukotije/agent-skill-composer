package com.vukan.agentskillcomposer.analysis.impl

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.vukan.agentskillcomposer.analysis.*
import com.vukan.agentskillcomposer.model.*
import java.nio.file.Path
import java.util.concurrent.Callable

class DefaultProjectAnalyzer(private val project: Project) : ProjectAnalyzer {

    private val buildFileAnalyzer = BuildFileAnalyzer()
    private val languageDetector = LanguageDetector()
    private val namingConventionAnalyzer = NamingConventionAnalyzer()
    private val packageStructureAnalyzer = PackageStructureAnalyzer()
    private val frameworkDetector = FrameworkDetector()
    private val testConventionAnalyzer = TestConventionAnalyzer()
    private val diStyleAnalyzer = DiStyleAnalyzer()
    private val codePatternAnalyzer = CodePatternAnalyzer()
    private val buildCommandAnalyzer = BuildCommandAnalyzer()
    private val kotlinIdiomAnalyzer = KotlinIdiomAnalyzer()
    private val apiRouteAnalyzer = ApiRouteAnalyzer()
    private val concurrencyModelAnalyzer = ConcurrencyModelAnalyzer()
    private val validationAnalyzer = ValidationAnalyzer()
    private val moduleStructureAnalyzer = ModuleStructureAnalyzer()
    private val representativeFileSelector = RepresentativeFileSelector()
    private val skillSuggestionEngine = SkillSuggestionEngine()

    override fun analyze(indicator: ProgressIndicator?): AnalysisResult {
        val projectDir = project.guessProjectDir()
            ?: return AnalysisResult.Failure("No project directory found")
        val projectRoot = Path.of(project.basePath ?: return AnalysisResult.Failure("No project base path"))

        try {
            // Step 1: Detect build system
            progress(indicator, "Detecting build system\u2026", 0.0)
            val buildSystem = runSafe("BuildFileAnalyzer.detectBuildSystem") {
                ReadAction.nonBlocking(Callable {
                    buildFileAnalyzer.detectBuildSystem(projectDir)
                }).executeSynchronously()
            }

            indicator?.checkCanceled()

            // Step 2: Extract dependencies from IDE's resolved model
            progress(indicator, "Reading project dependencies\u2026", 0.10)
            val buildClues = if (buildSystem != null) {
                runSafe("BuildFileAnalyzer.extractBuildClues") {
                    buildFileAnalyzer.extractBuildClues(project, buildSystem)
                }
            } else null

            indicator?.checkCanceled()

            // Step 3: Detect languages and source/test roots
            progress(indicator, "Detecting languages\u2026", 0.20)
            val langResult = runSafe("LanguageDetector") {
                languageDetector.detect(project)
            } ?: LanguageResult(emptySet(), emptyList(), emptyList())

            indicator?.checkCanceled()

            // Step 4: Collect file index (centralized — still needed for naming/package analysis)
            progress(indicator, "Indexing project files\u2026", 0.30)
            val (sourceFiles, testFiles) = collectFileIndex(langResult.sourceRoots, langResult.testRoots)

            indicator?.checkCanceled()

            // Step 5: Analyze naming conventions (filename-based — correct for this concern)
            progress(indicator, "Analyzing naming conventions\u2026", 0.40)
            val namingConventions = runSafe("NamingConventionAnalyzer") {
                namingConventionAnalyzer.analyze(sourceFiles, testFiles)
            } ?: emptyList()

            indicator?.checkCanceled()

            // Step 6: Analyze package structure (directory-based — correct for this concern)
            progress(indicator, "Analyzing package structure\u2026", 0.50)
            val packageConventions = runSafe("PackageStructureAnalyzer") {
                packageStructureAnalyzer.analyze(langResult.sourceRoots)
            } ?: emptyList()

            indicator?.checkCanceled()

            // Step 7: Supplement frameworks from IDE facets
            progress(indicator, "Detecting frameworks\u2026", 0.60)
            val buildFrameworks = buildClues?.detectedFrameworks ?: emptyList()
            val facetFrameworks = runSafe("FrameworkDetector") {
                frameworkDetector.supplement(project, buildFrameworks)
            } ?: emptyList()
            val allFrameworks = (buildFrameworks + facetFrameworks).distinctBy { it.name }

            indicator?.checkCanceled()

            // Step 8: Analyze test conventions (PSI-based)
            progress(indicator, "Analyzing test conventions\u2026", 0.65)
            val testConventions = runSafe("TestConventionAnalyzer") {
                testConventionAnalyzer.analyze(project)
            } ?: emptyList()

            indicator?.checkCanceled()

            // Step 8b: Analyze DI style (PSI-based)
            progress(indicator, "Analyzing dependency injection style\u2026", 0.70)
            val diConvention = runSafe("DiStyleAnalyzer") {
                diStyleAnalyzer.analyze(project)
            }

            indicator?.checkCanceled()

            // Step 8c: Deep structural analysis — repository supertypes, controller patterns,
            // entity relationships, test slices, error handling (PSI type system)
            progress(indicator, "Analyzing code patterns\u2026", 0.72)
            val codePatterns = runSafe("CodePatternAnalyzer") {
                codePatternAnalyzer.analyze(project)
            } ?: emptyList()

            indicator?.checkCanceled()

            // Step 8d: Build & run commands
            progress(indicator, "Inferring build commands\u2026", 0.74)
            val buildCommands = runSafe("BuildCommandAnalyzer") {
                buildCommandAnalyzer.analyze(projectDir, buildClues?.buildSystem ?: buildSystem, allFrameworks)
            }

            indicator?.checkCanceled()

            // Step 8e: Kotlin idioms (data classes, sealed classes, coroutines, extensions)
            progress(indicator, "Analyzing Kotlin idioms\u2026", 0.76)
            val kotlinIdioms = runSafe("KotlinIdiomAnalyzer") {
                kotlinIdiomAnalyzer.analyze(project, sourceFiles)
            } ?: emptyList()

            indicator?.checkCanceled()

            // Step 8f: API route structure (actual URL paths from annotations)
            progress(indicator, "Extracting API routes\u2026", 0.78)
            val apiRoutes = runSafe("ApiRouteAnalyzer") {
                apiRouteAnalyzer.analyze(project)
            }

            indicator?.checkCanceled()

            // Step 8g: Concurrency model (coroutines vs reactive vs blocking)
            progress(indicator, "Analyzing concurrency model\u2026", 0.80)
            val concurrencyModel = runSafe("ConcurrencyModelAnalyzer") {
                concurrencyModelAnalyzer.analyze(project, sourceFiles)
            }

            indicator?.checkCanceled()

            // Step 8h: Validation patterns (@Valid, constraint annotations)
            progress(indicator, "Analyzing validation patterns\u2026", 0.82)
            val validationConvention = runSafe("ValidationAnalyzer") {
                validationAnalyzer.analyze(project)
            }

            indicator?.checkCanceled()

            // Step 8i: Module structure (multi-module projects)
            progress(indicator, "Analyzing module structure\u2026", 0.84)
            val moduleStructure = runSafe("ModuleStructureAnalyzer") {
                moduleStructureAnalyzer.analyze(project)
            }

            indicator?.checkCanceled()

            // Step 9: Select representative files (annotation search with filename fallback)
            progress(indicator, "Selecting representative files\u2026", 0.88)
            val allConventions = namingConventions + packageConventions + testConventions +
                listOfNotNull(diConvention) + codePatterns +
                listOfNotNull(buildCommands) + kotlinIdioms +
                listOfNotNull(apiRoutes, concurrencyModel, validationConvention, moduleStructure)
            val representativeFiles = runSafe("RepresentativeFileSelector") {
                representativeFileSelector.select(project, sourceFiles, testFiles, projectRoot)
            } ?: emptyList()

            indicator?.checkCanceled()

            // Step 10: Generate skill suggestions
            progress(indicator, "Generating skill suggestions\u2026", 0.90)
            val testFrameworks = buildClues?.detectedTestFrameworks ?: emptyList()
            val suggestedSkills = runSafe("SkillSuggestionEngine") {
                skillSuggestionEngine.suggest(allFrameworks, testFrameworks, allConventions)
            } ?: emptyList()

            // Step 11: Assemble ProjectFacts
            progress(indicator, "Analysis complete", 1.0)

            val facts = ProjectFacts(
                projectRoot = projectRoot,
                languages = langResult.languages,
                buildSystem = buildClues?.buildSystem ?: buildSystem,
                frameworks = allFrameworks,
                testFrameworks = testFrameworks,
                sourceRoots = langResult.sourceRoots.map { it.toNioPath() },
                testRoots = langResult.testRoots.map { it.toNioPath() },
                conventions = allConventions,
                representativeFiles = representativeFiles,
                suggestedSkills = suggestedSkills,
            )

            return AnalysisResult.Success(facts)
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.error("Project analysis failed", e)
            return AnalysisResult.Failure(e.message ?: "Unknown error", e)
        }
    }

    private fun collectFileIndex(
        sourceRoots: List<VirtualFile>,
        testRoots: List<VirtualFile>,
    ): Pair<List<VirtualFile>, List<VirtualFile>> {
        val allFiles = ReadAction.nonBlocking(Callable {
            val scope = GlobalSearchScope.projectScope(project)
            val kotlinFiles = FilenameIndex.getAllFilesByExt(project, "kt", scope)
            val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)
            (kotlinFiles + javaFiles).toList()
        }).inSmartMode(project).executeSynchronously()

        val sourceRootSet = sourceRoots.toSet()
        val testRootSet = testRoots.toSet()

        val sourceFiles = allFiles.filter { file ->
            sourceRootSet.any { root -> VfsUtilCore.isAncestor(root, file, true) }
        }
        val testFiles = allFiles.filter { file ->
            testRootSet.any { root -> VfsUtilCore.isAncestor(root, file, true) }
        }

        return sourceFiles to testFiles
    }

    private fun progress(indicator: ProgressIndicator?, text: String, fraction: Double) {
        indicator?.let {
            it.text = text
            it.fraction = fraction
        }
    }

    private fun <T> runSafe(analyzerName: String, block: () -> T): T? = try {
        block()
    } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("$analyzerName failed, continuing with partial results", e)
        null
    }

    companion object {
        private val LOG = Logger.getInstance(DefaultProjectAnalyzer::class.java)
    }
}
