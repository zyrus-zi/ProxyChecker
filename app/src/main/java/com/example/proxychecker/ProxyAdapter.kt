package com.example.proxychecker

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ProxyAdapter(private val proxyList: List<ProxyItem>) :
    RecyclerView.Adapter<ProxyAdapter.ProxyViewHolder>() {

    class ProxyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val tvProtocol: TextView = view.findViewById(R.id.tvProtocol)
        val tvPing: TextView = view.findViewById(R.id.tvPing)
        val tvCountry: TextView = view.findViewById(R.id.tvCountry)
        val rootView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_proxy, parent, false)
        return ProxyViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProxyViewHolder, position: Int) {
        val proxy = proxyList[position]
        val context = holder.itemView.context

        holder.tvAddress.text = proxy.toString()
        holder.tvProtocol.text = proxy.protocol.name
        holder.tvCountry.text = proxy.country

        // Определяем тему для выбора цвета статуса
        val isDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (proxy.isAlive) {
            holder.tvPing.text = "${proxy.pingMs}ms"
            val colorRes = if (isDark) R.color.status_alive_dark else R.color.status_alive_light
            holder.tvPing.setTextColor(ContextCompat.getColor(context, colorRes))
        } else {
            holder.tvPing.text = "Dead"
            val colorRes = if (isDark) R.color.status_dead_dark else R.color.status_dead_light
            holder.tvPing.setTextColor(ContextCompat.getColor(context, colorRes))
        }

        // Disable listener to prevent checkbox issues during scroll
        holder.cbSelect.setOnCheckedChangeListener(null)
        holder.cbSelect.isChecked = proxy.isSelected

        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            proxy.isSelected = isChecked
            animateItemSelection(holder, isChecked)
        }

        // Add entrance animation
        animateItemEntrance(holder, position)
    }

    override fun getItemCount(): Int = proxyList.size

    /**
     * Animate item entrance with scale and alpha
     */
    private fun animateItemEntrance(holder: ProxyViewHolder, position: Int) {
        holder.rootView.alpha = 0f
        holder.rootView.scaleY = 0.8f

        val alphaAnimator = ObjectAnimator.ofFloat(holder.rootView, "alpha", 0f, 1f).apply {
            duration = 300
            startDelay = (position * 30).toLong()
        }

        val scaleAnimator = ObjectAnimator.ofFloat(holder.rootView, "scaleY", 0.8f, 1f).apply {
            duration = 300
            startDelay = (position * 30).toLong()
            interpolator = AccelerateDecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(alphaAnimator, scaleAnimator)
            start()
        }
    }

    /**
     * Animate item selection with pulse effect
     */
    private fun animateItemSelection(holder: ProxyViewHolder, isSelected: Boolean) {
        val scaleX = if (isSelected) 1.05f else 1f
        val scaleY = if (isSelected) 1.05f else 1f

        ObjectAnimator.ofFloat(holder.rootView, "scaleX", holder.rootView.scaleX, scaleX).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(holder.rootView, "scaleY", holder.rootView.scaleY, scaleY).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}