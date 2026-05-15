package com.example.pult

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager

import com.example.pult.databinding.ActivityMainBinding
import com.example.pult.ui.HistoryAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: HistoryAdapter

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
    }
}