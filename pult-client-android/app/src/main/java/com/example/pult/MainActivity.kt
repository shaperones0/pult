package com.example.pult

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
import com.github.mikephil.charting.data.Entry

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: HistoryAdapter

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
            }
        }

        lifecycleScope.launch {
            viewModel.wsMetrics.collect { metricsJson ->
                try {
                    val jsonObject = JSONObject(metricsJson)
                    val cpuUsage = jsonObject.getDouble("cpu_usage_percent").toFloat()
                    addEntryToLiveChart(cpuUsage)
                } catch (e: Exception) {
                    //ignore
                }
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
            chart.xAxis.setDrawGridLines(false)
            chart.xAxis.textColor = Color.LTGRAY

            chart.axisLeft.setDrawGridLines(false)
            chart.axisLeft.textColor = Color.LTGRAY
            chart.axisLeft.axisMaximum = 100f
            chart.axisLeft.axisMinimum = 0f

            chart.axisRight.isEnabled = false
            chart.legend.textColor = Color.WHITE

            chart.data = LineData()
        }
    }

    private fun addEntryToLiveChart(cpuUsage: Float) {
        val data = binding.chartLive.data ?: return
        var set = data.getDataSetByIndex(0) as LineDataSet?

        if (set == null) {
            set = LineDataSet(null, "Live CPU Usage")
            set.color = "#00e676".toColorInt()
            set.setDrawCircles(false)

            set.lineWidth = 2f
            set.mode = LineDataSet.Mode.CUBIC_BEZIER
            data.addDataSet(set)
        }

        data.addEntry(Entry(set.entryCount.toFloat(), cpuUsage), 0)
        binding.chartLive.data.notifyDataChanged()
        binding.chartLive.notifyDataSetChanged()

        binding.chartLive.setVisibleXRangeMaximum(30f)
        binding.chartLive.moveViewToX(data.entryCount.toFloat())
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
}