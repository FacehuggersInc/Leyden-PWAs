package org.bond.pwaapp

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Lock {
    const val REQUEST = 4101

    fun available(activity: Activity): Boolean {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        return keyguard?.isDeviceSecure == true
    }

    fun ask(activity: Activity, title: String, onDone: (Boolean) -> Unit) {
        if (!available(activity)) {
            onDone(false)
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle("Unlock to open it")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, callback(onDone))
            return
        }
        if (Build.VERSION.SDK_INT == 29) {
            @Suppress("DEPRECATION")
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle("Unlock to open it")
                .setDeviceCredentialAllowed(true)
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, callback(onDone))
            return
        }
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent: Intent? = keyguard?.createConfirmDeviceCredentialIntent(title, "Unlock to open it")
        if (intent == null) {
            onDone(false)
            return
        }
        pending = onDone
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, REQUEST)
    }

    private var pending: ((Boolean) -> Unit)? = null

    fun result(code: Int) {
        val done = pending ?: return
        pending = null
        done(code == Activity.RESULT_OK)
    }

    @android.annotation.TargetApi(28)
    private fun callback(onDone: (Boolean) -> Unit) = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onDone(true)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onDone(false)
        }
    }

    fun modeName(mode: String): String = when (mode) {
        "fingerprint" -> "fingerprint"
        "pin" -> "PIN"
        "either" -> "fingerprint or PIN"
        else -> "off"
    }

    fun afterName(seconds: Int): String = when {
        seconds <= 0 -> "right away"
        seconds < 120 -> "after 1 minute"
        else -> "after ${seconds / 60} minutes"
    }
}

object Pin {
    private const val ROUNDS = 20000

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ROUNDS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun hash(pin: String): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val made = derive(pin, salt)
        return "v1:" + pin.length + ":" + Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(made, Base64.NO_WRAP)
    }

    fun length(stored: String?): Int = stored?.split(':')?.getOrNull(1)?.toIntOrNull() ?: 0

    fun matches(pin: String, stored: String?): Boolean {
        val parts = stored?.split(':') ?: return false
        if (parts.size != 4 || parts[0] != "v1") return false
        if (parts[1].toIntOrNull() != pin.length) return false
        return try {
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            MessageDigest.isEqual(derive(pin, salt), expected)
        } catch (error: IllegalArgumentException) {
            false
        }
    }
}

object LockState {
    private const val NAME = "lockstate"
    private const val TRIES = 5
    private const val WAIT = 30_000L
    const val GRACE = 5_000L

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun last(context: Context, id: String): Long = prefs(context).getLong("$id.last", 0L)

    fun touch(context: Context, id: String) {
        prefs(context).edit().putLong("$id.last", System.currentTimeMillis()).apply()
    }

    fun unlocked(context: Context, id: String) {
        prefs(context).edit().putLong("$id.last", System.currentTimeMillis()).putInt("$id.fails", 0).remove("$id.until").apply()
    }

    fun fails(context: Context, id: String): Int = prefs(context).getInt("$id.fails", 0)

    fun waitUntil(context: Context, id: String): Long = prefs(context).getLong("$id.until", 0L)

    fun failed(context: Context, id: String) {
        val count = fails(context, id) + 1
        val edit = prefs(context).edit()
        if (count >= TRIES) {
            edit.putInt("$id.fails", 0).putLong("$id.until", System.currentTimeMillis() + WAIT)
        } else {
            edit.putInt("$id.fails", count)
        }
        edit.apply()
    }

    fun forgetUnlock(context: Context, id: String) {
        prefs(context).edit().remove("$id.last").apply()
    }

    fun clear(context: Context, id: String) {
        prefs(context).edit().remove("$id.last").remove("$id.fails").remove("$id.until").apply()
    }

    fun needed(context: Context, site: Site): Boolean {
        if (!site.locks) return false
        val away = System.currentTimeMillis() - last(context, site.id)
        return away > maxOf(site.relockAfter * 1000L, GRACE)
    }
}

