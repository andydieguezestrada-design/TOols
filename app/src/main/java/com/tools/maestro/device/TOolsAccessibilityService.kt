package com.tools.maestro.device

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Explicitly user-enabled automation bridge for TOols.
 * No action is executed merely by declaring this service; Android requires the user to enable it.
 */
class TOolsAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
