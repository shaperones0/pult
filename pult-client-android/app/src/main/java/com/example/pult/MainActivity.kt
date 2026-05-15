package com.example.pult

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        adapter = HistoryAdapter()
        // vertical
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        // sub
        lifecycleScope.launch {
            viewModel.statusText.collect { current ->
                binding.tvStatus.text = current

            }
        }

        lifecycleScope.launch {
            viewModel.historyList.collect { list ->
                adapter.submitList(list)
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