class LockPanel(
    private val activity: Activity,
    private val site: Site,
    private val ink: Int,
    private val onColor: Boolean,
    private val onUnlocked: () -> Unit,
) {
    val view: LinearLayout = activity.column(10)
    private var typing = !site.usesFinger || !Lock.available(activity)
    private val entered = StringBuilder()
    private var dots: LinearLayout? = null
    private var message: TextView? = null
    private val main = Handler(Looper.getMainLooper())

    init {
        view.gravity = Gravity.CENTER_HORIZONTAL
        build()
    }

    private fun fingerOk(): Boolean = site.usesFinger && Lock.available(activity)

    fun prompt() {
        if (!typing && fingerOk()) finger()
    }

    private fun finger() {
        Lock.ask(activity, "Open ${site.label}") { ok -> if (ok) pass() }
    }

    private fun pass() {
        LockState.unlocked(activity, site.id)
        onUnlocked()
    }

    private fun build() {
        view.removeAllViews()
        if (typing && site.usesPin) {
            keypad()
            return
        }
        if (fingerOk()) {
            view.put(bigButton("Use fingerprint", Glyphs.FINGER, true) { finger() }, MATCH_PARENT, activity.dp(54))
        }
        if (site.usesPin) {
            view.put(bigButton("Use PIN instead", Glyphs.GRID, false) {
                typing = true
                build()
            }, MATCH_PARENT, activity.dp(50))
        }
        if (!fingerOk() && !site.usesPin) {
            view.put(activity.text("Set a screen lock on this phone, or a PIN for ${site.label}, to unlock it. Removing the app from the list also removes its lock.", 13f, ink).apply { gravity = Gravity.CENTER })
        }
    }

    private fun bigButton(label: String, icon: String, primary: Boolean, onClick: () -> Unit): View {
        val button = activity.line(10)
        button.gravity = Gravity.CENTER
        val r = activity.dpf(16f)
        if (onColor) {
            button.background = if (primary) {
                val layers = android.graphics.drawable.LayerDrawable(arrayOf(rounded(0xFFD8D8D3.toInt(), r), rounded(Color.WHITE, r)))
                layers.setLayerInset(1, 0, 0, 0, activity.dp(2))
                activity.ripple(layers, 16f)
            } else {
                activity.ripple(rounded(0x1FFFFFFF, r, activity.dp(1), 0x73FFFFFF), 16f)
            }
        } else {
            button.background = if (primary) activity.primaryBack(16f) else activity.secondaryBack(16f)
        }
        val color = when {
            onColor && primary -> 0xFF1C1C1C.toInt()
            onColor -> Color.WHITE
            primary -> T.onAccent
            else -> T.ink
        }
        val iconColor = if (onColor && primary) T.accent else color
        button.put(activity.glyph(icon, iconColor, 20, 2f), activity.dp(20), activity.dp(20))
        button.put(activity.text(label, 15f, color, Fonts.sansBold), WRAP_CONTENT)
        button.isClickable = true
        button.contentDescription = label
        button.setOnClickListener { onClick() }
        return button
    }

    private fun keypad() {
        view.put(activity.text("${site.label}’s PIN", 16f, ink, Fonts.sansBold).apply { gravity = Gravity.CENTER })
        val row = activity.line(16)
        row.gravity = Gravity.CENTER
        dots = row
        view.put(row, WRAP_CONTENT, activity.dp(22)).margins(0, activity.dp(8), 0, activity.dp(4))
        val note = activity.text("", 12f, ink)
        note.gravity = Gravity.CENTER
        message = note
        view.put(note, WRAP_CONTENT)
        paintDots()
        val pad = Keypad.make(activity, ink, onColor, if (fingerOk()) ({ finger() }) else null, { digit -> type(digit) }, {
            if (entered.isNotEmpty()) entered.setLength(entered.length - 1)
            paintDots()
        })
        view.put(pad, WRAP_CONTENT, WRAP_CONTENT)
        waitNote()
    }

    private fun waitNote() {
        val until = LockState.waitUntil(activity, site.id)
        val left = until - System.currentTimeMillis()
        if (left > 0) {
            message?.text = "Too many wrong tries. Try again in ${left / 1000 + 1} s."
            main.postDelayed({ waitNote() }, 1000)
        } else if (message?.text?.startsWith("Too many") == true) {
            message?.text = ""
        }
    }

    private fun paintDots() {
        val row = dots ?: return
        row.removeAllViews()
        val shown = Pin.length(site.pinHash).coerceIn(4, 8)
        for (index in 0 until shown) {
            val dot = View(activity)
            dot.background = if (index < entered.length) rounded(ink, activity.dpf(4f)) else rounded(Color.TRANSPARENT, activity.dpf(4f), activity.dp(2), ink and 0x00FFFFFF or 0x99000000.toInt())
            row.put(dot, activity.dp(14), activity.dp(14))
        }
        row.contentDescription = "${entered.length} digits entered"
    }

    private fun type(digit: Char) {
        if (System.currentTimeMillis() < LockState.waitUntil(activity, site.id)) return
        val size = Pin.length(site.pinHash).coerceIn(4, 8)
        if (entered.length >= size) return
        entered.append(digit)
        paintDots()
        if (entered.length == size) {
            val attempt = entered.toString()
            view.postDelayed({
                if (Pin.matches(attempt, site.pinHash)) {
                    entered.setLength(0)
                    pass()
                } else {
                    wrong()
                }
            }, 80)
        }
    }

    private fun wrong() {
        entered.setLength(0)
        paintDots()
        LockState.failed(activity, site.id)
        val left = 5 - LockState.fails(activity, site.id)
        message?.text = if (LockState.waitUntil(activity, site.id) > System.currentTimeMillis()) "" else "Wrong PIN. $left more tries before a short wait."
        view.animate().translationX(activity.dpf(10f)).setDuration(60).withEndAction {
            view.animate().translationX(-activity.dpf(10f)).setDuration(60).withEndAction { view.animate().translationX(0f).setDuration(60).start() }.start()
        }.start()
        waitNote()
    }
}

