# Architecture

## High-level idea

Agent Skill Composer has four main responsibilities:
- inspect the current IntelliJ project
- summarize repo facts and conventions
- use AI to generate agent-oriented artifacts from those facts
- save the generated artifacts into the correct layout for Junie or Claude

## Layers

### UI layer
Responsible for user interaction only.

Main responsibilities:
- tool window rendering
- trigger analysis
- collect generation options
- show preview and save actions
- display errors and progress

Implemented classes (in `ui/`):
- `AgentSkillToolWindowFactory` — registered in plugin.xml; registers the panel as the content disposer so the panel's coroutine scope is cancelled when the tool-window content is released
- `AgentSkillToolWindowPanel` — orchestrator; hosts the Analyze button, analysis summary, generation form, and status panel; owns a gear toolbar action for the settings dialog. Implements `Disposable` and owns a `CoroutineScope(SupervisorJob())` cancelled in `dispose()` so in-flight generation cannot fire callbacks into a disposed Swing tree.
- `AnalysisSummaryPanel` — renders `ProjectFacts`
- `GenerationFormPanel` — target combo, artifact-type checkboxes with inline resolved paths, optional instructions, Generate button, plus embedded progress bar / per-artifact status list / final summary. `setGenerating(Boolean)` freezes the whole form during a run and shows a spinner on the button. Progress APIs: `showProgress(total)`, `updateProgress(completed, total, result)`, `showDone(artifacts)`. Holds a hidden "Save All" button revealed by `showDone` when generation yielded artifacts; `setSaving(Boolean)` freezes it during writes. Retains the last generated list so the button can emit it via `onSaveAll`.
- `EditorPreviewHelper` — opens each generated artifact as a read-only `LightVirtualFile` in an editor tab. Chosen over a dedicated preview panel because editor tabs give users syntax highlighting, search, and a familiar UX. Preview stays read-only; the Save All button is the write path.
- Save orchestration (in `AgentSkillToolWindowPanel.onSaveAll`): resolves metadata per artifact → renders via `DefaultArtifactRenderer` → pre-checks existence with NIO `Files.exists` → if any conflicts, shows one `Messages.showYesNoDialog` listing all of them (Cancel drops the entire batch) → iterates `DefaultArtifactWriter.save` → aggregates `List<SaveResult>` → emits one info balloon with `created`/`overwritten` counts and a "Reveal in Project" action anchored on the first saved file, plus an error balloon per failed path.

### Analysis layer
Responsible for deterministic repository inspection using IntelliJ SDK APIs (PSI, resolved dependency model, facets, Kotlin PSI).

Main responsibilities:
- detect build system and resolve dependencies via `OrderEnumerator`
- detect languages, source/test roots via `ModuleRootManager`
- inspect project structure (naming, packages, module layout)
- detect conventions via PSI (`AnnotatedElementsSearch`, type resolution, Kotlin AST)
- extract structural patterns (repo supertypes, controller signatures, entity relationships)
- detect Kotlin idioms, API routes, concurrency model, validation, build commands
- choose representative code examples via annotation search with filename fallback

Implemented classes (in `analysis/`):
- `ProjectAnalyzer` — interface
- `CommonAnnotations` — shared annotation FQNs across analyzers
- `BuildClues` — intermediate data from dependency resolution
- `BuildFileAnalyzer` — build system detection + `OrderEnumerator` dependency extraction
- `LanguageDetector` — `ModuleRootManager` source/test roots + `FilenameIndex` language detection
- `NamingConventionAnalyzer` — filename suffix patterns (heuristic — filenames ARE the data)
- `PackageStructureAnalyzer` — directory layout: layer-based, hierarchical feature, flat feature
- `FrameworkDetector` — entry point annotations, config files, IDE facets
- `TestConventionAnalyzer` — `AnnotatedElementsSearch` for @Test method naming style
- `DiStyleAnalyzer` — PSI inspection of all Spring stereotypes for DI patterns
- `CodePatternAnalyzer` — repo supertypes, controller methods, entity relationships, test slices, error handling
- `BuildCommandAnalyzer` — inferred build/test/run commands from build system + frameworks
- `KotlinIdiomAnalyzer` — Kotlin PSI (`KtTreeVisitorVoid`) for data classes, sealed classes, coroutines, extensions
- `ApiRouteAnalyzer` — actual URL paths from `@RequestMapping`/`@GetMapping` annotation attributes
- `ConcurrencyModelAnalyzer` — coroutines vs reactive vs blocking classification
- `ValidationAnalyzer` — @Valid, constraint annotations, validation approach
- `ModuleStructureAnalyzer` — multi-module structure and inter-module dependencies
- `RepresentativeFileSelector` — annotation search (Spring, Micronaut, Jakarta) with filename fallback
- `SkillSuggestionEngine` — rule-based mapping from detected evidence to skill suggestions
- `impl/DefaultProjectAnalyzer` — orchestrator with centralized file index, error isolation, progress tracking

