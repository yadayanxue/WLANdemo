package top.zhangyz.wlan.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import top.zhangyz.wlan.WLANActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun wlanSetting(view: View) {
        startActivity(Intent(this, WLANActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        check()
    }

    private fun check() {
        if (!NEEDED_PERMISSIONS.checkPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                NEEDED_PERMISSIONS.toTypedArray(),
                ACTION_REQUEST_PERMISSIONS
            )
            return
        }
    }


    private fun List<String>?.checkPermissions(): Boolean {
        if (this == null || isEmpty()) {
            return true
        }
        var allGranted = true
        for (neededPermission in this) {
            allGranted = allGranted and (ContextCompat.checkSelfPermission(
                this@MainActivity,
                neededPermission
            ) == PackageManager.PERMISSION_GRANTED)
        }
        return allGranted
    }

    companion object {
        private const val ACTION_REQUEST_PERMISSIONS = 2019

        private val NEEDED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
        )
    }
}
