package com.deepinnovations.call_history

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "callhistory/native"
    private val reqRole = 1001
    private val reqPhone = 1002
    private val reqContacts = 1003
    private var pending: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "status" -> result.success(
                        mapOf(
                            "role" to isRoleHeld(),
                            "phone" to hasPerm(Manifest.permission.READ_PHONE_STATE),
                            "contacts" to hasPerm(Manifest.permission.READ_CONTACTS),
                            "overlay" to Settings.canDrawOverlays(this)
                        )
                    )
                    "requestRole" -> requestRole(result)
                    "requestPhone" -> askPerm(Manifest.permission.READ_PHONE_STATE, reqPhone, result)
                    "requestContacts" -> askPerm(Manifest.permission.READ_CONTACTS, reqContacts, result)
                    "requestOverlay" -> {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                        result.success(true)
                    }
                    "openRoleSettings" -> {
                        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun hasPerm(p: String): Boolean =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun askPerm(p: String, code: Int, result: MethodChannel.Result) {
        if (hasPerm(p)) {
            result.success(true)
        } else {
            pending = result
            requestPermissions(arrayOf(p), code)
        }
    }

    private fun isRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestRole(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < 29) {
            result.success(false)
            return
        }
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            pending = result
            startActivityForResult(
                rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                reqRole
            )
        } else {
            result.success(rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqRole) {
            pending?.success(isRoleHeld())
            pending = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqPhone) {
            pending?.success(hasPerm(Manifest.permission.READ_PHONE_STATE))
            pending = null
        } else if (requestCode == reqContacts) {
            pending?.success(hasPerm(Manifest.permission.READ_CONTACTS))
            pending = null
        }
    }
}
