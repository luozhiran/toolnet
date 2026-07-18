package com.itg.ddlnet.networkscene

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import com.itg.ddlnet.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class NetworkSceneLogger(
    private val activity: AppCompatActivity,
    private val tag: String
) {
    private val rootView: View = activity.findViewById(R.id.network_scene_root)
    private val resultView: TextView = activity.findViewById(R.id.tv_result)
    private val resultScroll: ScrollView = activity.findViewById(R.id.result_scroll)
    private val resultPanel: LinearLayoutCompat = activity.findViewById(R.id.result_panel)
    private val resultBubble: TextView = activity.findViewById(R.id.btn_result_bubble)
    private val resultPanelHandle: View = activity.findViewById(R.id.result_panel_handle)

    private val resultBuffer = SpannableStringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var resultCount = 0
    private var panelDownRawX = 0f
    private var panelDownRawY = 0f
    private var panelStartWidth = 0
    private var panelDragging = false

    init {
        setupResultPanelGesture()
    }

    fun append(message: String) {
        Log.d(tag, message)
        activity.runOnUiThread {
            resultCount += 1
            resultBubble.text = "Log\n$resultCount"
            appendStyledResult(message)
            resultScroll.post { resultScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    fun closeIfOpen(): Boolean {
        if (resultPanel.visibility != View.VISIBLE) return false
        hideResultPanel()
        return true
    }

    private fun setupResultPanelGesture() {
        val touchSlop = 8.dp()
        resultBubble.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panelDownRawX = event.rawX
                    panelDownRawY = event.rawY
                    panelDragging = false
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - panelDownRawX
                    val dy = event.rawY - panelDownRawY
                    if (!panelDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        panelDragging = true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    val dx = event.rawX - panelDownRawX
                    if (!panelDragging || dx > 32.dp()) {
                        showResultPanel()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        resultPanelHandle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panelDownRawX = event.rawX
                    panelDownRawY = event.rawY
                    panelStartWidth = resultPanel.width.takeIf { it > 0 } ?: getDefaultPanelWidth()
                    panelDragging = false
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - panelDownRawX
                    val dy = event.rawY - panelDownRawY
                    if (!panelDragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        panelDragging = true
                    }
                    if (panelDragging) {
                        setResultPanelWidth((panelStartWidth + dx.toInt()).coerceIn(0, getMaxPanelWidth()))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (panelDragging) {
                        settleResultPanel()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun appendStyledResult(message: String) {
        val time = timeFormat.format(Date())
        val statusColor = when {
            message.contains("failed", ignoreCase = true) || message.contains("错误") -> 0xFFFF6B6B.toInt()
            message.contains("complete", ignoreCase = true) || message.contains("finish", ignoreCase = true) -> 0xFF4ADE80.toInt()
            message.contains("start", ignoreCase = true) || message.contains("connecting", ignoreCase = true) -> 0xFF60A5FA.toInt()
            else -> 0xFFE5E7EB.toInt()
        }
        val splitIndex = message.indexOf(':')
        val title = if (splitIndex > 0) message.substring(0, splitIndex) else "info"
        val detail = if (splitIndex > 0) message.substring(splitIndex + 1).trim() else message

        resultBuffer.append("┌ ")
        appendColored(resultBuffer, time, 0xFF94A3B8.toInt(), bold = false)
        resultBuffer.append("  ")
        appendColored(resultBuffer, title, statusColor, bold = true)
        resultBuffer.append('\n')
        resultBuffer.append("│ ")
        appendColored(resultBuffer, detail.ifEmpty { "-" }, 0xFFE5E7EB.toInt(), bold = false)
        resultBuffer.append("\n└────────────────────────\n\n")
        resultView.text = resultBuffer
    }

    private fun appendColored(
        builder: SpannableStringBuilder,
        text: String,
        color: Int,
        bold: Boolean
    ) {
        val start = builder.length
        builder.append(text)
        builder.setSpan(ForegroundColorSpan(color), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) {
            builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showResultPanel() {
        resultBubble.visibility = View.INVISIBLE
        resultPanel.visibility = View.VISIBLE
        resultPanel.translationX = -getDefaultPanelWidth().toFloat()
        setResultPanelWidth(getDefaultPanelWidth())
        resultPanel.animate().translationX(0f).setDuration(180).start()
        resultScroll.post { resultScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun hideResultPanel() {
        val targetWidth = resultPanel.width.takeIf { it > 0 } ?: getDefaultPanelWidth()
        resultPanel.animate()
            .translationX(-targetWidth.toFloat())
            .setDuration(160)
            .withEndAction {
                resultPanel.visibility = View.GONE
                resultPanel.translationX = 0f
                resultBubble.visibility = View.VISIBLE
            }
            .start()
    }

    private fun setResultPanelWidth(width: Int) {
        resultPanel.layoutParams = resultPanel.layoutParams.apply {
            this.width = width
            height = ViewGroup.LayoutParams.MATCH_PARENT
        } as ViewGroup.LayoutParams
    }

    private fun settleResultPanel() {
        val width = resultPanel.width
        val rootWidth = getAvailableRootWidth()
        val hiddenThreshold = (rootWidth * 0.18f).toInt()
        if (width <= hiddenThreshold) {
            hideResultPanel()
            return
        }

        val targets = intArrayOf(rootWidth / 3, rootWidth / 2, getMaxPanelWidth())
        val target = targets.minBy { abs(it - width) }
        resultPanel.animate()
            .setDuration(160)
            .withEndAction { setResultPanelWidth(target) }
            .start()
        setResultPanelWidth(target)
    }

    private fun getDefaultPanelWidth(): Int = getAvailableRootWidth() / 2

    private fun getMaxPanelWidth(): Int = (getAvailableRootWidth() * 0.9f).toInt()

    private fun getAvailableRootWidth(): Int {
        return rootView.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
    }

    private fun Int.dp(): Int = (this * activity.resources.displayMetrics.density).toInt()
}
