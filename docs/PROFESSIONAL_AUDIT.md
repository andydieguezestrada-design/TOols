# TOols 1.6 — Technical audit

This document is intentionally factual. TOols is a serious Android development workspace, but it is not presented as a full Android Studio replacement.

## Implemented in 1.6
- Project scaffolding/import/export with protected workspace paths.
- AI provider fallback architecture with configurable endpoints/models.
- AI response -> proposed multi-file change set.
- Human review screen with per-file change preview.
- Snapshot created before approved AI writes.
- Central `PathGuard` for user/AI file paths.
- Agent command boundary with approval gate for destructive/network-sensitive shell commands.
- Deterministic workspace read/list/search tools.
- Git/JGit commit + push and GitHub Actions dispatch/status.
- Build validation and CI workflow generation.
- Unit coverage for path security, AI file parsing and change planning.

## Deliberate limitations
- The Android device is not treated as a full Gradle/Flutter build host; CI provides the toolchains.
- The editor is intentionally lightweight; it is not a full syntax-aware IDE editor yet.
- GitHub token/repository administration and artifact download remain separate work items.
- AI writes are review-first; there is no autonomous destructive execution.

## Quality rule
No feature is described as "complete" unless it is wired into the application path and covered by a test or an explicit runtime validation path.
