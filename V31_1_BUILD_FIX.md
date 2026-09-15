# TOols 3.1.1 — Build Fix

This patch fixes the Kotlin compilation errors reported by GitHub Actions after the Interface PRO update.

## Fixed
- `AdvancedTaskPlanner.kt`
  - Corrected all `complexity =` named arguments to `estimatedComplexity =` to match `Task`.
  - Corrected critical-complexity risk check.
  - Corrected nullable `maxOfOrNull` depth calculation.
- `EnhancedAgentEngine.kt`
  - Corrected language statistics to group `metadata.values`.
  - Replaced invalid `ifblank` with Kotlin `ifBlank`.
- `EnhancedCodeAnalyzer.kt`
  - Corrected invalid nested `Regex(...toRegex(...))` construction.
- `DashboardScreen.kt`
  - Corrected Material 3 `LinearProgressIndicator` call for the project's Compose version.
  - Made `QuickAction` a `RowScope` composable so `Modifier.weight()` is valid.
- `Navigation.kt`
  - Parenthesized conditional list concatenations to remove type-inference ambiguity.
- Build metadata updated to version 3.1.1 / code 32.

## Validation
A local Kotlin compiler pass over all project Kotlin sources was performed. The Android-specific dependency errors expected without the Android/Gradle classpath remain, but no syntax/semantic errors matching the reported CI failures were found in the patched files.

The GitHub Actions workflow remains configured to publish a single `TOols-APK` artifact.
