package com.example.kolki.ui.expenses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kolki.R
import com.example.kolki.data.SimpleExpense
import android.content.Context
import android.widget.ImageButton
import android.widget.PopupMenu
import java.text.SimpleDateFormat
import java.util.*

class ExpensesAdapter(
    private val onItemClick: (SimpleExpense) -> Unit,
    private val onEdit: (SimpleExpense) -> Unit,
    private val onDelete: (SimpleExpense) -> Unit
) : ListAdapter<SimpleExpense, ExpensesAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(expense: SimpleExpense) {
            itemView.findViewById<TextView>(R.id.categoryText).text = expense.category
            itemView.findViewById<TextView>(R.id.commentText).text = expense.comment ?: ""
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            itemView.findViewById<TextView>(R.id.dateText).text = dateFormat.format(expense.date)
            
            val prefs = itemView.context.getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
            val symbol = prefs.getString("currency_symbol", "S/")
            val amountStr = String.format(Locale.getDefault(), "%.2f", expense.amount)
            itemView.findViewById<TextView>(R.id.amountText).text = "$symbol $amountStr"
            
            itemView.setOnClickListener {
                onItemClick(expense)
            }

            // Overflow menu (three dots)
            itemView.findViewById<ImageButton>(R.id.menuButton)?.setOnClickListener { v ->
                val popup = PopupMenu(v.context, v)
                popup.inflate(R.menu.menu_expense_item)
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit -> { onEdit(expense); true }
                        R.id.action_delete -> { onDelete(expense); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}

class ExpenseDiffCallback : DiffUtil.ItemCallback<SimpleExpense>() {
    override fun areItemsTheSame(oldItem: SimpleExpense, newItem: SimpleExpense): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SimpleExpense, newItem: SimpleExpense): Boolean {
        return oldItem == newItem
    }
}
