package com.example.kolki.ui.analysis

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kolki.R

data class ChatMessage(val role: String, val text: String)

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatAdapter.DIFF) {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
        val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem === newItem

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.role == newItem.role && oldItem.text == newItem.text
        }

        // Very small markdown bold parser for **bold** and __bold__
        private fun toBoldMarkdown(text: String): CharSequence {
            val sb = SpannableStringBuilder(text)
            applyBoldPattern(sb, "\\*\\*(.+?)\\*\\*")
            applyBoldPattern(sb, "__(.+?)__")
            return sb
        }

        private fun applyBoldPattern(sb: SpannableStringBuilder, pattern: String) {
            val regex = Regex(pattern)
            var offset = 0
            regex.findAll(sb.toString()).forEach { match ->
                val start = match.range.first - offset
                val end = match.range.last + 1 - offset
                if (start >= 0 && end <= sb.length) {
                    // Remove delimiters and apply bold
                    val inner = match.groups[1]?.value ?: return@forEach
                    sb.replace(start, end, inner)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, start + inner.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    offset += (match.value.length - inner.length)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == "user") TYPE_USER else TYPE_AI
    }

    class UserVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val msgTv = itemView.findViewById<TextView>(R.id.messageText)
        fun bind(m: ChatMessage) { msgTv.text = toBoldMarkdown(m.text) }
    }

    class AiVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val msgTv = itemView.findViewById<TextView>(R.id.messageText)
        fun bind(m: ChatMessage) { msgTv.text = toBoldMarkdown(m.text) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserVH(inf.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AiVH(inf.inflate(R.layout.item_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserVH -> holder.bind(item)
            is AiVH -> holder.bind(item)
        }
    }

}
