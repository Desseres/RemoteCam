package com.samsung.android.scan3d.util

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.core.net.toUri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.samsung.android.scan3d.R

/** Credits live outside the configuration flow, in the same visual style as the guide. */
object AboutDialog {
    fun show(context: Context) {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val background = Color.rgb(18, 25, 35)
        val cardColor = Color.rgb(30, 40, 54)
        val accent = Color.rgb(139, 221, 207)
        val foreground = Color.rgb(238, 243, 249)
        val secondary = Color.rgb(181, 194, 210)
        fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
            this.text = value; textSize = size; setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
            if (bold) { setTypeface(typeface, Typeface.BOLD); isAccessibilityHeading = true }
        }
        val dialog = Dialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(12))
            isFocusableInTouchMode = true
        }
        root.addView(text(context.getString(R.string.app_name), 24f, foreground, true))
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        root.addView(text(context.getString(R.string.about_version, version), 13f, secondary).apply {
            setPadding(0, dp(4), 0, dp(12))
        })
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(context).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        fun card(title: Int, content: Int): LinearLayout {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); this.background = shape(cardColor)
            }
            card.addView(text(context.getString(title), 16f, accent, true))
            card.addView(text(context.getString(content), 14f, secondary).apply { setPadding(0, dp(8), 0, 0) })
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            return card
        }
        val original = card(R.string.about_author_title, R.string.about_author_body)
        original.addView(Button(context).apply {
            setText(R.string.about_original_project); isAllCaps = false; setTextColor(accent)
            this.background = shape(Color.rgb(39, 56, 73))
            setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_VIEW, context.getString(R.string.original_project_url).toUri()))
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); height = dp(48) })
        card(R.string.about_free_title, R.string.about_free_body)
        card(R.string.about_update_title, R.string.about_update_body)
        body.addView(Button(context).apply {
            setText(R.string.privacy_policy); isAllCaps = false
            setOnClickListener { showPrivacy(context) }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(Button(context).apply {
            setText(R.string.close); isAllCaps = false; setTextColor(background); this.background = shape(accent)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        dialog.setContentView(root)
        root.requestFocus()
        dialog.window?.setBackgroundDrawable(shape(background))
        dialog.window?.setTitle(context.getString(R.string.info))
        dialog.show()
        val metrics = context.resources.displayMetrics
        dialog.window?.setLayout(minOf((metrics.widthPixels * 0.94).toInt(), dp(640)),
            minOf((metrics.heightPixels * 0.84).toInt(), dp(620)))
    }

    private fun showPrivacy(context: Context) {
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val policy = checkNotNull(javaClass.getResourceAsStream("/privacy-policy.txt"))
            .bufferedReader().use { it.readText() }
        val text = TextView(context).apply {
            this.text = policy; textSize = 16f
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.EMAIL_ADDRESSES)
        }
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.privacy_policy)
            .setView(ScrollView(context).apply { addView(text) })
            .setPositiveButton(R.string.close, null).show()
    }
}
