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
    }

    /** A usage event: `name` and `params` in Analytics' form (lowercase, underscores; short values). */
    fun event(context: Context, name: String, vararg params: Pair<String, Any>) {
        if (!available(context) || !enabled(context)) return
        val b = Bundle()
        for ((k, v) in params) when (v) {
            is Boolean -> b.putLong(k, if (v) 1 else 0)
            is Number -> b.putLong(k, v.toLong())
            else -> b.putString(k, v.toString().take(100))
        }
        FirebaseAnalytics.getInstance(context).logEvent(name, b)
    }

    /** A screen of the app (it's one activity, so Analytics can't tell them apart by itself). */
    fun screen(context: Context, name: String) =
        event(context, FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to name)
}
