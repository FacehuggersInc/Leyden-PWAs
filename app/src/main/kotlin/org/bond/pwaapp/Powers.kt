package org.bond.pwaapp

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.WebView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object Proxy {
    fun supported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    } catch (error: Throwable) {
        false
    }

    fun onlyListed(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)
    } catch (error: Throwable) {
        false
    }

    fun apply(context: Context) {
        if (!supported()) return
        val defaults = Defaults.get(context)
        val main = Handler(Looper.getMainLooper())
        val executor = java.util.concurrent.Executor { command -> main.post(command) }
        try {
            if (defaults.proxy.isEmpty() || defaults.proxySites.isEmpty() || !onlyListed()) {
                ProxyController.getInstance().clearProxyOverride(executor) {}
                return
            }
            val builder = ProxyConfig.Builder().addProxyRule(defaults.proxy)
            if (defaults.proxyFallback == "direct") builder.addDirect()
            for (site in defaults.proxySites) builder.addBypassRule(site)
            builder.setReverseBypassEnabled(true)
            ProxyController.getInstance().setProxyOverride(builder.build(), executor) {}
        } catch (error: RuntimeException) {
            return
        }
    }
}

object Copies {
    fun badged(source: Bitmap, badge: String, color: Int): Bitmap {
        if (badge == "none") return source
        val made = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(made)
        val size = made.width
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = size * 0.13f
        val cx = size * 0.70f
        val cy = size * 0.70f
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius + size * 0.02f, paint)
        paint.color = if (badge == "dot") T.accent else color
        canvas.drawCircle(cx, cy, radius, paint)
        if (badge != "dot") {
            paint.color = if (isLight(color)) Color.BLACK else Color.WHITE
            paint.textSize = radius * 1.2f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(badge.take(1).uppercase(), cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
        }
        return made
    }

    fun make(context: Context, from: Site, name: String, badge: String, settings: Boolean, insertions: Boolean, rules: Boolean, lock: Boolean): Site {
        val json = from.toJson()
        json.put("id", UUID.randomUUID().toString())
        json.put("name", name)
        json.put("created", System.currentTimeMillis())
        val made = Site.fromJson(json) ?: from
        if (!settings) {
            val fresh = Site.fresh(Defaults.get(context))
            made.orientation = fresh.orientation
            made.barColor = fresh.barColor
            made.splash = fresh.splash
            made.dark = fresh.dark
            made.textSize = fresh.textSize
            made.zoom = fresh.zoom
            made.fullScreen = fresh.fullScreen
            made.links = null
            made.navStyle = null
            made.keepInside = mutableListOf()
            made.popups = fresh.popups
            made.desktop = false
            made.power = Power()
        }
        if (!insertions) {
            made.insertions = mutableListOf()
            made.hidden = mutableListOf()
        }
        if (!rules) {
            made.rules = mutableListOf()
            made.unblocked = mutableSetOf()
        }
        if (!lock) {
            made.lockMode = "off"
            made.pinHash = null
        }
        made.ownStorage = Storage.separate
        made.granted = mutableSetOf()
        made.power.badge = badge
        made.power.copyOf = from.id
        made.power.check = null
        SiteStore.icon(context, from.id)?.let { SiteStore.saveIcon(context, made.id, badged(it, badge, from.color)) }
        SiteStore.save(context, made)
        return made
    }
}

object Files {
    private val main = Handler(Looper.getMainLooper())

    fun pickFolder(activity: Activity, request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, request)
    }

    fun keep(context: Context, uri: Uri): String {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifEmpty { "chosen folder" }
    }

    fun release(context: Context, address: String) {
        if (address.isEmpty()) return
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(address), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (error: SecurityException) {
            return
        }
    }

    fun create(context: Context, tree: String, name: String, mime: String): OutputStream? {
        val root = Uri.parse(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
        val document = DocumentsContract.createDocument(context.contentResolver, parent, mime, name) ?: return null
        return context.contentResolver.openOutputStream(document)
    }

    fun download(context: Context, tree: String, url: String, name: String, mime: String?, agent: String, cookie: String?, done: (String?) -> Unit) {
        val app = context.applicationContext
        Thread {
            val problem = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", agent)
                if (cookie != null) connection.setRequestProperty("Cookie", cookie)
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                val out = create(app, tree, name, mime ?: connection.contentType ?: "application/octet-stream") ?: throw java.io.IOException("couldn’t make the file")
                connection.inputStream.use { input -> out.use { input.copyTo(it) } }
                connection.disconnect()
                null
            } catch (error: Exception) {
                error.message ?: "download failed"
            }
            main.post { done(problem) }
        }.start()
    }

    fun savePicture(context: Context, tree: String, name: String, bitmap: Bitmap): String {
        if (tree.isNotEmpty()) {
            val out = create(context, tree, name, "image/png") ?: throw java.io.IOException("couldn’t make the file")
            out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return "your chosen folder"
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("couldn’t save")
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return "Pictures/Screenshots"
        }
        @Suppress("DEPRECATION")
        val folder = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")
        folder.mkdirs()
        java.io.File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "Pictures/Screenshots"
    }
}

