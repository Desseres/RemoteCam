package com.samsung.android.scan3d.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.http.Bandwidth
import com.samsung.android.scan3d.serv.CameraStatus
import com.samsung.android.scan3d.serv.StreamMode

/** A compact native guide: persistent navigation, readable cards, and a scrollable body. */
object BandwidthDialog {
    fun show(context: Context, status: CameraStatus) {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val foreground = Color.rgb(238, 243, 249)
        val secondary = Color.rgb(181, 194, 210)
        val accent = Color.rgb(139, 221, 207)
        val background = Color.rgb(18, 25, 35)
        val cardColor = Color.rgb(30, 40, 54)
        fun shape(color: Int, radius: Int = 12) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
        fun text(value: String, size: Float = 14f, color: Int = secondary, bold: Boolean = false) = TextView(context).apply {
            this.text = value; textSize = size; setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        val dialog = Dialog(context)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(12)) }
        root.addView(text(context.getString(R.string.guide_title), 23f, foreground, true).apply { isAccessibilityHeading = true })
        root.addView(text(context.getString(R.string.guide_subtitle), 13f).apply { setPadding(0, dp(6), 0, dp(16)) })
        val tabs = LinearLayout(context)
        root.addView(tabs)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(8)) }
        val scroll = ScrollView(context).apply { isFillViewport = false; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val close = Button(context).apply {
            setText(R.string.close); isAllCaps = false; setTextColor(background); this.background = shape(accent)
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(close, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        fun card(title: Int, content: Int? = null, titleColor: Int = foreground): LinearLayout {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); this.background = shape(cardColor)
            }
            card.addView(text(context.getString(title), 16f, titleColor, true).apply { isAccessibilityHeading = true })
            if (content != null) card.addView(text(context.getString(content)).apply { setPadding(0, dp(8), 0, 0) })
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            return card
        }
        fun table(card: LinearLayout, headers: List<String>, rows: List<List<String>>, selected: Int) {
            (listOf(headers) + rows).forEachIndexed { index, values ->
                val row = LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                    if (index > 0 && index == selected + 1) this.background = shape(Color.rgb(39, 67, 74), 6)
                    else if (index % 2 == 0) setBackgroundColor(Color.rgb(24, 33, 46))
                }
                values.forEach { value -> row.addView(text(value, 13f, if (index == 0) accent else foreground, index == 0).apply {
                    gravity = Gravity.CENTER; isAccessibilityHeading = index == 0; setPadding(dp(3), 0, dp(3), 0)
                }, LinearLayout.LayoutParams(0, -2, 1f)) }
                card.addView(row)
            }
        }
        val tabLabels = listOf(R.string.guide_formats, R.string.guide_bandwidth, R.string.guide_obs)
        val buttons = mutableListOf<Button>()
        fun select(index: Int) {
            buttons.forEachIndexed { i, button -> button.background = shape(if (i == index) accent else cardColor, 8); button.setTextColor(if (i == index) background else foreground); button.isSelected = i == index }
            body.removeAllViews(); scroll.scrollTo(0, 0)
            when (index) {
                0 -> {
                    card(R.string.guide_jpeg_title, R.string.guide_jpeg_body, accent)
                    card(R.string.guide_browser_title, R.string.guide_browser_body)
                    card(R.string.guide_mjpeg_title, R.string.guide_mjpeg_body)
                    card(R.string.guide_rtc_title, R.string.guide_rtc_body, accent)
                    card(R.string.guide_go2rtc_title, R.string.guide_go2rtc_body)
                    card(R.string.guide_lens_title, R.string.guide_lens_body)
                    card(R.string.rotation_label, R.string.rotation_jpeg_hint)
                    body.addView(text(context.getString(R.string.guide_modes_note), 13f))
                }
                1 -> {
                    val current = card(R.string.guide_current, titleColor = accent)
                    val size = status.config.resolution
                    val webRtc = status.config.mode == StreamMode.WEBRTC
                    val summary = when {
                        size == null -> context.getString(R.string.bandwidth_wait)
                        webRtc -> context.getString(R.string.guide_rtc_current, size.width, size.height, status.config.bitrateMbps, status.rtc.mbps)
                        status.averageFrameBytes > 0 -> context.getString(R.string.bandwidth_current, size.width, size.height, status.config.quality, status.averageFrameBytes / 1000, status.fps, status.sourceMbps)
                        else -> context.getString(R.string.bandwidth_wait)
                    }
                    current.addView(text(summary, 15f, foreground).apply { setPadding(0, dp(10), 0, 0) })
                    if (webRtc) {
                        val grid = card(R.string.guide_table_title)
                        val limits = listOf(4, 8, 12, 16, 24, 32, 40)
                        table(grid, listOf(R.string.guide_table_limit, R.string.guide_table_one, R.string.guide_table_two).map(context::getString),
                            limits.map { limit -> listOf(limit, limit * 2, limit * 4).map { context.getString(R.string.bandwidth_fps_value, it) } }, limits.indexOf(status.config.bitrateMbps))
                        body.addView(text(context.getString(R.string.guide_rtc_table_note), 13f).apply { setPadding(0, 0, 0, dp(16)) })
                    } else if (status.averageFrameBytes > 0) {
                        val grid = card(R.string.guide_table_title)
                        val rates = listOf(10, 15, 20, 24, 30)
                        table(grid, listOf(R.string.bandwidth_fps, R.string.bandwidth_stream, R.string.bandwidth_capacity).map(context::getString),
                            rates.map { fps -> listOf(context.getString(R.string.bandwidth_fps_value, fps),
                                context.getString(R.string.bandwidth_number, Bandwidth.megabitsPerSecond(status.averageFrameBytes, fps.toDouble())),
                                context.getString(R.string.bandwidth_number, Bandwidth.recommendedMbps(status.averageFrameBytes, fps.toDouble()))) }, rates.indexOf(status.fps))
                        body.addView(text(context.getString(R.string.bandwidth_explanation), 13f).apply { setPadding(0, 0, 0, dp(16)) })
                    }
                    card(R.string.guide_green_title, R.string.guide_green_body, Color.rgb(139, 214, 154))
                    card(R.string.guide_orange_title, R.string.guide_orange_body, Color.rgb(255, 190, 112))
                    card(R.string.guide_red_title, R.string.guide_red_body, Color.rgb(255, 138, 128))
                    card(R.string.guide_gray_title, R.string.guide_gray_body)
                    card(R.string.guide_measurement_title, R.string.guide_measurement_body)
                }
                2 -> {
                    val source = card(R.string.guide_obs_step1, R.string.guide_obs_step1_body, accent)
                    listOf(R.string.guide_copy_jpeg to "/view", R.string.guide_copy_mjpeg to "/cam.mjpeg", R.string.guide_copy_rtc to "/webrtc").forEach { (label, path) ->
                        source.addView(Button(context).apply {
                            setText(label); isAllCaps = false; textSize = 13f; setTextColor(accent)
                            this.background = shape(Color.rgb(40, 58, 75), 8)
                            setOnClickListener {
                                IpUtil.getLocalIpAddress()?.let { ip -> ClipboardUtil.copyToClipboard(context, "RemoteCam", "http://$ip:8080$path"); Toast.makeText(context, R.string.address_copied, Toast.LENGTH_SHORT).show() }
                                    ?: Toast.makeText(context, R.string.connect_wifi, Toast.LENGTH_SHORT).show()
                            }
                        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                    }
                    card(R.string.guide_obs_step2, R.string.guide_obs_step2_body)
                    card(R.string.guide_obs_step3, R.string.guide_obs_step3_body)
                    card(R.string.guide_network_title, R.string.guide_network_body)
                    card(R.string.guide_audio_title, R.string.guide_audio_body, accent)
                }
            }
            // New copy buttons must not pull the scroll position down when a tab is selected.
            buttons.getOrNull(index)?.let { it.isFocusableInTouchMode = true; it.requestFocus() }
            scroll.post { scroll.scrollTo(0, 0) }
        }
        tabLabels.forEachIndexed { index, label ->
            val button = Button(context).apply {
                setText(label); isAllCaps = false; textSize = 12f; minWidth = 0; minimumWidth = 0; setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { select(index) }
            }
            buttons.add(button)
            tabs.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (index > 0) marginStart = dp(6) })
        }
        select(0)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(shape(background, 18))
        dialog.window?.setTitle(context.getString(R.string.guide_title))
        dialog.show()
        val metrics = context.resources.displayMetrics
        dialog.window?.setLayout((metrics.widthPixels * 0.94).toInt().coerceAtMost(dp(640)), (metrics.heightPixels * 0.86).toInt())
    }
}
