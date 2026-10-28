package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibility : AccessibilityService() {

    companion object {

        var active: JarvisAccessibility? = null
            private set

        fun isEnabled(): Boolean {
            return active != null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        // Commands are executed only when requested.
    }

    override fun onInterrupt() {
        // Accessibility interrupted.
    }

    fun goHome(): Boolean {
        return performGlobalAction(
            GLOBAL_ACTION_HOME
        )
    }

    fun goBack(): Boolean {
        return performGlobalAction(
            GLOBAL_ACTION_BACK
        )
    }

    fun openRecentApps(): Boolean {
        return performGlobalAction(
            GLOBAL_ACTION_RECENTS
        )
    }

    fun openNotifications(): Boolean {
        return performGlobalAction(
            GLOBAL_ACTION_NOTIFICATIONS
        )
    }

    fun openQuickSettings(): Boolean {
        return performGlobalAction(
            GLOBAL_ACTION_QUICK_SETTINGS
        )
    }

    fun scroll(up: Boolean): Boolean {

        val root = rootInActiveWindow ?: return false

        val direction = if (up) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        return try {
            scrollNode(root, direction)
        } finally {
            root.recycle()
        }
    }

    private fun scrollNode(
        node: AccessibilityNodeInfo,
        direction: Int
    ): Boolean {

        if (
            node.isScrollable &&
            node.performAction(direction)
        ) {
            return true
        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            val result = try {
                scrollNode(child, direction)
            } finally {
                child.recycle()
            }

            if (result) return true
        }

        return false
    }

    fun tapLabel(label: String): Boolean {

        if (label.isBlank()) return false

        val root = rootInActiveWindow ?: return false

        return try {

            val nodes = root.findAccessibilityNodeInfosByText(
                label
            )

            for (node in nodes) {

                val result = try {

                    val visibleText = node.text
                        ?.toString()
                        .orEmpty()

                    val description = node.contentDescription
                        ?.toString()
                        .orEmpty()

                    val matches =
                        visibleText.contains(
                            label,
                            ignoreCase = true
                        ) ||
                        description.contains(
                            label,
                            ignoreCase = true
                        )

                    if (matches) {
                        clickNode(node)
                    } else {
                        false
                    }

                } finally {
                    node.recycle()
                }

                if (result) return true
            }

            false

        } finally {
            root.recycle()
        }
    }

    private fun clickNode(
        node: AccessibilityNodeInfo
    ): Boolean {

        var current: AccessibilityNodeInfo? =
            AccessibilityNodeInfo.obtain(node)

        while (current != null) {

            if (
                current.isClickable &&
                current.isEnabled
            ) {

                val result = current.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

                current.recycle()

                return result
            }

            val parent = current.parent

            current.recycle()

            current = parent
        }

        return false
    }

    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 400L
    ): Boolean {

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    duration
                )
            )
            .build()

        return dispatchGesture(
            gesture,
            null,
            null
        )
    }

    override fun onDestroy() {

        if (active === this) {
            active = null
        }

        super.onDestroy()
    }
}