object Keypad {
    fun make(activity: Activity, ink: Int, onColor: Boolean, finger: (() -> Unit)?, onDigit: (Char) -> Unit, onDelete: () -> Unit, onDone: (() -> Unit)? = null): View {
        val grid = activity.column(12)
        val keys = listOf("123", "456", "789", "f0d")
        for (line in keys) {
            val row = activity.line(12)
            for (char in line) {
                val key: View = when (char) {
                    'f' -> if (finger != null) iconKey(activity, Glyphs.FINGER, "Use fingerprint", ink) { finger() } else if (onDone != null) iconKey(activity, Glyphs.CHECK, "Done", ink) { onDone() } else activity.spacer()
                    'd' -> iconKey(activity, "M9 6h11v12H9l-6-6zM12 10l4 4M16 10l-4 4", "Delete", ink) { onDelete() }
                    else -> digitKey(activity, char, ink, onColor) { onDigit(char) }
                }
                row.put(key, activity.dp(84), activity.dp(62))
            }
            grid.put(row, WRAP_CONTENT, WRAP_CONTENT)
        }
        return grid
    }

    private fun digitKey(activity: Activity, char: Char, ink: Int, onColor: Boolean, onClick: () -> Unit): View {
        val key = activity.text(char.toString(), 24f, ink, Fonts.mono)
        key.gravity = Gravity.CENTER
        val r = activity.dpf(16f)
        key.background = if (onColor) {
            val layers = android.graphics.drawable.LayerDrawable(arrayOf(rounded(0x1F000000, r), rounded(0x29FFFFFF, r)))
            layers.setLayerInset(1, 0, 0, 0, activity.dp(2))
            activity.ripple(layers, 16f)
        } else {
            activity.secondaryBack(16f)
        }
        key.isClickable = true
        key.setOnClickListener { onClick() }
        return key
    }

    private fun iconKey(activity: Activity, icon: String, label: String, ink: Int, onClick: () -> Unit): View {
        val key = FrameLayout(activity)
        key.background = activity.ripple(null, 16f)
        key.contentDescription = label
        key.isClickable = true
        key.setOnClickListener { onClick() }
        key.addView(activity.glyph(icon, ink, 24, 2f), FrameLayout.LayoutParams(activity.dp(24), activity.dp(24), Gravity.CENTER))
        return key
    }
}

