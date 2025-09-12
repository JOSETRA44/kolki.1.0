package com.example.kolki.ui.statistics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kolki.data.SimpleCategoryTotal
import com.example.kolki.databinding.ItemCategoryStatBinding

class CategoryStatsAdapter : ListAdapter<SimpleCategoryTotal, CategoryStatsAdapter.CategoryStatViewHolder>(CategoryStatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryStatViewHolder {
        val binding = ItemCategoryStatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryStatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryStatViewHolder, position: Int) {
        holder.bind(getItem(position), getMaxAmount())
    }
    
    private fun getMaxAmount(): Double {
        return currentList.maxOfOrNull { it.total } ?: 1.0
    }

    inner class CategoryStatViewHolder(
        private val binding: ItemCategoryStatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(categoryTotal: SimpleCategoryTotal, maxAmount: Double) {
            binding.categoryNameText.text = categoryTotal.category
            val prefs = binding.root.context.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val symbol = prefs.getString("currency_symbol", "S/")
            binding.categoryAmountText.text = "$symbol ${String.format("%.2f", categoryTotal.total)}"
            
            val progress = if (maxAmount > 0) {
                ((categoryTotal.total / maxAmount) * 100).toInt()
            } else {
                0
            }
            binding.categoryProgressBar.progress = progress
        }
    }

    class CategoryStatDiffCallback : DiffUtil.ItemCallback<SimpleCategoryTotal>() {
        override fun areItemsTheSame(oldItem: SimpleCategoryTotal, newItem: SimpleCategoryTotal): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: SimpleCategoryTotal, newItem: SimpleCategoryTotal): Boolean {
            return oldItem == newItem
        }
    }
}
