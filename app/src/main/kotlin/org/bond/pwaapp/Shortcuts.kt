package org.bond.pwaapp

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri

object Shortcuts {
    fun intent(context: Context, site: Site): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("pwaapp://site/${site.id}"), context, WebAppActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)

    fun open(context: Context, site: Site, address: String? = null) {
        val intent = intent(context, site).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (address != null) intent.putExtra(WebAppActivity.ADDRESS, address)
        context.startActivity(intent)
    }

    const val RUN = "run"
    const val SELECT = "select"
    const val TEST = "test"

    fun test(context: Context, site: Site, id: String) {
        context.startActivity(intent(context, site).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(TEST, id))
    }

    fun run(context: Context, site: Site, id: String) {
        context.startActivity(intent(context, site).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(RUN, id))
    }

    fun select(context: Context, site: Site, id: String) {
        context.startActivity(intent(context, site).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(SELECT, id))
    }

    fun canPin(context: Context): Boolean = context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    fun pinned(context: Context, id: String): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.pinnedShortcuts?.any { it.id == id && it.isEnabled } == true

    fun pin(context: Context, site: Site): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        return try {
            manager.requestPinShortcut(info(context, site), null)
        } catch (error: IllegalStateException) {
            false
        }
    }

    fun refresh(context: Context, site: Site) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            if (manager.pinnedShortcuts.any { it.id == site.id }) {
                manager.enableShortcuts(listOf(site.id))
                manager.updateShortcuts(listOf(info(context, site)))
            }
        } catch (error: RuntimeException) {
            return
        }
    }

    fun retire(context: Context, id: String) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            manager.disableShortcuts(listOf(id), "This app was removed")
        } catch (error: RuntimeException) {
            return
        }
    }

    private fun info(context: Context, site: Site): ShortcutInfo {
        val bitmap = SiteStore.icon(context, site.id) ?: LauncherIcons.make(null, false, site.color, site.label)
        return ShortcutInfo.Builder(context, site.id)
            .setShortLabel(site.label)
            .setLongLabel(site.label)
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(intent(context, site))
            .build()
    }
}
