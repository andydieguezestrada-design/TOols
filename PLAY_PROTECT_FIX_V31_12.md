# TOols V31.12 — Play Protect install fix

The Play Protect block was caused by the declared Android Accessibility Service.

Changes:
- Removed the `AccessibilityService` component from the release manifest.
- Removed the Accessibility permission/control card from Settings so the UI no longer advertises an unavailable service.
- Other TOols capabilities and existing project files are preserved, including file management, AI/provider routing, camera/microphone, project tools and CI integration.

Important: device-wide accessibility automation is disabled in this build. It must not be re-added to this variant if the goal is to avoid the specific Play Protect sideload block.
