package top.zhangyz.wlan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.*
import android.util.Log

class WLANHelper(private val wifiManager: WifiManager, val wlanListener: WLANListener? = null) {
    val scanList = mutableListOf<ScanResult>()
    private var current: ScanResult? = null

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
                            if (WifiManager.WIFI_STATE_DISABLED == state) {
                                it.onEnable(false)
                                it.onDisconnect()
                                scanList.clear()
                                current = null
                                it.onNotify()
                            } else if (WifiManager.WIFI_STATE_ENABLED == state) {
                                it.onEnable(true)
                                wifiManager.startScan()
                            }
                        }

                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        Log.i(TAG, "wifi NETWORK_STATE_CHANGED_ACTION：")
                        updateConnectInfo()
                    }
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        Log.i(TAG, "wifi SCAN_RESULTS_AVAILABLE_ACTION：")
                        val scanResults = wifiManager.scanResults
                        scanResults.sortByDescending { it.level }
                        scanList.clear()
                        scanList.addAll(scanResults)
                        current = null
                        updateConnectInfo()
                        wlanListener?.onNotify()
                    }
                    WifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> {
                        Log.i(TAG, "wifi SUPPLICANT_STATE_CHANGED_ACTION：")

                    }
                    WifiManager.NETWORK_IDS_CHANGED_ACTION -> {
                        Log.i(TAG, "wifi NETWORK_IDS_CHANGED_ACTION：")

                    }
                    else -> {
                        Log.i(TAG, "wifi ELSE：$it")
                    }
                }
            }
        }
    }

    private fun updateConnectInfo() {
        kotlin.run {
            val connectedInfo = wifiManager.connectionInfo?.also {
                Log.i(
                    TAG,
                    it.toString() + ", " + it.rssi + "," + getLevelResource(it.rssi)
                )
            }
            connectedInfo?.let { info ->
                if (info.supplicantState == SupplicantState.COMPLETED) {
                    val ssid = info.ssid.substring(1, info.ssid.length - 1)
                    if (ssid.isNotEmpty())
                        scanList.forEach {
                            //                            Log.i(
//                                TAG,
//                                it.toString() + ", " + it.level + "," + getLevelResource(
//                                    it.level
//                                )
//                            )
                            if (ssid == it.SSID) {
                                scanList.remove(it)
                                current?.let {
                                    scanList.add(0, it)
                                }
                                current = it
                                wlanListener?.run {
                                    connectedInfo(it)
                                    onNotify()
                                }
                                return@run
                            }
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

    fun onStart(context: Context) {
        context.registerReceiver(wifiReceiver, IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        })
    }

    fun onStop(context: Context) {
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
        current?.let {
            current = null
            scanList.add(0, it)
            wlanListener?.let {
                it.onNotify()
                it.onDisconnect()
            }
        }
        wifiManager.enableNetwork(
            getOldConfiguration(config.SSID)?.networkId ?: wifiManager.addNetwork(config), true
        )
    }

    fun disconnect() {
        wifiManager.disconnect()
    }

    fun remove(config: WifiConfiguration) {
        wifiManager.removeNetwork(config.networkId)
    }

    fun getOldConfiguration(ssid: String): WifiConfiguration? {
        wifiManager.configuredNetworks.forEach {
            if (ssid == it.SSID) return it
        }
        return null
    }

    interface WLANListener {
        fun onDisconnect()

        fun onConnect()

        fun onFailed()

        fun onNotify()

        fun onStateChange(state: Int)

        fun connectedInfo(scanResult: ScanResult)
        fun onEnable(enable: Boolean)
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