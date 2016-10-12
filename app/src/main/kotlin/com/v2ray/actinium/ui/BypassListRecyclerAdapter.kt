package com.v2ray.actinium.ui

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.v2ray.actinium.R
import com.v2ray.actinium.util.AppInfo
import kotlinx.android.synthetic.main.item_recycler_bypass_list.view.*
import org.jetbrains.anko.image
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.textColor
import java.util.*

class BypassListRecyclerAdapter(val apps: List<AppInfo>, blacklist: MutableSet<String>?) : RecyclerView.Adapter<BypassListRecyclerAdapter.AppViewHolder>() {

    val blacklist = if (blacklist == null) HashSet<String>() else HashSet<String>(blacklist)

    override fun onBindViewHolder(holder: AppViewHolder?, position: Int) {
        val appInfo = apps[position]
        holder?.bind(appInfo)
    }

    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        return AppViewHolder(parent.context.layoutInflater
                .inflate(R.layout.item_recycler_bypass_list, parent, false))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val inBlacklist: Boolean get() = blacklist.contains(appInfo.packageName)
        private lateinit var appInfo: AppInfo

        val icon = itemView.icon!!
        val name = itemView.name!!
        val checkBox = itemView.check_box!!

        fun bind(appInfo: AppInfo) {
            this.appInfo = appInfo

            icon.image = appInfo.appIcon
            name.text = appInfo.appName

            if (inBlacklist) {
                checkBox.isChecked = true
            } else {
                checkBox.isChecked = false
            }

            name.textColor = itemView.context.resources.getColor(if (appInfo.isSystemApp)
                R.color.color_highlight_material else R.color.abc_secondary_text_material_light)

            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (inBlacklist) {
                blacklist.remove(appInfo.packageName)
                checkBox.isChecked = false
            } else {
                blacklist.add(appInfo.packageName)
                checkBox.isChecked = true
            }
        }
    }
}