object PinSheet {
    fun set(activity: Activity, label: String, onDone: (String) -> Unit) {
        val sheet = Sheet(activity)
        var first: String? = null
        val entered = StringBuilder()
        val title = activity.text("A PIN for $label", 18f, T.ink, Fonts.sansBold)
        val detail = activity.text("4 to 8 digits, only for $label. It’s kept as a salted hash, never the digits. If you forget it, removing $label from your list removes the lock too.", 13f, T.muted)
        val shown = activity.text("", 26f, T.ink, Fonts.monoMedium)
        shown.gravity = Gravity.CENTER
        shown.letterSpacing = 0.4f
        fun paint() {
            shown.text = if (entered.isEmpty()) "····" else "●".repeat(entered.length)
        }
        lateinit var pad: View
        fun done() {
            if (entered.length < 4) {
                activity.toast("At least 4 digits")
                return
            }
            val typed = entered.toString()
            entered.setLength(0)
            if (first == null) {
                first = typed
                title.text = "Type it again"
                paint()
                return
            }
            if (first != typed) {
                first = null
                title.text = "They didn’t match. A PIN for $label"
                paint()
                return
            }
            sheet.dismiss()
            onDone(typed)
        }
        pad = Keypad.make(activity, T.ink, false, null, { digit ->
            if (entered.length < 8) entered.append(digit)
            paint()
        }, {
            if (entered.isNotEmpty()) entered.setLength(entered.length - 1)
            paint()
        }) { done() }
        sheet.body.put(title)
        sheet.body.put(detail)
        sheet.body.put(shown)
        val holder = activity.line()
        holder.gravity = Gravity.CENTER
        holder.put(pad, WRAP_CONTENT)
        sheet.body.put(holder)
        val bar = activity.line(8)
        bar.put(activity.quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, activity.dp(48))
        bar.put(activity.spacer(), 0, 1, 1f)
        bar.put(activity.primaryButton("Next", Glyphs.ARROW_RIGHT) { done() }, activity.dp(150), activity.dp(48))
        sheet.body.put(bar)
        paint()
        sheet.show()
    }
}

object Gate {
    fun open(activity: Activity, site: Site, onPass: () -> Unit) {
        if (!LockState.needed(activity, site)) {
            onPass()
            return
        }
        val sheet = Sheet(activity)
        val top = activity.line(12)
        top.put(activity.appIcon(site, 40, 12f), activity.dp(40), activity.dp(40))
        val words = activity.column(1)
        words.put(activity.text(site.label, 17f, T.ink, Fonts.sansBold))
        words.put(activity.text("locked · unlock to see or change it", 12f, T.muted))
        top.put(words, 0, WRAP_CONTENT, 1f)
        sheet.body.put(top)
        val panel = LockPanel(activity, site, T.ink, false) {
            sheet.dismiss()
            onPass()
        }
        sheet.body.put(panel.view)
        sheet.show()
        panel.prompt()
    }
}

object Locking {
    val MODES = listOf(Option("off"), Option("fingerprint", "finger"), Option("pin", "PIN"), Option("either"))
    val AFTER = listOf(Option("0", "right away"), Option("60", "1 min"), Option("300", "5 min"), Option("900", "15 min"))

    fun choose(activity: android.app.Activity, site: Site, key: String, done: () -> Unit) {
        val wantsFinger = key == "fingerprint" || key == "either"
        val wantsPin = key == "pin" || key == "either"
        if (wantsFinger && !Lock.available(activity)) {
            activity.toast(if (key == "either") "Set a screen lock on this phone first, or use a PIN only" else "Set a screen lock on this phone first")
            return
        }
        if (wantsPin && site.pinHash == null) {
            PinSheet.set(activity, site.label) { pin ->
                site.pinHash = Pin.hash(pin)
                site.lockMode = key
                done()
            }
            return
        }
        site.lockMode = key
        done()
    }

    fun detail(site: Site): String = when (site.lockMode) {
        "fingerprint" -> "your phone’s fingerprint or screen lock"
        "pin" -> "a PIN only for ${site.label}"
        "either" -> "fingerprint, or ${site.label}’s own PIN"
        else -> "opens without asking"
    }
}
