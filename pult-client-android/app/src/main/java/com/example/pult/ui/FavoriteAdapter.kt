package com.example.pult.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pult.databinding.ItemFavoriteBinding
import com.example.pult.databinding.ItemHistoryBinding
import com.example.pult.db.FavoriteEntity

class FavoriteAdapter(
    private val onCommandClick: (String) -> Unit,
    private val onCommandLongClick: (String) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder>() {

    private var items: List<FavoriteEntity> = emptyList()

    fun submitList(newList: List<FavoriteEntity>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FavoriteViewHolder(private val binding: ItemFavoriteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FavoriteEntity) {
            binding.tvCommandText.text = item.commandText

            binding.root.setOnClickListener {
                onCommandClick(item.commandText)
            }

            binding.root.setOnLongClickListener {
                onCommandLongClick(item.commandText)
                true //no need for regular click
            }
        }
    }
}