### Generation layer
Responsible for AI-backed synthesis.

Main responsibilities:
- convert facts into prompts
- call the configured AI provider
- post-process responses into artifacts

Implemented classes (in `generation/`):
- `AiProvider` — interface: `suspend fun generate(systemPrompt, userPrompt): String` + `suspend fun listModels(): List<String>`
- `AiProviderFactory` — object: two overloads. `create(PluginSettings)` requires a non-blank API key; `create(ProviderType, apiKey, baseUrl, modelName)` used by the settings dialog to probe models before saving.
- `PromptFactory` — interface: `buildSystemPrompt(target, artifactType)` + `buildUserPrompt(request)`
- `PromptTemplates` — static template text per (target, artifactType), 5 valid combinations
- `ArtifactGenerator` — interface: `suspend fun generate(request): GenerationResult`
- `impl/HttpAiProvider` — abstract base: JDK 21 `HttpClient` + bundled Gson, `withContext(Dispatchers.IO)`, unified `sendRequest` helper for both generate and list-models
- `impl/OpenAiCompatibleProvider` — `POST /chat/completions`, `GET /models`. Covers native OpenAI and any OpenAI-compatible endpoint (Ollama, Azure, local).
- `impl/AnthropicProvider` — `POST /v1/messages`, `GET /v1/models`, `anthropic-version: 2023-06-01`, `max_tokens: 8192`
- `impl/GeminiProvider` — `POST /v1beta/models/{model}:generateContent`, `GET /v1beta/models` filtered to entries whose `supportedGenerationMethods` contain `generateContent`
- `impl/DefaultPromptFactory` — serializes ProjectFacts into structured prompt sections; takes `TargetPathResolver` by constructor so the prompt's "File intention" line is resolved by the same authority that owns the write path (single source of truth)
- `impl/DefaultArtifactGenerator` — orchestrates PromptFactory → AiProvider → GeneratedArtifact. Rethrows `CancellationException`, wraps everything else in `GenerationResult.Failure`.

