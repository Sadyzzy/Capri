package com.capri.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CapriAccessibility : AccessibilityService() {
    companion object { var instance: CapriAccessibility? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(i: Intent?): Boolean { instance = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun clickByLabel(labels: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (l in labels) for (n in root.findAccessibilityNodeInfosByText(l)) {
            var x: AccessibilityNodeInfo? = n
            while (x != null && !x.isClickable) x = x.parent
            if (x != null && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun screenText(): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            n.text?.let { sb.append(it).append('\n') }
            n.contentDescription?.let { sb.append(it).append('\n') }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(rootInActiveWindow)
        return sb.toString().take(4000).ifBlank { "(tela sem texto legível)" }
    }

    fun nav(action: String): Boolean = performGlobalAction(when (action) {
        "back" -> GLOBAL_ACTION_BACK
        "recents" -> GLOBAL_ACTION_RECENTS
        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
        else -> GLOBAL_ACTION_HOME
    })

    fun typeText(t: String): Boolean {
        var f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (f == null) {
            fun edit(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (n == null) return null
                if (n.isEditable) return n
                for (i in 0 until n.childCount) edit(n.getChild(i))?.let { return it }
                return null
            }
            f = edit(rootInActiveWindow)?.also { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }
        if (f == null) return false
        val b = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t)
        }
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    fun scroll(forward: Boolean): Boolean {
        fun find(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        val s = find(rootInActiveWindow) ?: return false
        return s.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                               else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
}
