package com.example.pult

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import com.example.pult.databinding.ActivityMainBinding
import com.example.pult.ui.HistoryAdapter
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.example.pult.ui.ChartManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: HistoryAdapter

    private lateinit var chartManager: ChartManager
    private lateinit var prefsManager: PrefsManager

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

        prefsManager = PrefsManager(this)

        chartManager = ChartManager(binding.chartLive, binding.chartHistory)
        chartManager.chartsSetup()

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    performLogout()
                    true
                }
                else -> false
            }
        }

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

                chartManager.chartHistoryFromList(list)
            }
        }

        lifecycleScope.launch {
            viewModel.wsMetrics.collect { metricsJson ->
                try {
                    val jsonObject = JSONObject(metricsJson)
                    val cpuUsage = jsonObject.getDouble("cpu_usage_percent").toFloat()
                    val ramUsage = jsonObject.getDouble("ram_usage_percent").toFloat()
                    chartManager.chartLivePush(cpuUsage, ramUsage)

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

    private fun performLogout() {
        prefsManager.clearCredentials()

        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)

        finish()
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