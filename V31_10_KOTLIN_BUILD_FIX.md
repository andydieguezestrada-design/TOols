# TOols V31.10 — Kotlin build fixes

Fixed the compile errors reported by GitHub Actions:

- `AgentKnowledgeStore`: stores the Android `Context` as a property so RAG/local-model calls compile.
- `AIProviderManager`: corrected invalid Kotlin `when` expression (`else` nesting).
- `PermissionManager`: replaced nonexistent `AccessibilityManager.getInstance(context)` with Android system-service lookup.
- `SettingsScreen`: same accessibility-service lookup fix.
- Version bumped to 3.1.10 / versionCode 41.

The reported failure was at `:app:compileDebugKotlin`; resource processing had already passed.
