package com.example.pult.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.pult.databinding.ItemHistoryBinding
import com.example.pult.db.HistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var items: List<HistoryEntity> = emptyList()

    fun submitList(newList: List<HistoryEntity>) {
        items = newList
        notifyDataSetChanged()  //force redraw
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: HistoryViewHolder,
        position: Int
    ) {
        //push values into list
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding)
        : RecyclerView.ViewHolder(binding.root) {

            private val dateFormat = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())

            fun bind(item: HistoryEntity) {
                binding.tvActionName.text = "Action: ${item.actionName}"
                binding.tvResultMessage.text = item.resultMessage
                binding.tvTime.text = dateFormat.format(Date(item.timestamp))
            }
        }
}