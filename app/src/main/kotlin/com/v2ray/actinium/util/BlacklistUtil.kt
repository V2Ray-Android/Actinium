package com.v2ray.actinium.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.*

object AppManagerUtil {
    fun loadNetworkAppList(ctx: Context): List<AppInfo> {
        val packageManager = ctx.packageManager
        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val apps = ArrayList<AppInfo>()

        for (pkg in packages) {
            if (!pkg.hasInternetPermission) continue

            val appName = pkg.applicationInfo.loadLabel(packageManager).toString()
            val appIcon = pkg.applicationInfo.loadIcon(packageManager)

            val appInfo = AppInfo(appName, pkg.packageName, appIcon)
            apps.add(appInfo)
        }

        return apps
    }

    val PackageInfo.hasInternetPermission: Boolean get() {
        val permissions = requestedPermissions
        return permissions?.any { it == Manifest.permission.INTERNET } ?: false
    }
}

data class AppInfo(val appName: String, val packageName: String, val appIcon: Drawable)