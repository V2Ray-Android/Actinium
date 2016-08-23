package com.v2ray.actinium.ui

import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.v2ray.actinium.R
import com.v2ray.actinium.util.AppInfo
import kotlinx.android.synthetic.main.item_recycler_blacklist.view.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.backgroundResource
import org.jetbrains.anko.image
import org.jetbrains.anko.layoutInflater

class BlacklistRecyclerAdapter(val apps: List<AppInfo>, var blacklist: MutableSet<String>) : RecyclerView.Adapter<BlacklistRecyclerAdapter.AppViewHolder>() {

    override fun onBindViewHolder(holder: AppViewHolder?, position: Int) {
        val appInfo = apps[position]
        holder?.bind(appInfo)
    }

    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        return AppViewHolder(parent.context.layoutInflater
                .inflate(R.layout.item_recycler_blacklist, parent, false))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val inBlacklist: Boolean get() = blacklist.contains(appInfo.packageName)
        private lateinit var appInfo: AppInfo

        val icon = itemView.icon!!
        val name = itemView.name!!

        fun bind(appInfo: AppInfo) {
            this.appInfo = appInfo

            icon.image = appInfo.appIcon
            name.text = appInfo.appName

            if (inBlacklist)
                itemView.backgroundResource = R.color.bg_item_selected
            else
                itemView.backgroundColor = Color.TRANSPARENT

            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (inBlacklist) {
                blacklist.remove(appInfo.packageName)
                itemView.backgroundColor = Color.TRANSPARENT
            } else {
                blacklist.add(appInfo.packageName)
                itemView.backgroundResource = R.color.bg_item_selected
            }
        }
    }
}