Settings (in `settings/`):
- `PluginSettings` — `PersistentStateComponent` for provider type, baseUrl, and model name; `PasswordSafe` for API key
- `ProviderType` — enum of `ANTHROPIC`, `OPENAI`, `GEMINI`, `OPENAI_COMPATIBLE` with display name + default base URL only. Model lists come from live fetch; no hardcoded IDs.
- `PluginSettingsConfigurable` — Settings > Tools > Agent Skill Composer (Kotlin UI DSL v2). Model fetch runs on `ApplicationManager.executeOnPooledThread` with `SwingUtilities.invokeLater` for EDT callbacks — coroutines are unreliable at application scope (Dispatchers.EDT doesn't resume after withContext(IO) in a Configurable lifecycle). `@Volatile fetching` flag prevents concurrent requests. Model combo stays editable so users can type IDs the server doesn't return.

### Output layer
Responsible for target-specific rendering and file writing.

Main responsibilities:
- map target + artifact type to output path
- resolve artifact metadata (frontmatter rules)
- build final markdown content
- write files safely

Implemented classes (in `output/`):
- `TargetPathResolver` — interface with `resolveRelativePath` (display) and `resolveOutputPath` (absolute `Path`)
- `DefaultTargetPathResolver` — exhaustive target+artifactType→path mapping with `require(applicableTo)` guard
- `ArtifactMetadata` — data class: fileName, relativePath, requiresFrontmatter, frontmatterTemplate
- `ArtifactMetadataResolver` — wraps `TargetPathResolver`, adds Junie skill frontmatter rules
- `SaveResult` — `sealed class` with `Saved(path, status)` / `Failed(path, message, cause)`; `SaveStatus { CREATED, OVERWRITTEN }`
- `ArtifactRenderer` — interface: `render(artifact, metadata): String`
- `impl/DefaultArtifactRenderer` — prepends YAML frontmatter when `metadata.requiresFrontmatter`; guarantees single trailing newline
- `ArtifactWriter` — interface: `save(projectRoot, metadata, content): SaveResult`
- `impl/DefaultArtifactWriter` — `WriteAction.computeAndWait` + `VfsUtil.createDirectoryIfMissing` + `VirtualFile.setBinaryContent` / `createChildData`. Returns `Saved(path, CREATED)` when the file didn't exist pre-write, `Saved(path, OVERWRITTEN)` when it did. Never prompts — the overwrite confirmation lives in the UI orchestrator so `output/` stays pure. Defense-in-depth: normalizes the resolved absolute path and asserts it still starts with the (normalized) project root before writing, so a future change to `TargetPathResolver` cannot traverse out of the project.

## Domain model

### `ProjectFacts`
Structured summary of the repository.

Possible fields:
- `projectRoot: Path`
- `languages: Set<ProjectLanguage>`
- `buildSystem: BuildSystem?`
- `frameworks: List<DetectedFramework>`
- `testFrameworks: List<TestFramework>`
- `sourceRoots: List<Path>`
- `testRoots: List<Path>`
- `conventions: List<DetectedConvention>`
- `representativeFiles: List<RepresentativeFile>`

### `DetectedConvention`
Possible fields:
- `type: ConventionType`
- `summary: String`
- `confidence: ConventionConfidence`
- `evidence: List<String>`

### `GenerationRequest`
Possible fields:
- `target: GenerationTarget`
- `artifactType: ArtifactType`
- `projectFacts: ProjectFacts`
- `userInstructions: String?`

### `GeneratedArtifact`
Possible fields:
- `target: GenerationTarget`
- `artifactType: ArtifactType`
- `title: String`
- `content: String`
- `defaultPath: String`

## Core design rules

### Rule 1: Evidence before generation
No generator should run without `ProjectFacts`.

### Rule 2: Keep target-specific logic isolated
Junie and Claude differences should live in output/rendering classes, not scattered through the UI.

### Rule 3: Provider abstraction from day one
AI calls must go through `AiProvider` so the implementation can change later without touching the rest of the plugin.

### Rule 4: Prefer typed models over string maps
Use Kotlin data classes for facts, requests, and artifacts.

### Rule 5: Keep analysis shallow but useful
Use reliable heuristics and examples. Do not attempt deep semantic indexing in MVP.

## Output target strategy

### Junie target
- project guidance file: `.junie/AGENTS.md`
- skills: `.junie/skills/<skill-name>/SKILL.md`

### Claude target
- project guidance file: `CLAUDE.md`
- skills: `.claude/skills/<skill-name>/SKILL.md`
- optional commands: `.claude/commands/<command>.md`

## Recommended artifact prompts

Prompt templates should always include:
- a concise project summary
- detected conventions
- representative examples
- the selected output target
- formatting constraints for the target file

## Error handling

Show user-facing errors for:
- no open project
- unsupported project type
- missing API key or provider settings
- AI request failure
- file write failure

## Test strategy

JUnit 5 on JUnit Platform (`useJUnitPlatform()`, `maxHeapSize = "512m"`). Pure unit tests only — no `BasePlatformTestCase`, no live `Project`. Platform-coupled code is covered by manual `runIde` walkthroughs, not automated tests, because the setup cost does not pay off for an MVP.

The `testFramework(TestFrameworkType.Platform)` dependency is intentionally NOT declared — it ships a `ServiceLoader`-registered `JUnit5TestSessionListener` that expects a booted `TestApplicationManager` at JUnit startup and crashes pure unit tests with "could not be instantiated". `LightVirtualFile` stays available via `core-impl.jar` from `intellijIdea(...)`. If someone later needs `BasePlatformTestCase`-style tests, they should live in a separate source set so the listener only loads for tests that actually boot the platform.

Covered today:
- `output/DefaultTargetPathResolverTest` — target → path mapping, `require` guards
- `output/ArtifactMetadataResolverTest` — frontmatter rules, YAML escaping
- `model/ArtifactTypeTest` — `applicableTo` matrix, `forTarget` ordering
- `generation/DefaultPromptFactoryTest` — prompt section assembly, LOW-confidence filter, evidence cap, empty-section omission
- `generation/PromptTemplatesTest` — backbone + target/artifact overlays; invalid combo throws
- `analysis/NamingConventionAnalyzerTest` — via `LightVirtualFile`; confidence thresholds, evidence cap, per-pattern coverage
- `analysis/BuildFileAnalyzerTest` — `parseCoordinate` / `detectFrameworks` / `detectTestFrameworks` via `@VisibleForTesting internal` helpers

Intentionally uncovered (recorded so future work knows why):
- `DefaultProjectAnalyzer` orchestrator and PSI analyzers — require a `Project`; covered by manual runIde
- `BuildFileAnalyzer.detectBuildSystem` / `collectLibraryCoordinates` — trivial IDE-API wrappers
- UI panels (Swing / Kotlin UI DSL) — covered manually in sandbox IDE

## Future extension points

After MVP, the architecture should support:
- more artifact types
- more AI providers
- update-in-place workflows
- more analyzers
