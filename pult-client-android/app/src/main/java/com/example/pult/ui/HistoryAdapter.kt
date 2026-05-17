package com.example.pult.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.pult.databinding.ItemHistoryBinding
import com.example.pult.db.HistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.toColorInt

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var items: List<HistoryEntity> = emptyList()

    fun submitList(newList: List<HistoryEntity>) {
        //filter
        val tempList = mutableListOf<HistoryEntity>()
        for (item in newList) {
            if (item.actionName == "background_monitor") continue

            tempList.add(item)
        }
        items = tempList
        notifyDataSetChanged()  //force redraw
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        //push values into list
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding)
        : RecyclerView.ViewHolder(binding.root) {

            private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            fun bind(item: HistoryEntity) {
                binding.tvTime.text = "[${dateFormat.format(Date(item.timestamp))}]"

                val content = if (item.actionName == "server_log")
                    item.resultMessage else
                    "${item.actionName}: ${item.resultMessage}"

                binding.tvLogContent.text = content

                if (item.logLevel == "error") {
                    binding.tvLogContent.setTextColor("#FF5252".toColorInt())
                } else {

                    binding.tvLogContent.setTextColor("#64B5F6".toColorInt())
                }
            }
        }
}