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

Suggested classes:
- `AgentSkillToolWindowFactory`
- `AgentSkillToolWindowPanel`
- `AnalysisSummaryPanel`
- `GenerationFormPanel`
- `ArtifactPreviewPanel`

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

Suggested classes:
- `PromptFactory`
- `AiProvider`
- `AnthropicProvider` or `OpenAiCompatibleProvider`
- `ProjectGuidanceGenerator`
- `SkillGenerator`
- `CommandGenerator`

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

Planned classes (M5):
- `ArtifactRenderer`
- `ArtifactWriter`
- `SaveResult`

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

Focus on lightweight tests first.

Good candidates:
- target path resolution
- prompt assembly from facts
- artifact filename generation
- convention extraction heuristics for small fixtures

Keep UI tests minimal in MVP.

## Future extension points

After MVP, the architecture should support:
- more artifact types
- more AI providers
- update-in-place workflows
- MCP export layer
- more analyzers
