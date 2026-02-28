package com.example.proxychecker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProxyAdapter(private val proxyList: List<ProxyItem>) :
    RecyclerView.Adapter<ProxyAdapter.ProxyViewHolder>() {

    class ProxyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val tvProtocol: TextView = view.findViewById(R.id.tvProtocol)
        val tvPing: TextView = view.findViewById(R.id.tvPing)
        val tvCountry: TextView = view.findViewById(R.id.tvCountry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_proxy, parent, false)
        return ProxyViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProxyViewHolder, position: Int) {
        val proxy = proxyList[position]

        holder.tvAddress.text = proxy.toString()
        holder.tvProtocol.text = proxy.protocol.name
        holder.tvCountry.text = proxy.country

        if (proxy.isAlive) {
            holder.tvPing.text = "${proxy.pingMs}ms"
            holder.tvPing.setTextColor(Color.parseColor("#008000")) // Зеленый
        } else {
            holder.tvPing.text = "Dead"
            holder.tvPing.setTextColor(Color.RED)
        }

        // Отключаем слушатель, чтобы при прокрутке чекбоксы не сходили с ума
        holder.cbSelect.setOnCheckedChangeListener(null)
        holder.cbSelect.isChecked = proxy.isSelected

        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            proxy.isSelected = isChecked
        }
    }

    override fun getItemCount(): Int = proxyList.size
}