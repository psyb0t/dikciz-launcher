package org.fossify.home.dikciz

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import org.fossify.home.R

internal class DikcizHealthPermissionRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_health_permission_rationale_title)
            .setMessage(R.string.dikciz_health_permission_rationale_message)
            .setPositiveButton(R.string.dikciz_dialog_close, null)
            .setOnDismissListener { finish() }
            .show()
    }
}
