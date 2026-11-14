package com.example.simplelauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TilesAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (View, AppInfo) -> Unit
) : RecyclerView.Adapter<TilesAdapter.VH>() {

    private var items: List<AppInfo> = emptyList()

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(it, app)
            true
        }
    }

    override fun getItemCount() = items.size
}

sealed class Row {
    data class Header(val letter: String) : Row()
    data class App(val info: AppInfo) : Row()
}

class AllAppsAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (View, AppInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<Row> = emptyList()

    fun submit(list: List<Row>) {
        rows = list
        notifyDataSetChanged()
    }

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val letter: TextView = view.findViewById(R.id.letter)
    }

    class AppVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderVH(inflater.inflate(R.layout.item_header, parent, false))
        } else {
            AppVH(inflater.inflate(R.layout.item_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).letter.text = row.letter
            is Row.App -> {
                val h = holder as AppVH
                h.icon.setImageDrawable(row.info.icon)
                h.label.text = row.info.label
                h.itemView.setOnClickListener { onClick(row.info) }
                h.itemView.setOnLongClickListener {
                    onLongClick(it, row.info)
                    true
                }
            }
        }
    }

    override fun getItemCount() = rows.size
}
