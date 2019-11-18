package top.zhangyz.wlan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    private var wlanHelper: WLANHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wlan)
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.let {
            wlanHelper = WLANHelper(it, object : WLANHelper.WLANListener {
                override fun onEnable(enable: Boolean) {
                    if (wifiSwitch.isChecked != enable) {
                        wifiSwitch.setOnCheckedChangeListener(null)
                        wifiSwitch.isChecked = enable
                        wifiSwitch.setOnCheckedChangeListener(this@WLANActivity)
                    }
                }

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
                    wlanRecyclerView.adapter?.notifyDataSetChanged()
                }

                override fun onDisconnect() {
                    connectedLayout.visibility = View.GONE
                }

                override fun onConnect() {
                }

                override fun onFailed() {
                }
            }).apply {
                wifiSwitch.isChecked = isOpen()
            }
        }
        connectedLayout.setOnClickListener {

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
                        if (isOldWifi(result.SSID)) {
                            return@setOnClickListener
                        }
                        layoutInflater.inflate(R.layout.wlan_password_dialog, null).run {
                            wlanDialogTitle.text = result.SSID

                            wlanPwdEditText.addTextChangedListener {
                                wlanConnect.isEnabled = it != null && it.length > 7
                            }
                            val dialog =
                                AlertDialog.Builder(this@WLANActivity).setNegativeButton("", null)
                                    .setPositiveButton("", null).setView(this).create()
                            wlanCancel.setOnClickListener {
                                dialog.cancel()
                            }
                            wlanConnect.setOnClickListener {
                                wlanHelper?.connect(result, wlanPwdEditText.text.toString())
                            }
                            dialog.show()
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
    }

    private fun isOldWifi(ssid: String?): Boolean {
        if (ssid != null && ssid.isNotEmpty())
            wlanHelper?.getOldConfiguration("\"$ssid\"")?.let {
                wlanHelper?.connect(it)
                return true
            }
        return false
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