object Shot {
    private const val LIMIT = 16000
    private const val HIDE_FIXED = "(function(){var out=0;document.querySelectorAll('body *').forEach(function(e){var p=getComputedStyle(e).position;if(p==='fixed'||p==='sticky'){e.setAttribute('data-pwa-shot',e.style.visibility||'-');e.style.visibility='hidden';out++;}});return out;})()"
    private const val SHOW_FIXED = "(function(){document.querySelectorAll('[data-pwa-shot]').forEach(function(e){var v=e.getAttribute('data-pwa-shot');e.style.visibility=v==='-'?'':v;e.removeAttribute('data-pwa-shot');});})()"

    fun take(activity: WebAppActivity, done: (Bitmap?) -> Unit) {
        val web = activity.webView
        web.evaluateJavascript("JSON.stringify([document.documentElement.scrollHeight,innerHeight,scrollY,devicePixelRatio])") { value ->
            val numbers = try {
                org.json.JSONArray(org.json.JSONArray("[$value]").getString(0))
            } catch (error: Exception) {
                done(null)
                return@evaluateJavascript
            }
            val total = numbers.optDouble(0)
            val view = numbers.optDouble(1)
            val start = numbers.optDouble(2)
            if (total <= 0 || view <= 0 || web.width == 0 || web.height == 0) {
                done(null)
                return@evaluateJavascript
            }
            val scale = web.height / view
            val fullHeight = (total * scale).toInt().coerceAtMost(LIMIT)
            val out = Bitmap.createBitmap(web.width, fullHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            var drawn = 0
            fun slice(index: Int) {
                val y = index * view
                web.evaluateJavascript("window.scrollTo(0,$y)", null)
                web.postDelayed({
                    val piece = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
                    web.draw(Canvas(piece))
                    val top = (y * scale).toInt()
                    val keep = (fullHeight - top).coerceAtMost(web.height)
                    if (keep > 0) canvas.drawBitmap(Bitmap.createBitmap(piece, 0, 0, web.width, keep), 0f, top.toFloat(), null)
                    piece.recycle()
                    drawn = top + keep
                    if (index == 0) web.evaluateJavascript(HIDE_FIXED, null)
                    if (drawn < fullHeight) {
                        slice(index + 1)
                    } else {
                        web.evaluateJavascript(SHOW_FIXED, null)
                        web.evaluateJavascript("window.scrollTo(0,$start)", null)
                        done(out)
                    }
                }, 260)
            }
            slice(0)
        }
    }
}

object Media {
    const val SHIM = "(function(){if(window.__pwaMedia)return;var handlers={};window.__pwaMedia=handlers;try{var ms=navigator.mediaSession;if(ms&&ms.setActionHandler){var orig=ms.setActionHandler.bind(ms);" +
        "ms.setActionHandler=function(a,h){handlers[a]=h;try{orig(a,h);}catch(e){}};}}catch(e){}" +
        "function report(){try{var m=[].slice.call(document.querySelectorAll('video,audio'));var live=m.filter(function(x){return !x.paused&&!x.ended})[0];var any=live||m[0];" +
        "var md=navigator.mediaSession&&navigator.mediaSession.metadata;PwaApp.media(window.__pwaMediaKey||'',live?'playing':'paused',(md&&md.title)||document.title,(md&&md.artist)||'',any&&any.tagName==='VIDEO'?'video':'audio');}catch(e){}}" +
        "['play','pause','ended'].forEach(function(k){document.addEventListener(k,function(){setTimeout(report,50);},true);});})();"

