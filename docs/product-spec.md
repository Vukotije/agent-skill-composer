# Agent Skill Composer

## Concept

Agent Skill Composer is an IntelliJ plugin that analyzes a Kotlin/Java project, extracts repository conventions and representative examples, and uses AI to generate project-specific guidance and reusable skills for coding agents.

The plugin is AI-first but evidence-grounded.
AI is mandatory for generation.
Repository analysis is mandatory before generation.

## Why this exists

A raw prompt to an agent can produce a draft `AGENTS.md` or skill file, but it often misses repository-specific conventions, gives generic advice, and is not repeatable.
This plugin adds value by:
- extracting structured repository evidence inside IntelliJ
- presenting detected conventions
- generating guidance from those facts
- saving artifacts directly into agent-specific project layouts

## Users

Primary user:
- a developer using IntelliJ who wants agents such as Junie or Claude Code to follow repository conventions better

Secondary user:
- a reviewer of the GitHub project who needs to understand the value quickly

## Product goals

- Generate project-specific agent guidance from real repo evidence
- Support both Junie and Claude output targets
- Demonstrate strong plugin structure and product thinking
- Stay small enough to finish and demo confidently

## Non-goals

- Generic chat assistant
- MCP support (descoped)
- Full language-agnostic code understanding
- Full autonomous agent execution
- Deep repository indexing or huge multi-file intelligence

## Supported targets

### Junie
- `.junie/AGENTS.md`
- `.junie/skills/<skill-name>/SKILL.md`

### Claude
- `CLAUDE.md`
- `.claude/skills/<skill-name>/SKILL.md`
- optional `.claude/commands/<command>.md`

## MVP workflow

1. User opens the plugin tool window
2. User clicks Analyze Project
3. Plugin extracts repo facts and detected conventions
4. User selects output target: Junie or Claude
5. User selects artifact type
6. Plugin builds a structured prompt from repo evidence
7. AI generates a draft artifact
8. User previews and optionally edits
9. User saves artifact to the correct path for the selected target

## MVP artifact types

### Required
- Project guidance
- Write tests skill
- Implement service skill

### Optional if time permits
- Review changes command for Claude
- Review changes skill for Junie

## Deterministic analysis responsibilities

The analysis layer should produce structured facts such as:
- build system
- language mix
- frameworks and libraries
- package structure patterns
- naming conventions
- test framework and style
- DI patterns
- representative examples

The analysis layer should not generate prose guidance.

## AI responsibilities

The AI layer should:
- interpret which repo patterns matter most for agents
- draft guidance text and instructions
- produce artifact content in the correct target style
- adapt wording to the repository context

## Architecture overview

### ui
- tool window
- analysis summary panel
- generation form
- preview panel

### analysis
- build file scanner
- package structure analyzer
- framework detector
- naming convention analyzer
- test convention analyzer
- representative file selector

### generation
- prompt factory
- AI provider interface
- concrete HTTP provider
- artifact generators

### output
- target-specific path resolution
- markdown rendering helpers
- file writing service

### model
- typed models for facts, conventions, artifact requests, and generated artifacts

### settings
- provider settings
- model selection
- API key storage/preferences

## Evidence-first rule

Every generated artifact must be based on a `ProjectFacts` structure.
No feature should generate output directly from the current project without first collecting facts.

## Output target behavior

### Junie target
- use `.junie/AGENTS.md` for project guidance
- use `.junie/skills/<name>/SKILL.md` for skills
- ensure skill files include the required `name` and `description` frontmatter expected by Junie

### Claude target
- use `CLAUDE.md` for project guidance by default
- use `.claude/skills/<name>/SKILL.md` for skills
- optionally support `.claude/commands/<name>.md` for reusable commands

## Risks

### Risk: generic output
Mitigation:
- feed AI concrete repo evidence and examples
- show evidence in the UI
- constrain artifact templates and prompts

### Risk: too much IntelliJ SDK complexity
Mitigation:
- keep analysis shallow and targeted
- start with files, source roots, PSI basics, and a few analyzers

### Risk: API/provider complexity
Mitigation:
- implement one provider first
- keep provider layer narrow
- use user-supplied key in MVP

## Success criteria

The plugin is successful if:
- it runs inside the IntelliJ sandbox IDE
- it analyzes a real Kotlin/Java repo
- it generates at least one valid Junie artifact and one valid Claude artifact
- the generated content reflects real repo patterns rather than generic boilerplate
- the codebase is well-organized and understandable
