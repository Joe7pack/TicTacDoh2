package com.guzzardo.tictacdoh2

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.guzzardo.tictacdoh2.PreferencesUtil.firstTimeAskingPermission
import com.guzzardo.tictacdoh2.PreferencesUtil.isFirstTimeAskingPermission

object PermissionUtil {
    /*
     * Check if version is marshmallow and above.
     * Used in deciding to ask runtime permission
     * */
    fun shouldAskPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
    }

    private fun shouldAskPermission(context: android.content.Context, permission: String): Boolean {
        if (shouldAskPermission()) {
            val permissionResult = ActivityCompat.checkSelfPermission(context, permission)
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                return true
            }
        }
        return false
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun checkPermission(context: Context, permission: String, listener: PermissionAskListener) {
        // If permission is not granted
        if (shouldAskPermission(context, permission)) {
            // If permission denied previously
            if ((context as Activity).shouldShowRequestPermissionRationale(permission)) {
                listener.onPermissionPreviouslyDenied()
            } else {
                // Permission denied or first time requested
                if (isFirstTimeAskingPermission(context, permission)) {
                    firstTimeAskingPermission(context, permission, false)
                    listener.onPermissionAsk()
                } else {
                    // Handle the feature without permission or ask user to manually allow permission
                    listener.onPermissionDisabled()
                }
            }
        } else {
            listener.onPermissionGranted()
        }
    }

    /*
     * Callback on various cases on checking permission
     *
     * 1.  Below M, runtime permission not needed. In that case onPermissionGranted() would be called.
     *     If permission is already granted, onPermissionGranted() would be called.
     *
     * 2.  Above M, if the permission is being asked first time onPermissionAsk() would be called.
     *
     * 3.  Above M, if the permission is previously asked but not granted, onPermissionPreviouslyDenied()
     *     would be called.
     *
     * 4.  Above M, if the permission is disabled by device policy or the user checked "Never ask again"
     *     check box on previous request permission, onPermissionDisabled() would be called.
     * */
    interface PermissionAskListener {
        // Callback to ask permission
        fun onPermissionAsk()

        // Callback on permission denied
        fun onPermissionPreviouslyDenied()

        // Callback on permission "Never show again" checked and denied
        fun onPermissionDisabled()

        // Callback on permission granted
        fun onPermissionGranted()
    }
}