package dev.skiff

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash reports (Crashlytics) and usage (Analytics), through Firebase: on unless turned off in Settings. Nothing about
 * what's watched: only which way things went (USB debugging or pairing), a link's site, and whether it worked.
 * Without the build's google-services.json there's no Firebase app, and all of this does nothing.
 */
object Telemetry {
    private const val PREFS = "settings"
    private const val KEY = "telemetry"

    private fun available(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    /** At launch: collection as Settings says. */
    fun init(context: Context) = apply(context, enabled(context))

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        apply(context, on)
    }

    private fun apply(context: Context, on: Boolean) {
        if (!available(context)) return
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = on
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(on)
        // Which build: github or play (to compare the two).
        FirebaseAnalytics.getInstance(context).setUserProperty("store", BuildConfig.STORE)
        FirebaseCrashlytics.getInstance().setCustomKey("store", BuildConfig.STORE)
    }

    /** A usage event: `name` and `params` in Analytics' form (lowercase, underscores; short values). */
    fun event(context: Context, name: String, vararg params: Pair<String, Any>) {
        if (!available(context) || !enabled(context) || Demo.on) return
        val b = Bundle()
        for ((k, v) in params) when (v) {
            is Boolean -> b.putLong(k, if (v) 1 else 0)
            is Number -> b.putLong(k, v.toLong())
            else -> b.putString(k, v.toString().take(100))
        }
        FirebaseAnalytics.getInstance(context).logEvent(name, b)
    }

    /**
     * Something that failed and was handled: an `app_error` event (where, and the exception's class), and a
     * Crashlytics non-fatal with its stack trace. Never the message: it can hold a link, a TV's address or a command.
     */
    fun error(context: Context, where: String, e: Throwable) {
        if (!available(context) || !enabled(context) || Demo.on) return
        event(context, "app_error", "where" to where, "error" to e.javaClass.simpleName)
        FirebaseCrashlytics.getInstance().recordException(
            RuntimeException("$where: ${e.javaClass.name}").apply { stackTrace = e.stackTrace },
        )
    }

    /** A debug build (the test crash is only there). */
    fun debuggable(context: Context) = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Crashes the app on purpose, to check the whole Crashlytics flow: the report is sent when the app opens again.
     * Debug builds only: Settings → Privacy → Test crash, or `adb shell am start -n dev.skiff/.MainActivity --ez
     * test_crash true`.
     */
    fun testCrash(context: Context) {
        if (!debuggable(context)) return
        if (available(context)) FirebaseCrashlytics.getInstance().log("Test crash, asked for in Skiff")
        throw RuntimeException("Test crash from Skiff (debug build)")
    }

    /** A screen of the app (it's one activity, so Analytics can't tell them apart by itself). */
    fun screen(context: Context, name: String) =
        event(context, FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to name)
}