    const val PIP = """(function(){if(window.__pwaPip||!window.HTMLVideoElement)return;var P={v:null,saved:null,html:null};window.__pwaPip=P;
function fill(v){if(P.v&&P.v!==v)P.leave(true);P.v=v;P.saved=v.getAttribute('style');P.html=document.documentElement.getAttribute('style');
v.setAttribute('style',(P.saved||'')+';position:fixed!important;left:0!important;top:0!important;right:auto!important;bottom:auto!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;margin:0!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important;transform:none!important;');
document.documentElement.style.setProperty('overflow','hidden','important');}
P.leave=function(quiet){var v=P.v;if(!v)return;P.v=null;if(P.saved==null)v.removeAttribute('style');else v.setAttribute('style',P.saved);if(P.html==null)document.documentElement.removeAttribute('style');else document.documentElement.setAttribute('style',P.html);
if(!quiet){try{v.dispatchEvent(new Event('leavepictureinpicture'));}catch(e){}}};
P.fill=function(){var v=[].filter.call(document.querySelectorAll('video'),function(x){return !x.paused&&!x.ended})[0];if(v)fill(v);};
try{Object.defineProperty(Document.prototype,'pictureInPictureEnabled',{configurable:true,get:function(){return true;}});
Object.defineProperty(Document.prototype,'pictureInPictureElement',{configurable:true,get:function(){return P.v;}});}catch(e){}
HTMLVideoElement.prototype.requestPictureInPicture=function(){var v=this;return new Promise(function(ok,no){var w=v.videoWidth||16,h=v.videoHeight||9,yes=false;
try{yes=window.PwaApp&&PwaApp.pip(window.__pwaMediaKey||'',w,h);}catch(e){}if(!yes){no(new DOMException('Picture-in-picture isn’t available right now','NotAllowedError'));return;}
fill(v);var win={width:w,height:h,onresize:null,addEventListener:function(){},removeEventListener:function(){}};
setTimeout(function(){try{var e=new Event('enterpictureinpicture');e.pictureInPictureWindow=win;v.dispatchEvent(e);}catch(x){}},0);ok(win);});};
Document.prototype.exitPictureInPicture=function(){P.leave();return Promise.resolve();};})();"""

    const val FILL = "window.__pwaPip&&window.__pwaPip.fill()"

    fun action(name: String): String = "(function(){var h=window.__pwaMedia&&window.__pwaMedia['$name'];if(h){try{h({action:'$name'});return;}catch(e){}}" +
        "var m=[].slice.call(document.querySelectorAll('video,audio'));var live=m.filter(function(x){return !x.paused})[0]||m[0];if(!live)return;" +
        "if('$name'==='play')live.play();else if('$name'==='pause')live.pause();else if('$name'==='nexttrack')live.currentTime=Math.min(live.duration||0,live.currentTime+30);else if('$name'==='previoustrack')live.currentTime=Math.max(0,live.currentTime-15);})()"
}

class AudioService : Service() {
    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "play", "pause", "nexttrack", "previoustrack" -> controller?.invoke(intent.action.orEmpty())
        }
        val title = intent?.getStringExtra(TITLE) ?: current.title
        val artist = intent?.getStringExtra(ARTIST) ?: current.artist
        val app = intent?.getStringExtra(APP) ?: current.app
        val playing = intent?.getBooleanExtra(PLAYING, current.playing) ?: current.playing
        current = State(title, artist, app, playing)
        show()
        return START_NOT_STICKY
    }

    private fun pending(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, AudioService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private fun show() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Playing in an app", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while audio or video keeps playing with the screen off"
        })
        val media = session ?: MediaSession(this, "pwaapp").also { made ->
            made.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { controller?.invoke("play") }
                override fun onPause() { controller?.invoke("pause") }
                override fun onSkipToNext() { controller?.invoke("nexttrack") }
                override fun onSkipToPrevious() { controller?.invoke("previoustrack") }
            })
            made.isActive = true
            session = made
        }
        val state = current
        media.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, state.title).putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist.ifEmpty { state.app }).build())
        media.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
        val toggle = if (state.playing) Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Pause", pending("pause")).build()
        else Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_play), "Play", pending("play")).build()
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.title)
            .setContentText("${state.app} · playing in Leyden")
            .setOngoing(state.playing)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Back", pending("previoustrack")).build())
            .addAction(toggle)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), "Forward", pending("nexttrack")).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Stop", pending(STOP)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, notification)
    }

    override fun onDestroy() {
        session?.release()
        session = null
        controller?.invoke("pause")
        super.onDestroy()
    }

    class State(val title: String = "", val artist: String = "", val app: String = "", val playing: Boolean = false)

    companion object {
        const val CHANNEL = "playing"
        const val ID = 6101
        const val STOP = "stop"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val APP = "app"
        const val PLAYING = "playing"
        var controller: ((String) -> Unit)? = null
        var current = State()

        fun update(context: Context, app: String, title: String, artist: String, playing: Boolean) {
            val intent = Intent(context, AudioService::class.java).putExtra(TITLE, title).putExtra(ARTIST, artist).putExtra(APP, app).putExtra(PLAYING, playing)
            try {
                context.startForegroundService(intent)
            } catch (error: RuntimeException) {
                return
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioService::class.java))
        }
    }
}

