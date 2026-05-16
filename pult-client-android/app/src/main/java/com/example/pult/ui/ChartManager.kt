package com.example.pult.ui

import android.graphics.Color
import androidx.core.graphics.toColorInt
import com.example.pult.db.HistoryEntity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.DataSet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.mutableListOf
import kotlin.math.abs

class ChartManager(val chartLive: LineChart, val chartHistory: LineChart) {
    private val liveTimeWindow = 15L
    private val liveTimestamps = mutableListOf<Long>()
    private val liveStartTime = System.currentTimeMillis()
    private val historyXToTimeMap = mutableListOf<Pair<Float, Long>>()

    data class ChartStyle(
        val colorRes: Int,
        val label: String
    )

    private val chartStyle = mapOf(
        "CPU" to ChartStyle("#00E676".toColorInt(), "CPU"),
        "RAM" to ChartStyle("#FF5252".toColorInt(), "RAM")
    )
    
    fun chartsSetup() {
        val charts = listOf(chartLive, chartHistory)

        for (chart in charts) {
            chart.description.isEnabled = false
            chart.setTouchEnabled(true)
            chart.isDragEnabled = true
            chart.setScaleEnabled(true)
            chart.setDrawGridBackground(false)

            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.textColor = Color.LTGRAY
            chart.xAxis.enableGridDashedLine(10f, 10f, 0f)
            chart.xAxis.gridColor = "#444444".toColorInt()

            chart.axisRight.setDrawGridLines(false)
            chart.axisRight.textColor = Color.LTGRAY
            chart.axisRight.axisMaximum = 100f
            chart.axisRight.axisMinimum = 0f
            chart.axisRight.enableGridDashedLine(10f, 10f, 0f)
            chart.axisRight.gridColor = "#444444".toColorInt()

            chart.axisLeft.isEnabled = false
            chart.legend.textColor = Color.WHITE

            chart.data = LineData()
        }

        //pre-fill vales of chart live
        val maxN: Int = liveTimeWindow.toInt()
        for (i in 0..<maxN) {
            chartLivePush(
                0f, 0f,
                (i.toLong() - liveTimeWindow) * 1000 + liveStartTime
            )
        }
    }
    
    fun chartLivePush(cpuUsage: Float, ramUsage: Float, curTime: Long? = null) {
        val data = chartLive.data ?: return
        var cpuSet = data.getDataSetByIndex(0) as LineDataSet?
        var ramSet = data.getDataSetByIndex(1) as LineDataSet?

        if (cpuSet == null) {
            cpuSet = chartMakeDatasetStyle("CPU", mutableListOf(), true)
            data.addDataSet(cpuSet)
        }

        if (ramSet == null) {
            ramSet = chartMakeDatasetStyle("RAM", mutableListOf(), true)
            data.addDataSet(ramSet)
        }

        val currentMs = curTime ?: System.currentTimeMillis()
        val xSecs = (currentMs - liveStartTime) / 1000f

        liveTimestamps.add(currentMs)

        data.addEntry(Entry(xSecs, cpuUsage), 0)
        data.addEntry(Entry(xSecs, ramUsage), 1)

        //cleanup
        val memoryWindow = 60f
        while (cpuSet.entryCount > 0 && cpuSet.getEntryForIndex(0).x < (xSecs - memoryWindow)) {
            cpuSet.removeFirst()
            ramSet.removeFirst()
            if (liveTimestamps.isNotEmpty()) {
                liveTimestamps.removeAt(0)
            }
        }

        chartLive.xAxis.valueFormatter = TimeAxisFormatter(liveTimestamps, liveStartTime)

        chartLive.data.notifyDataChanged()
        chartLive.notifyDataSetChanged()

        chartLive.setVisibleXRangeMaximum(liveTimeWindow.toFloat())
        chartLive.moveViewToX(xSecs - liveTimeWindow)
    }

