package com.example.pult

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import com.example.pult.databinding.ActivityMainBinding
import com.example.pult.ui.HistoryAdapter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.core.graphics.toColorInt
import com.example.pult.db.HistoryEntity
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: HistoryAdapter

    private val liveTimestamps = mutableListOf<Long>()
    private val liveStartTime = System.currentTimeMillis()
    private val liveTimeWindow = 15L
    private val historyXToTimeMap = mutableListOf<Pair<Float, Long>>()

    var socketTicksCount = 0

    //permission dialog launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBackgroundMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCharts()

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        adapter = HistoryAdapter()

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        // vertical
        binding.rvHistory.layoutManager = layoutManager
        binding.rvHistory.adapter = adapter

        // sub
        lifecycleScope.launch {
            viewModel.statusText.collect { current ->
                binding.tvStatus.text = current

            }
        }

        lifecycleScope.launch {
            viewModel.historyList.collect { list ->
                //autoscroll on update
                val layoutManager = binding.rvHistory.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val isAtBottom = lastVisiblePosition >= adapter.itemCount - 2 || adapter.itemCount == 0

                adapter.submitList(list)

                if (isAtBottom && list.isNotEmpty()) {
                    binding.rvHistory.scrollToPosition(list.size - 1)
                }

                loadHistoryList(list)
            }
        }

        lifecycleScope.launch {
            viewModel.wsMetrics.collect { metricsJson ->
                try {
                    val jsonObject = JSONObject(metricsJson)
                    val cpuUsage = jsonObject.getDouble("cpu_usage_percent").toFloat()
                    val ramUsage = jsonObject.getDouble("ram_usage_percent").toFloat()
                    addEntryToLiveChart(cpuUsage, ramUsage)

                    socketTicksCount++
                    if (socketTicksCount >= 10) {
                        viewModel.saveLiveMetricsToDb(cpuUsage, ramUsage)
                        socketTicksCount = 0
                    }
                } catch (_: Exception) {}
            }
        }

        binding.btnSend.setOnClickListener {
            viewModel.sendCommand()
        }

        binding.btnPing.setOnClickListener {
            val workRequest = OneTimeWorkRequestBuilder<PcMonitorWorker>().build()
            val workManager = WorkManager.getInstance(this@MainActivity)

            workManager.enqueue(workRequest)
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observe(this@MainActivity) { workInfo ->
                    if (workInfo != null && workInfo.state.isFinished) {
                        viewModel.refreshHistory()
                    }
                }
        }

        checkPermissionsAndStartMonitoring()
    }

    private fun setupCharts() {
        val charts = listOf(binding.chartLive, binding.chartHistory)

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
            addEntryToLiveChart(0f, 0f, (i.toLong() - liveTimeWindow) * 1000 + liveStartTime)
        }
    }

    @SuppressLint("UseKtx")
    private fun addEntryToLiveChart(cpuUsage: Float, ramUsage: Float, curTime: Long? = null) {
        val data = binding.chartLive.data ?: return
        var cpuSet = data.getDataSetByIndex(0) as LineDataSet?
        var ramSet = data.getDataSetByIndex(1) as LineDataSet?

        if (cpuSet == null) {
            cpuSet = createHistoryDataSet(mutableListOf(), "#00E676".toColorInt(), true, "CPU")
            data.addDataSet(cpuSet)
        }

        if (ramSet == null) {
            ramSet = createHistoryDataSet(mutableListOf(), "#FF5252".toColorInt(), true, "RAM")
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

        binding.chartLive.xAxis.valueFormatter = TimeAxisFormatter(liveTimestamps, liveStartTime)

        binding.chartLive.data.notifyDataChanged()
        binding.chartLive.notifyDataSetChanged()

        binding.chartLive.setVisibleXRangeMaximum(liveTimeWindow.toFloat())
//        binding.chartLive.setVisibleXRangeMinimum(tdWindow)
//        Log.i("MyActivity", "${xSecs - tdWindow}")
        binding.chartLive.moveViewToX(xSecs - liveTimeWindow)
    }

    private fun loadHistoryList(historyList: List<HistoryEntity>) {
        val metricsLogs = historyList.filter { it.logLevel == "info" && it.resultMessage.contains("CPU:") }
        if (metricsLogs.isEmpty()) return

        val regex = """CPU: ([\d.]+)%, RAM: ([\d.]+)%""".toRegex()
        val dataSets = mutableListOf<ILineDataSet>()

        historyXToTimeMap.clear()
        binding.chartHistory.xAxis.removeAllLimitLines()

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
                        dataSets.add(createHistoryDataSet(currentCpuChunk,
                            "#00E676".toColorInt(), dataSets.isEmpty(), "CPU"))
                        dataSets.add(createHistoryDataSet(currentRamChunk,
                            "#FF5252".toColorInt(), dataSets.size == 1, "RAM"))
                        currentCpuChunk = mutableListOf()
                        currentRamChunk = mutableListOf()
                    }

                    addGapLimitLine(artificialX)
                    artificialX += gapVisualWidthSecs
                    addGapLimitLine(artificialX)
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
            dataSets.add(createHistoryDataSet(currentCpuChunk,
                "#00E676".toColorInt(), dataSets.isEmpty(), "CPU"))
            dataSets.add(createHistoryDataSet(currentRamChunk,
                "#FF5252".toColorInt(), dataSets.size == 1, "RAM"))
        }

        binding.chartHistory.data = LineData(dataSets)
        binding.chartHistory.xAxis.valueFormatter = SmartTimeFormatter(historyXToTimeMap, "HH:mm")

        binding.chartHistory.setVisibleXRangeMinimum(15f)
        binding.chartHistory.setVisibleXRangeMaximum(900f)

        binding.chartHistory.moveViewToX(artificialX)

        binding.chartHistory.invalidate()
    }

    private fun createHistoryDataSet(entries: List<Entry>, colorRes: Int, showInLegend: Boolean, label: String): LineDataSet {
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

    private fun addGapLimitLine(xPos: Float) {
        val limitLine = LimitLine(xPos)
        limitLine.lineColor = "#666666".toColorInt() // Сплошная неяркая линия
        limitLine.lineWidth = 1.5f
        binding.chartHistory.xAxis.addLimitLine(limitLine)
    }

    private fun checkPermissionsAndStartMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startBackgroundMonitoring()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startBackgroundMonitoring()
        }
    }

    private fun startBackgroundMonitoring() {
        val workRequest = PeriodicWorkRequestBuilder<PcMonitorWorker>(15, TimeUnit.MINUTES).build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            "pc_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        workManager.getWorkInfosForUniqueWorkLiveData("pc_monitor")
            .observe(this) { workInfos ->
                if (workInfos.isNotEmpty()) {
                    val isAnyFinished = workInfos.any { it.state.isFinished}
                    if (isAnyFinished) {
                        viewModel.refreshHistory()
                    }
                }
            }
    }

    inner class TimeAxisFormatter(
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

    inner class SmartTimeFormatter(
        private val xToTimeMap: List<Pair<Float, Long>>,
        pattern: String
    ) : ValueFormatter() {
        private val format = SimpleDateFormat(pattern, Locale.getDefault())
        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            if (xToTimeMap.isEmpty()) return ""
            // Ищем ближайшую известную координату X
            val closest = xToTimeMap.minByOrNull { abs(it.first - value) }
            return closest?.let { format.format(Date(it.second)) } ?: ""
        }
    }
}