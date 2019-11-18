package top.zhangyz.wlan

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_wlan.*

class WLANActivity : AppCompatActivity() {
    private var wlanHelper: WLANHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wlan)
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.let {
            wlanHelper = WLANHelper(it, object : WLANHelper.WLANListener {
                override fun connectedInfo(scanResult: ScanResult) {
                    connectedLayout.visibility = View.VISIBLE
                    connectLevel.setImageResource(WLANHelper.getLevelResource(scanResult.level))
                    connectLabel.text = scanResult.SSID
                    connectLock.visibility =
                        if (WLANHelper.needPassword(scanResult)) View.VISIBLE else View.GONE
                }

                override fun onStateChange(state: Int) {
                }

                override fun onNotify() {
                }

                override fun onDisconnect() {
                    connectedLayout.visibility = View.GONE
                }

                override fun onConnect() {
                }

                override fun onFailed() {
                }
            })
        }

        wifiSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            wlanHelper?.setWifiEnabled(isChecked)
        }
    }

    override fun onStart() {
        super.onStart()
        wlanHelper?.onStart(this)
    }

    override fun onStop() {
        super.onStop()
        wlanHelper?.onStop(this)
    }
}
