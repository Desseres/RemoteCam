package com.samsung.android.scan3d.util

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.http.Bandwidth
import com.samsung.android.scan3d.serv.CameraStatus

object BandwidthDialog {
    fun show(context: Context, status: CameraStatus) {
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun paragraph(text: String) {
            content.addView(TextView(context).apply { this.text = text; textSize = 14f; setPadding(0, 0, 0, padding) })
        }
        val size = status.config.resolution
        if (size == null || status.averageFrameBytes <= 0) paragraph(context.getString(R.string.bandwidth_wait))
        else {
            paragraph(context.getString(R.string.bandwidth_current, size.width, size.height,
                status.config.quality, status.averageFrameBytes / 1000, status.fps, status.sourceMbps))
            val table = TableLayout(context).apply { isStretchAllColumns = true; isShrinkAllColumns = true }
            fun row(values: List<String>, heading: Boolean = false) {
                table.addView(TableRow(context).apply {
                    values.forEach { value ->
                        addView(TextView(context).apply {
                            text = value; textSize = 13f
                            setPadding(padding / 4, padding / 3, padding / 4, padding / 3)
                            if (heading) { setTypeface(typeface, Typeface.BOLD); isAccessibilityHeading = true }
                        })
                    }
                })
            }
            row(listOf(R.string.bandwidth_fps, R.string.bandwidth_stream, R.string.bandwidth_capacity).map(context::getString), true)
            listOf(10, 15, 20, 24, 30).forEach { fps ->
                row(listOf(context.getString(R.string.bandwidth_fps_value, fps),
                    context.getString(R.string.bandwidth_number, Bandwidth.megabitsPerSecond(status.averageFrameBytes, fps.toDouble())),
                    context.getString(R.string.bandwidth_number, Bandwidth.recommendedMbps(status.averageFrameBytes, fps.toDouble()))))
            }
            content.addView(table, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        paragraph(context.getString(R.string.bandwidth_explanation))
        paragraph(context.getString(R.string.bandwidth_legend))
        val address = IpUtil.getLocalIpAddress()?.let { "http://$it:8080/view" } ?: "http://PHONE_IP:8080/view"
        paragraph(context.getString(R.string.bandwidth_obs, address))
        AlertDialog.Builder(context).setTitle(R.string.bandwidth_title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null).show()
    }
}
