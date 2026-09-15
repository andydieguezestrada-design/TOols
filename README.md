# TOols 1.6 — AI Development Workspace

TOols is an Android workspace for creating, inspecting, editing, validating and synchronizing software projects from a phone. Its AI layer is provider-agnostic and uses a review-first change workflow.

## Core workflow
1. Create or import a project.
2. Inspect and edit files.
3. Ask the AI to analyze or change the project.
4. TOols converts generated files into a proposed change set.
5. Review the per-file changes.
6. Approve -> snapshot -> apply.
7. Validate and send the project to GitHub Actions when a full build toolchain is required.

## Safety
AI-supplied paths pass through `PathGuard`. Destructive/network-sensitive terminal commands require explicit approval. AI changes are not written directly from the chat response.

## Honest scope
This is a serious development workspace, not a claim to replace Android Studio. The architecture is intentionally modular so build orchestration, richer editor services, artifact handling and repository administration can be added without rewriting the core.

See `ARCHITECTURE.md` and `docs/PROFESSIONAL_AUDIT.md`.

## Device file management + AI agent (2.7)

TOols can operate as a user-controlled Android file manager. From **Archivos**, the user can grant the special Android **All files access** permission and browse shared storage. The AI chat can then issue structured file actions (`list`, `search`, `read`, `mkdir`, `write`, `rename`, `copy`, `move`, `delete`). Safe actions execute automatically; destructive actions require an explicit approval dialog.

This does not bypass Android security boundaries. Areas protected by Android remain protected, and arbitrary device/UI control is not implied by file access.


## TOols 2.9.0 — Motion System
- Animated window/screen transitions.
- Subtle fade + slide + scale motion for navigation.
- Animated task/progress indicator for operations.
- Motion is intentionally fast and restrained so animations do not interfere with productivity.
- Designed to extend later with typing/cursor, file-operation, build, AI-thinking and success/error micro-interactions.
