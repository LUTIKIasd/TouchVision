package com.example.touchvision

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private val items: List<HistoryItem>) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val pages: List<List<HistoryItem>> = items.chunked(3)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_page, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val itemsOnPage = pages[position]

        holder.card1.visibility = View.INVISIBLE
        holder.card2.visibility = View.INVISIBLE
        holder.card3.visibility = View.INVISIBLE

        itemsOnPage.getOrNull(0)?.let { fillCard(holder.card1, it) }
        itemsOnPage.getOrNull(1)?.let { fillCard(holder.card2, it) }
        itemsOnPage.getOrNull(2)?.let { fillCard(holder.card3, it) }
    }

    private fun fillCard(cardView: View, item: HistoryItem) {
        cardView.visibility = View.VISIBLE
        val text = cardView.findViewById<TextView>(R.id.itemText)
        val time = cardView.findViewById<TextView>(R.id.itemTime)
        text.text = item.text
        time.text = item.time
    }

    override fun getItemCount(): Int = pages.size

    class HistoryViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card1: View = v.findViewById(R.id.card1)
        val card2: View = v.findViewById(R.id.card2)
        val card3: View = v.findViewById(R.id.card3)
    }
}