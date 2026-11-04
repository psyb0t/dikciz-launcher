package org.fossify.home.dikciz

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

internal object DikcizForegroundActivity : Application.ActivityLifecycleCallbacks {
    private var attached=false
    private var resumed=WeakReference<Activity>(null)
    @Synchronized fun attach(application: Application) {
        if (attached) return
        application.registerActivityLifecycleCallbacks(this)
        attached=true
    }
    fun current(context: Context): Activity? {
        if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked==true) return null
        val activity=resumed.get()?.takeIf { !it.isDestroyed && !it.isFinishing }
        return activity ?: (context as? Activity)?.takeIf { it.hasWindowFocus() && !it.isDestroyed && !it.isFinishing }
    }
    override fun onActivityResumed(activity: Activity) { resumed=WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) { if (resumed.get()===activity) resumed.clear() }
    override fun onActivityCreated(activity: Activity,state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity,state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) { if (resumed.get()===activity) resumed.clear() }
}
