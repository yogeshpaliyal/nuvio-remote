package dev.skiff

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo

/**
 * The play build's updates: Google Play's in-app updates (Play allows no other way). A flexible update: Play
 * downloads it while Skiff stays open, then Skiff restarts into it. Play says only that there is one (its
 * versionCode), not its name or what's new.
 */
internal object StoreUpdates {
    private const val PAGE = "https://play.google.com/store/apps/details?id=dev.skiff"
    private var info: AppUpdateInfo? = null

    suspend fun check(context: Context, force: Boolean): Updates.Release? {
        val manager = AppUpdateManagerFactory.create(context)
        val i = try {
            manager.requestAppUpdateInfo()
        } catch (e: Exception) {
            if (force) throw IllegalStateException("Google Play didn't answer: is Skiff installed from Play?", e)
            return null
        }
        // Downloaded earlier and not installed yet: finish it now.
        if (i.installStatus() == InstallStatus.DOWNLOADED) manager.completeUpdate()
        if (i.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE || !i.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return null
        info = i
        return Updates.Release(version = "", notes = "", apkUrl = null, pageUrl = PAGE, id = "code:${i.availableVersionCode()}")
    }

    suspend fun open(context: Context, r: Updates.Release) {
        val manager = AppUpdateManagerFactory.create(context)
        val i = info
        val activity = context.activity()
        if (i == null || activity == null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        // Once downloaded, install it (Skiff restarts into the new version).
        lateinit var listener: InstallStateUpdatedListener
        listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                manager.unregisterListener(listener)
                manager.completeUpdate()
            }
        }
        manager.registerListener(listener)
        manager.startUpdateFlow(i, activity, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
    }

    private tailrec fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
}
