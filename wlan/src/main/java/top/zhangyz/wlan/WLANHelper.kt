package top.zhangyz.wlan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.*
import android.util.Log

class WLANHelper(private val wifiManager: WifiManager, val wlanListener: WLANListener? = null) {
    val scanList = mutableListOf<ScanResult>()
    var current: ScanResult? = null

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let {
                when (it) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        -1
                    ).let { state ->
                        Log.i(TAG, "wifi状态：$state")
                        wlanListener?.let {
                            it.onStateChange(state)
                            if (WifiManager.WIFI_STATE_DISABLING == state) { // 关闭wifi
                                it.onEnable(false)
                                it.onDisconnect()
                                scanList.clear()
                                current = null
                                it.onNotify()
                            } else if (WifiManager.WIFI_STATE_ENABLED == state) { // 打开wifi
                                it.onEnable(true)
                                wifiManager.startScan()
                            }
                        }
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        // 网络状态改变
                        Log.i(TAG, "wifi NETWORK_STATE_CHANGED_ACTION：")
                        updateConnectInfo()
                        wlanListener?.onNetworkStateChange(
                            intent.getParcelableExtra<NetworkInfo>(
                                WifiManager.EXTRA_NETWORK_INFO
                            )
                        )
                    }
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        // 扫描完成
                        Log.i(TAG, "wifi SCAN_RESULTS_AVAILABLE_ACTION：")
                        val scanResults = wifiManager.scanResults
                        scanList.clear()
                        scanResults.forEach {
                            Log.i(TAG, it.toString())
                            if (it.SSID.isNotEmpty())
                                scanList.add(it)
                        }
                        scanList.sortByDescending { it.level } // 排序
                        // 扫描列表刷新时，连接wifi不一样断开连接
                        current?.let {
                            if ("\"${it.SSID}\"" != wifiManager.connectionInfo?.ssid) {
                                wlanListener?.onDisconnect()
                                current = null
                            }
                        }
                        updateConnectInfo()
                        wlanListener?.onNotify()
                    }
                    WifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> {
                        Log.i(TAG, "wifi SUPPLICANT_STATE_CHANGED_ACTION：")
                        val supplicantState =
                            intent.getParcelableExtra<SupplicantState>(WifiManager.EXTRA_NEW_STATE)
                        val intExtra = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0)
                        Log.i(TAG, " 验证：$intExtra,$supplicantState")
                        if (WifiManager.ERROR_AUTHENTICATING == intExtra
                        ) {
                            wlanListener?.onFailed()
                            return@let
                        }
                    }
                    else -> {
                        Log.i(TAG, "wifi ELSE：$it")
                    }
                }
            }
        }
    }

    private fun updateConnectInfo() {
        val connectedInfo = wifiManager.connectionInfo?.also {
            Log.i(
                TAG,
                it.toString() + ", " + it.rssi + "," + getLevelResource(it.rssi)
            )
        }
        connectedInfo?.let { info ->
            val ssid = info.ssid.substring(1, info.ssid.length - 1)
            if (ssid.isNotEmpty()) {
                scanList.forEach {
                    if (ssid == it.SSID) {
                        scanList.remove(it)
                        if (it.SSID != current?.SSID) {
                            current?.let {
                                scanList.add(0, it)
                                scanList.sortByDescending { it.level }
                            }
                            current = it
                        }
                        wlanListener?.run {
                            onConnect(it)
                            onNotify()
                        }
                        return
                    }
                }
            }
        }
    }

    fun isOpen(): Boolean {
        return wifiManager.isWifiEnabled
    }

    fun setWifiEnabled(enable: Boolean) {
        if (enable != wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = enable
        }
    }

    fun startScan() {
        wifiManager.startScan()
    }


    fun getConnectionInfo(): WifiInfo? {
        return wifiManager.connectionInfo
    }

    fun register(context: Context) {
        context.registerReceiver(wifiReceiver, IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        })
    }

    fun unregister(context: Context) {
        context.unregisterReceiver(wifiReceiver)
    }

    fun connect(scanResult: ScanResult, password: String?) {
        connect(scanResult.SSID, password, scanResult.capabilities)
    }

    fun connect(ssid: String, password: String?, capabilities: String?) {
        WifiConfiguration().let { config ->
            config.allowedAuthAlgorithms.clear()
            config.allowedGroupCiphers.clear()
            config.allowedKeyManagement.clear()
            config.allowedPairwiseCiphers.clear()
            config.allowedProtocols.clear()
            config.SSID = "\"$ssid\""
            (capabilities ?: "").let {
                if (it.contains("WEP")) {
                    //WIFICIPHER_WEP
                    config.hiddenSSID = true
                    config.wepKeys[0] = "\"" + password + "\""
                    config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    config.wepTxKeyIndex = 0
                } else if (it.contains("WPA")) {
                    //WIFICIPHER_WPA
                    config.preSharedKey = "\"" + password + "\""
                    config.hiddenSSID = true
                    config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                    //config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                    config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                    config.status = WifiConfiguration.Status.ENABLED
                } else {
                    config.wepKeys[0] = "\"\""
                    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    config.wepTxKeyIndex = 0
                }
            }
            connect(config)
        }
    }

    fun connect(config: WifiConfiguration) {
        disconnect()
        current?.let {
            if ("\"${it.SSID}\"" != config.SSID) {
                current = null
                scanList.add(0, it)
                scanList.sortByDescending { it.level }
                wlanListener?.run {
                    onDisconnect()
                    onNotify()
                }
            }
        }
        wifiManager.enableNetwork(wifiManager.addNetwork(config), true)
    }

    fun disconnect() {
        wifiManager.disconnect()
    }

    fun remove(config: WifiConfiguration) {
        wifiManager.removeNetwork(config.networkId)
    }

    fun getOldConfiguration(ssid: String): WifiConfiguration? {
        wifiManager.configuredNetworks?.forEach {
            if (ssid == it.SSID) return it
        }
        return null
    }

    interface WLANListener {
        fun onDisconnect() // 断开WiFi

        fun onConnect(scanResult: ScanResult) // 连接了wifi，并且扫描到了

        fun onFailed() //

        fun onNetworkStateChange(networkInfo: NetworkInfo?) // 连接wifi时状态更新

        fun onNotify() // 列表更新

        fun onStateChange(state: Int) // WiFi状态更新

        fun onEnable(enable: Boolean) // wifi开启关闭
    }

    companion object {

        fun needPassword(scanResult: ScanResult): Boolean {
            return scanResult.capabilities.toUpperCase()
                .let {
                    it.contains("WPA") || it.contains("WEP")
                }
        }

        fun getLevelResource(rssi: Int): Int {
            val level = WifiManager.calculateSignalLevel(rssi, 4)
            return when (level) {
                3 -> R.drawable.signal_wifi_3_bar
                2 -> R.drawable.signal_wifi_2_bar
                1 -> R.drawable.signal_wifi_1_bar
                else -> R.drawable.signal_wifi_0_bar
            }
        }

        const val TAG = "WLANHelper"
    }
}