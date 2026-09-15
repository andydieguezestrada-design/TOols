# TOols Professional — Architecture

TOols is organized as a mobile development workspace rather than a collection of demo screens.

## Layers

- `ui/`: Compose screens and navigation only.
- `domain/`: stable models and use cases.
- `data/`: Room persistence and repositories.
- `workspace/`: filesystem/project operations.
- `core/security/`: path and security policies.
- `ai/`: provider abstraction and fallback.
- `integration/github/`: GitHub/CI integration.
- `core/permissions/`: explicit Android permission policy.

## Safety rules

1. AI-generated paths are validated by `PathGuard`.
2. AI file changes create a snapshot before writing.
3. `.git` content is never exported with a project ZIP.
4. API keys are kept out of project context.
5. Broad external-storage permissions are intentionally avoided; Android SAF is preferred.
6. Release minification is disabled until shrinker rules are covered by tests.

## AI workflow

Project context -> AI response -> structured file parser -> snapshot -> validated apply.

The current release does not pretend that Android/Gradle toolchains exist locally on every phone. CI/CD is used for full builds.

## CI/CD

Generated Android/Flutter projects contain a GitHub Actions workflow. TOols can synchronize a repository, dispatch the workflow, and inspect the latest run.

## Verification policy

A feature is considered implemented only when it has executable code, error handling, and (where practical) unit coverage. UI placeholders are not considered implementations.
