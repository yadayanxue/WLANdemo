package top.zhangyz.wlan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Rect
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_wlan.*
import kotlinx.android.synthetic.main.wlan_item_layout.view.*
import kotlinx.android.synthetic.main.wlan_password_dialog.view.*
import kotlin.math.roundToInt

class WLANActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        wlanHelper?.setWifiEnabled(isChecked)
    }

    private var dialog: AlertDialog? = null
    private var dialogLayout: View? = null
    private var wlanHelper: WLANHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wlan)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.let {
            wlanHelper = WLANHelper(it, object : WLANHelper.WLANListener {
                override fun onEnable(enable: Boolean) {
                    if (wifiSwitch.isChecked != enable) {
                        wifiSwitch.setOnCheckedChangeListener(null)
                        wifiSwitch.isChecked = enable
                        wifiSwitch.setOnCheckedChangeListener(this@WLANActivity)
                    }
                }

                override fun onConnect(scanResult: ScanResult) {
                    cancelDialog()
                    connectedLayout.visibility = View.VISIBLE
                    connectLevel.setImageResource(WLANHelper.getLevelResource(scanResult.level))
                    connectLabel.text = scanResult.SSID
                    connectLock.visibility =
                        if (WLANHelper.needPassword(scanResult)) View.VISIBLE else View.GONE
                }

                override fun onStateChange(state: Int) {
                }

                override fun onNotify() {
                    wlanRecyclerView.adapter?.notifyDataSetChanged()
                }

                override fun onDisconnect() {
                    connectedLayout.visibility = View.GONE
                }

                override fun onFailed() {
                    dialogLayout?.wlanTip?.let {
                        it.visibility = View.VISIBLE
                        it.setText(R.string.wlan_password_error)
                    }
                }
            }).apply {
                wifiSwitch.isChecked = isOpen()
            }
        }
        wifiSwitch.setOnCheckedChangeListener(this)

        wlanRecyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(p0: ViewGroup, p1: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(
                    layoutInflater.inflate(
                        R.layout.wlan_item_layout,
                        p0,
                        false
                    )
                ) {}

            override fun getItemCount(): Int = wlanHelper?.scanList?.size ?: 0

            override fun onBindViewHolder(p0: RecyclerView.ViewHolder, p1: Int) {
                wlanHelper?.scanList?.get(p1)?.let { result ->
                    p0.itemView.setOnClickListener { _ ->
                        if (dialog != null) return@setOnClickListener
                        dialogLayout =
                            layoutInflater.inflate(R.layout.wlan_password_dialog, null)?.apply {
                                val oldWifi = getOldConfiguration(result.SSID)
                                dialog =
                                    AlertDialog.Builder(this@WLANActivity)
                                        .setNegativeButton("", null)
                                        .setPositiveButton("", null).setView(this).create()
                                        .also { dialog ->
                                            wlanDialogTitle.text = result.SSID
                                            wlanPwdEditText.addTextChangedListener {
                                                wlanConnect.isEnabled = it != null && it.length > 7
                                            }
                                            if (oldWifi != null) {
                                                wlanPwdEditText.hint = "新密码"
                                                wlanForget.run {
                                                    visibility = View.VISIBLE
                                                    setOnClickListener {
                                                        wlanHelper?.remove(oldWifi)
                                                        cancelDialog()
                                                    }
                                                }
                                                wlanConnect.run {
                                                    isEnabled = true
                                                    setOnClickListener {
                                                        if (wlanPwdEditText.text.length > 7)
                                                            wlanHelper?.connect(
                                                                result,
                                                                wlanPwdEditText.text.toString()
                                                            )
                                                        else
                                                            wlanHelper?.connect(oldWifi)
                                                    }
                                                }
                                            } else {
                                                wlanConnect.setOnClickListener {
                                                    wlanHelper?.connect(
                                                        result,
                                                        wlanPwdEditText.text.toString()
                                                    )
                                                }
                                            }
                                            wlanCancel.setOnClickListener {
                                                cancelDialog()
                                            }
                                            dialog.show()
                                        }
                            }
                    }
                    p0.itemView.wifiLabel.text = result.SSID
                    p0.itemView.wifiLock.visibility =
                        if (WLANHelper.needPassword(result)) View.VISIBLE else View.GONE
                    p0.itemView.wifiLevel.setImageResource(WLANHelper.getLevelResource(result.level))
                }
            }

        }

        wlanRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            val mDivider = resources.getDrawable(R.drawable.wlan_item_divider)
            private val mBounds = Rect()
            val space = resources.getDimensionPixelOffset(R.dimen.dp_30)

            override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                canvas.save()
                val left: Int
                val right: Int
                //noinspection AndroidLintNewApi - NewApi lint fails to handle overrides.
                if (parent.clipToPadding) {
                    left = parent.paddingLeft
                    right = parent.width - parent.paddingRight
                    canvas.clipRect(
                        left, parent.paddingTop, right,
                        parent.height - parent.paddingBottom
                    )
                } else {
                    left = 0
                    right = parent.width
                }

                val childCount = parent.childCount
                for (i in 0 until childCount - 1) {
                    val child = parent.getChildAt(i)
                    parent.getDecoratedBoundsWithMargins(child, mBounds)
                    val bottom = mBounds.bottom + child.translationY.roundToInt()
                    val top = bottom - mDivider.intrinsicHeight
                    mDivider.setBounds(left + space, top, right, bottom)
                    mDivider.draw(canvas)
                }
                canvas.restore()
            }

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.set(0, 0, 0, mDivider.intrinsicHeight)
            }
        })
        wlanHelper?.register(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        } else
            return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelDialog()
        wlanHelper?.unregister(this)
    }

    private fun cancelDialog() {
        dialog?.run {
            dialog = null
            dialogLayout = null
            cancel()
        }
    }

    private fun getOldConfiguration(ssid: String?): WifiConfiguration? {
        if (ssid != null && ssid.isNotEmpty())
            wlanHelper?.getOldConfiguration("\"$ssid\"")?.let {
                return it
            }
        return null
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
                this@WLANActivity,
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
