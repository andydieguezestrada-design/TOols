# TOols V31.9 — Project Brain

This release prioritizes durable project continuity and trustworthy workspace awareness.

## New
- Persistent project ledger with SHA-256 inventory.
- Detects files added, modified and removed while TOols was closed.
- Resume briefing combines previous goal/next steps/blockers with the current filesystem state.
- New agent tools: `workspace_scan`, `project_diff`, `resume_project`.
- Project ledger is checkpointed after agent rounds.
- RAG symbol matches receive an explicit relevance boost.

## Design principle
Memory is evidence, not truth. The current filesystem and fresh tool results always outrank stale memory/RAG.
