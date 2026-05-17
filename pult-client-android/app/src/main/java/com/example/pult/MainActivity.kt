package com.example.pult

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import com.example.pult.databinding.ActivityMainBinding
import com.example.pult.ui.HistoryAdapter
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.example.pult.ui.ChartManager
import com.example.pult.ui.FavoriteAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: HistoryAdapter

    private lateinit var chartManager: ChartManager
    private lateinit var prefsManager: PrefsManager
    private lateinit var favoriteAdapter: FavoriteAdapter

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
        favoriteAdapter = FavoriteAdapter(
            onCommandClick = { command ->
                showCommandConfirmDialog(command)
            },
            onCommandLongClick = { command ->
                viewModel.favoritesRemove(command)
            }
        )
        binding.rvFavorites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFavorites.adapter = favoriteAdapter

        binding.btnNewCommand.setOnClickListener {
            showCommandDialog()
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        // vertical
        binding.rvHistory.layoutManager = layoutManager
        binding.rvHistory.adapter = adapter

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
            viewModel.favoriteList.collect { list ->
                val layoutManager = binding.rvFavorites.layoutManager as LinearLayoutManager
                favoriteAdapter.submitList(list)

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

        checkPermissionsAndStartMonitoring()
    }

    private fun showCommandDialog() {
        val titleView = TextView(this).apply {
            text = "Выполнить"
        }
        styleAlertTitleView(titleView)

        val editText = EditText(this).apply {
            hint = "dir | ping 8.8.8.8 | python script.py"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            isSingleLine = true
        }

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            val marginSide = (16 * resources.displayMetrics.density).toInt()
            setMargins(marginSide, 0, marginSide, 0)
        }
        editText.layoutParams = params
        container.addView(editText)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setCustomTitle(titleView)
            .setView(container)
            .setPositiveButton("Отправить") { _, _ ->
                val command = editText.text.toString().trim()
                if (command.isNotEmpty()) {
                    showCommandConfirmDialog(command)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun styleAlertTitleView(tv: TextView) {
        tv.apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)

            val density = resources.displayMetrics.density
            val horizontalPadding = (20 * density).toInt()
            val topPadding = (2 * density).toInt()
            val bottomPadding = (8 * density).toInt()

            setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
        }
    }

    private fun showCommandConfirmDialog(command: String) {
        val titleView = TextView(this).apply {
            text = "Отправить команду?"
        }
        styleAlertTitleView(titleView)

        val textView = TextView(this).apply {
            text = command
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(Typeface.MONOSPACE)

            isSingleLine = true
        }

        val scrollView = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        scrollView.addView(textView)

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            val margin = (16 * resources.displayMetrics.density).toInt()
            setMargins(margin, 0, margin, 0)
        }
        scrollView.layoutParams = params
        container.addView(scrollView)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setCustomTitle(titleView)
            .setView(container)
            .setPositiveButton("Отправить") { _, _ ->
                if (command.isNotEmpty()) {
                    viewModel.favoritesAdd(command)
                    viewModel.sendCommand(command)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
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
                        viewModel.historyRefresh()
                    }
                }
            }
    }
}