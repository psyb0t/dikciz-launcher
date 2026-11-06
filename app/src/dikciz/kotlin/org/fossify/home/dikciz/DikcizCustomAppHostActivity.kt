package org.fossify.home.dikciz

import android.content.Context
import android.content.Intent
import android.net.Uri

/** One Android document task per app bundle, independent of the launcher and manager. */
internal class DikcizCustomAppHostActivity : DikcizCustomAppActivity() {
    companion object {
        fun intent(context: Context, appId: String): Intent =
            Intent(context, DikcizCustomAppHostActivity::class.java)
                .setData(Uri.Builder().scheme("dikciz-app").authority("bundle").appendPath(appId).build())
                .putExtra("appId", appId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