object Bubbles {
    private const val CHANNEL = "bubbles"
    const val PERMISSION = 7604

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 30

    @android.annotation.TargetApi(30)
    private fun allowed(manager: NotificationManager, shortcut: String): String {
        if (!manager.areNotificationsEnabled()) return "notifications"
        val channel = manager.getNotificationChannel(CHANNEL)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return "notifications"
        if (Build.VERSION.SDK_INT >= 31) {
            when (manager.bubblePreference) {
                NotificationManager.BUBBLE_PREFERENCE_ALL -> return ""
                NotificationManager.BUBBLE_PREFERENCE_NONE -> return "off"
            }
            val conversation = manager.getNotificationChannel(CHANNEL, shortcut)
            return if (conversation?.canBubble() == true) "" else "selected"
        }
        @Suppress("DEPRECATION")
        return if (manager.areBubblesAllowed()) "" else "off"
    }

    @android.annotation.TargetApi(30)
    fun show(activity: Activity, site: Site) {
        if (!supported()) {
            activity.toast("Bubbles need Android 11 or newer")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), PERMISSION)
            return
        }
        val manager = activity.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Apps as bubbles", NotificationManager.IMPORTANCE_HIGH).apply {
            setAllowBubbles(true)
            description = "Lets an app float over others when you ask for it"
        })
        val bitmap = SiteStore.icon(activity, site.id) ?: LauncherIcons.make(null, false, site.color, site.label)
        val icon = Icon.createWithAdaptiveBitmap(bitmap)
        val person = Person.Builder().setName(site.label).setIcon(icon).setImportant(true).setKey(site.id).build()
        val shortcutId = "bubble-${site.id}"
        val shortcut = ShortcutInfo.Builder(activity, shortcutId)
            .setShortLabel(site.label).setLongLived(true).setIcon(icon).setPerson(person)
            .setCategories(setOf("android.shortcut.conversation"))
            .setIntent(Intent(activity, BubbleActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("pwaapp://site/${site.id}")))
            .build()
        activity.getSystemService(ShortcutManager::class.java)?.pushDynamicShortcut(shortcut)
        val state = allowed(manager, shortcutId)
        if (state == "notifications" || state == "off") {
            explain(activity, state)
            return
        }
        val target = Intent(activity, BubbleActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("pwaapp://site/${site.id}"))
        val content = PendingIntent.getActivity(activity, site.id.hashCode(), target, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val bubble = Notification.BubbleMetadata.Builder(content, icon)
            .setDesiredHeight(640).setAutoExpandBubble(true).setSuppressNotification(state.isEmpty()).build()
        val notification = Notification.Builder(activity, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(site.label)
            .setContentText("Tap the bubble button to float ${site.label}")
            .setContentIntent(content)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .addPerson(person)
            .setStyle(Notification.MessagingStyle(person).setConversationTitle(site.label).addMessage(Notification.MessagingStyle.Message("Open ${site.label} as a bubble", System.currentTimeMillis(), person)))
            .setBubbleMetadata(bubble)
            .build()
        manager.notify(site.id.hashCode(), notification)
        if (state == "selected") activity.toast("Pull down the notification and tap its bubble button once; after that ${site.label} bubbles straight away")
    }

    private fun explain(activity: Activity, state: String) {
        val sheet = Sheet(activity)
        sheet.body.put(activity.text(if (state == "notifications") "Notifications are off for Leyden" else "Bubbles are off for Leyden", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(activity.text(if (state == "notifications") "Android shows bubbles through notifications, so they need to be on. Leyden only notifies for bubbles and background audio." else "Android lets you choose which apps can float as bubbles. Allow Leyden, then try again.", 13f, T.muted))
        sheet.body.put(activity.primaryButton("Open Android’s settings", Glyphs.ARROW_RIGHT) {
            sheet.dismiss()
            val intent = if (state == "notifications") Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) else Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
            try {
                activity.startActivity(intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.packageName))
            } catch (error: Exception) {
                activity.toast("Open Android settings › apps › Leyden › notifications")
            }
        }, android.view.ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(50))
        sheet.show()
    }
}

