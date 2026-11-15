package org.bond.pwaapp

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

class Edges(val left: Int, val top: Int, val right: Int, val bottom: Int, val keyboard: Int)

object Bars {
    fun edgeToEdge(window: Window, lightBars: Boolean) {
        window.decorView
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (lightBars) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        appearance(window, lightBars)
    }

    fun appearance(window: Window, lightBars: Boolean) {
        window.decorView
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (lightBars) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            flags = if (lightBars) flags or light else flags and light.inv()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    fun fullScreen(window: Window, on: Boolean) {
        window.decorView
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController ?: return
            if (on) {
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            val hide = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            flags = if (on) flags or hide else flags and hide.inv()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    fun edges(insets: WindowInsets, withBars: Boolean): Edges {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom
            val shown = if (withBars) bars else cutout
            return Edges(shown.left, shown.top, shown.right, maxOf(shown.bottom, keyboard), keyboard)
        }
        @Suppress("DEPRECATION")
        val bottom = insets.systemWindowInsetBottom
        @Suppress("DEPRECATION")
        val top = insets.systemWindowInsetTop
        @Suppress("DEPRECATION")
        val left = insets.systemWindowInsetLeft
        @Suppress("DEPRECATION")
        val right = insets.systemWindowInsetRight
        @Suppress("DEPRECATION")
        val stable = insets.stableInsetBottom
        val keyboard = if (bottom > stable + 50) bottom else 0
        return if (withBars) Edges(left, top, right, bottom, keyboard) else Edges(0, 0, 0, keyboard, keyboard)
    }
}

abstract class Screen : Activity() {
    lateinit var root: FrameLayout
    lateinit var content: LinearLayout
    lateinit var bottom: LinearLayout
    lateinit var scroll: ScrollView
    private var backCallback: Any? = null
    var guarded: Site? = null
    private var showingLock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Look.refresh(this)
        super.onCreate(savedInstanceState)
        Bars.edgeToEdge(window, !T.dark)
        root = FrameLayout(this)
        root.setBackgroundColor(T.bg)
        val column = column()
        scroll = ScrollView(this)
        scroll.isFillViewport = true
        content = column(12)
        content.setPadding(dp(16), dp(14), dp(16), dp(22))
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        column.put(scroll, MATCH_PARENT, 0, 1f)
        bottom = line(8)
        bottom.setPadding(dp(16), dp(10), dp(16), dp(12))
        bottom.setBackgroundColor(T.bg)
        bottom.visibility = View.GONE
        column.put(bottom, MATCH_PARENT, WRAP_CONTENT)
        root.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.setOnApplyWindowInsetsListener { view, insets ->
            val edges = Bars.edges(insets, true)
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            insets
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = OnBackInvokedCallback { if (!onBack()) finish() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!onBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    open fun onBack(): Boolean = false

    override fun onResume() {
        super.onResume()
        redraw()
    }

    abstract fun render()

    fun redraw() {
        val at = scroll.scrollY
        content.removeAllViews()
        bottom.removeAllViews()
        bottom.visibility = View.GONE
        paint()
        scroll.post { scroll.scrollTo(0, at) }
    }

    fun redrawTop() {
        content.removeAllViews()
        bottom.removeAllViews()
        bottom.visibility = View.GONE
        paint()
        scroll.scrollTo(0, 0)
    }

    private fun paint() {
        val watched = guarded?.let { SiteStore.get(this, it.id) }
        if (watched != null && watched.locks && watched.hideRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (watched != null && LockState.needed(this, watched)) {
            showingLock = true
            header(watched.label, "locked", site = watched)
            content.put(note("Unlock to see or change ${watched.label}’s settings and what it stores."))
            val panel = LockPanel(this, watched, T.ink, false) { redrawTop() }
            content.put(panel.view)
            scroll.post { panel.prompt() }
            return
        }
        showingLock = false
        render()
    }

    override fun onPause() {
        super.onPause()
        val watched = guarded ?: return
        if (!showingLock) LockState.touch(this, watched.id)
    }

    fun header(title: String, subtitle: String? = null, back: Boolean = true, site: Site? = null, trailing: View? = null) {
        val row = line(10)
        if (back) row.put(squareKey(Glyphs.ARROW_LEFT, "Back") { if (!onBack()) finish() }, dp(44), dp(44))
        if (site != null) row.put(appIcon(site, 44, 14f), dp(44), dp(44))
        val words = column(1)
        words.put(text(title, 20f, T.ink, Fonts.sansBold).oneLine())
        if (subtitle != null) words.put(text(subtitle, 12f, T.muted).oneLine())
        row.put(words, 0, WRAP_CONTENT, 1f)
        if (trailing != null) row.put(trailing, dp(44), dp(44))
        content.put(row)
    }

    fun showBottom() {
        bottom.visibility = View.VISIBLE
    }

    fun note(message: String): View {
        val view = text(message, 12f, T.muted)
        view.setPadding(dp(4), 0, dp(4), 0)
        return view
    }

    fun empty(message: String): View {
        val box = column()
        box.background = surface()
        box.setPadding(dp(16), dp(18), dp(16), dp(18))
        val words = text(message, 13f, T.muted)
        words.gravity = Gravity.CENTER
        box.put(words)
        return box
    }
}