    fun chartHistoryFromList(historyList: List<HistoryEntity>) {
        val metricsLogs = historyList.filter {
            it.logLevel == "info" && it.resultMessage.contains("CPU:")
        }
        if (metricsLogs.isEmpty()) return

        val regex = """CPU: ([\d.]+)%, RAM: ([\d.]+)%""".toRegex()
        val dataSets = mutableListOf<ILineDataSet>()

        historyXToTimeMap.clear()
        chartHistory.xAxis.removeAllLimitLines()

        var currentCpuChunk = mutableListOf<Entry>()
        var currentRamChunk = mutableListOf<Entry>()

        var artificialX = 0f
        var lastTimestamp = 0L
        val maxGapMs = 15 * 60 * 1000
        val gapVisualWidthSecs = 60f

        for (log in metricsLogs) {
            val match = regex.find(log.resultMessage) ?: continue
            val cpu = match.groupValues[1].toFloat()
            val ram = match.groupValues[2].toFloat()


            if (lastTimestamp != 0L) {
                val deltaMs = log.timestamp - lastTimestamp

                if (deltaMs > maxGapMs) {
                    //break
                    if (currentCpuChunk.isNotEmpty()) {
                        dataSets.add(chartMakeDatasetStyle("CPU", currentCpuChunk, dataSets.isEmpty()))
                        dataSets.add(chartMakeDatasetStyle("RAM", currentRamChunk, dataSets.size == 1))
                        currentCpuChunk = mutableListOf()
                        currentRamChunk = mutableListOf()
                    }

                    chartMakeLimitLine(chartHistory, artificialX)
                    artificialX += gapVisualWidthSecs
                    chartMakeLimitLine(chartHistory, artificialX)
                }
                else {
                    artificialX += (deltaMs / 1000f)
                }
            }

            currentCpuChunk.add(Entry(artificialX, cpu))
            currentRamChunk.add(Entry(artificialX, ram))
            historyXToTimeMap.add(Pair(artificialX, log.timestamp))

            lastTimestamp = log.timestamp
        }

        if (currentCpuChunk.isNotEmpty()) {
            dataSets.add(chartMakeDatasetStyle("CPU", currentCpuChunk, dataSets.isEmpty()))
            dataSets.add(chartMakeDatasetStyle("RAM", currentRamChunk, dataSets.size == 1))

        }

        chartHistory.data = LineData(dataSets)
        chartHistory.xAxis.valueFormatter = SmartTimeFormatter(historyXToTimeMap, "HH:mm")

        chartHistory.setVisibleXRangeMinimum(15f)
        chartHistory.setVisibleXRangeMaximum(900f)

        chartHistory.moveViewToX(artificialX)

        chartHistory.invalidate()
    }

    private fun chartMakeDataset(entries: List<Entry>, colorRes: Int, showInLegend: Boolean, label: String): LineDataSet {
        val set = LineDataSet(entries, if (showInLegend) label else "")
        set.color = colorRes
        set.setDrawCircles(true)
        set.circleRadius = 1.5f
        set.circleColors = listOf(colorRes)
        set.setDrawCircleHole(false)
        set.lineWidth = 2f
        if (!showInLegend) {
            set.form = Legend.LegendForm.NONE
        }
        return set
    }

    private fun chartMakeDatasetStyle(styleName: String, entries: List<Entry>, showInLegend: Boolean): LineDataSet {
        val style = chartStyle[styleName]!!
        return chartMakeDataset(
            entries,
            style.colorRes,
            showInLegend,
            style.label
        )
    }

    private fun chartMakeLimitLine(chart: LineChart, xPos: Float) {
        val limitLine = LimitLine(xPos)
        limitLine.lineColor = "#666666".toColorInt()
        limitLine.lineWidth = 1.5f
        chart.xAxis.addLimitLine(limitLine)
    }

    class TimeAxisFormatter(
        private val timestamps: List<Long>,
        private val startTime: Long
    ) : ValueFormatter() {
        private val format = SimpleDateFormat("mm.ss", Locale.getDefault())
        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            //value is elapsed time since startTime
            val actualTimeMs = startTime + (value * 1000L).toLong()
            return format.format(Date(actualTimeMs))
        }
    }

    class SmartTimeFormatter(
        private val xToTimeMap: List<Pair<Float, Long>>,
        pattern: String
    ) : ValueFormatter() {
        private val format = SimpleDateFormat(pattern, Locale.getDefault())
        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            if (xToTimeMap.isEmpty()) return ""
            val closest = xToTimeMap.minByOrNull { abs(it.first - value) }
            return closest?.let { format.format(Date(it.second)) } ?: ""
        }
    }

}