object Share {
    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val sites = SiteStore.all(context).filter { (it.power.shareText || it.power.shareFiles) && !it.locks }.take(4)
        try {
            manager.removeAllDynamicShortcuts()
            val made = sites.map { site ->
                val bitmap = SiteStore.icon(context, site.id) ?: LauncherIcons.make(null, false, site.color, site.label)
                val builder = ShortcutInfo.Builder(context, "share-${site.id}")
                if (Build.VERSION.SDK_INT >= 29) builder.setLongLived(true)
                builder
                    .setShortLabel(site.label)
                    .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
                    .setCategories(setOf("org.bond.pwaapp.SHARE"))
                    .setIntent(Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND).putExtra(ShareActivity.SITE, site.id))
                    .build()
            }
            manager.addDynamicShortcuts(made)
        } catch (error: RuntimeException) {
            return
        }
    }

    fun fill(template: String, title: String, text: String, url: String): String {
        fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
        return template.replace("{title}", enc(title)).replace("{text}", enc(text)).replace("{url}", enc(url))
    }

    fun destination(site: Site, title: String, text: String, url: String): String? {
        val template = site.power.shareTemplate.ifEmpty { return null }
        val filled = fill(template, title, text, url)
        return SiteLookup.resolveOrNull(site.url, filled)
    }
}

fun WebView.safely(script: String) {
    try {
        evaluateJavascript(script, null)
    } catch (error: RuntimeException) {
        return
    }
}

object Passwords {
    fun ready(context: Context): Boolean {
        val manager = context.getSystemService(android.view.autofill.AutofillManager::class.java) ?: return false
        return manager.isAutofillSupported && manager.isEnabled
    }

    fun choose(activity: Activity) {
        try {
            activity.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:${activity.packageName}")))
        } catch (error: Exception) {
            try {
                activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (inner: Exception) {
                activity.toast("Open Android settings › passwords and autofill")
            }
        }
    }

    fun fill(activity: Activity, view: android.view.View) {
        if (!ready(activity)) {
            explain(activity)
            return
        }
        view.requestFocus()
        activity.getSystemService(android.view.autofill.AutofillManager::class.java)?.requestAutofill(view)
    }

    fun explain(activity: Activity) {
        val sheet = Sheet(activity)
        sheet.body.put(activity.text("No password manager for apps", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(activity.text("Sites in Leyden get passwords from Android’s autofill, the same way other apps do. Right now no autofill service is on, so there’s nothing to offer. Pick your password manager, like Google, Bitwarden, 1Password or Proton Pass, as the autofill service.", 13f, T.muted))
        sheet.body.put(activity.text("Passwords you saved in Chrome are in Google Password Manager; choose Google there to use them here.", 12f, T.faint))
        sheet.body.put(activity.primaryButton("Choose a password manager", Glyphs.ARROW_RIGHT) {
            sheet.dismiss()
            choose(activity)
        }, android.view.ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(50))
        sheet.show()
    }
}

object Snapshots {
    private fun file(context: Context, id: String): java.io.File = java.io.File(java.io.File(context.cacheDir, "snaps").apply { mkdirs() }, "$id.jpg")

    fun allowed(site: Site): Boolean = site.keeps(Kinds.TITLE) && !site.locks && !site.privateMode

    fun take(context: Context, site: Site, view: WebView, locked: Boolean) {
        if (!allowed(site) || locked || view.width <= 0 || view.height <= 0) {
            drop(context, site.id)
            return
        }
        val scale = 0.3f
        val bitmap = try {
            Bitmap.createBitmap((view.width * scale).toInt().coerceAtLeast(1), (view.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
        } catch (error: OutOfMemoryError) {
            return
        }
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
        try {
            view.draw(canvas)
        } catch (error: RuntimeException) {
            return
        }
        val target = file(context, site.id)
        Thread {
            try {
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 72, it) }
            } catch (error: Exception) {
                target.delete()
            }
        }.start()
    }

    fun get(context: Context, site: Site): Bitmap? {
        if (!allowed(site)) return null
        val found = file(context, site.id)
        return if (found.isFile) android.graphics.BitmapFactory.decodeFile(found.path) else null
    }

    fun size(context: Context, id: String): Long = file(context, id).takeIf { it.isFile }?.length() ?: 0L

    fun drop(context: Context, id: String) {
        file(context, id).delete()
    }
}
