package com.v2ray.actinium.ui

import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.v2ray.actinium.R
import com.v2ray.actinium.util.ConfigManager
import com.v2ray.actinium.util.ConfigUtil
import kotlinx.android.synthetic.main.item_recycler_main.view.*
import org.jetbrains.anko.*
import java.util.*

class MainRecyclerAdapter(val activity: AppCompatActivity, var configs: Array<out String>) : RecyclerView.Adapter<MainRecyclerAdapter.MainViewHolder>() {
    private val preference = activity.defaultSharedPreferences

    var actionMode: ActionMode? = null

    val selectedConfigs by lazy { HashSet<String>() }

    var changeable: Boolean = true
        set(value) {
            if (field == value)
                return
            field = value
            notifyDataSetChanged()
        }

    val actionModeCallback: ActionMode.Callback by lazy {
        object : ActionMode.Callback {
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem) = when (item.itemId) {
                R.id.del_config -> {
                    ConfigManager.delConfigFilesByName(selectedConfigs)
                    updateConfigList()
                    actionMode?.finish()
                    true
                }
                else -> false
            }

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                activity.menuInflater.inflate(R.menu.action_main_recycler, menu)
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                actionMode = null
                selectedConfigs.clear()
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount() = configs.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val name = configs[position]
        val conf = ConfigManager.getConfigFileByName(name).readText()

        holder.name.text = name
        holder.address.text = ConfigUtil.readAddressFromConfig(conf)
        holder.radio.isChecked = name == preference.getString(ConfigManager.PREF_CURR_CONFIG, "")

        if (actionMode != null) {
            holder.radio.isEnabled = false

            holder.infoContainer.onClick {
                if (holder.radio.isChecked) return@onClick

                if (selectedConfigs.contains(name)) {
                    selectedConfigs.remove(name)

                    if (selectedConfigs.isEmpty())
                        actionMode?.finish()
                } else
                    selectedConfigs.add(name)

                notifyDataSetChanged()
            }

            holder.bg.backgroundResource = if (selectedConfigs.contains(name))
                R.color.bg_item_selected else Color.TRANSPARENT
        } else if (changeable) {
            holder.radio.isEnabled = true
            holder.bg.backgroundColor = Color.TRANSPARENT
            holder.radio.onClick {
                preference.edit().putString(ConfigManager.PREF_CURR_CONFIG, name).apply()
                notifyDataSetChanged()
            }

            holder.infoContainer.onLongClick {
                if (holder.radio.isChecked) return@onLongClick false
                actionMode = activity.supportActionBar?.startActionMode(actionModeCallback)
                selectedConfigs.add(name)
                notifyDataSetChanged()
                true
            }

            holder.infoContainer.onClick {
                holder.infoContainer.context.startActivity<TextActivity>("title" to name, "text" to conf)
            }
        } else {
            holder.radio.isEnabled = false
            holder.bg.backgroundColor = Color.rgb(238, 238, 238)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(parent.context.layoutInflater
                .inflate(R.layout.item_recycler_main, parent, false))
    }

    fun updateConfigList() {
        configs = ConfigManager.configs
        notifyDataSetChanged()
    }

    class MainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val radio = itemView.btn_radio!!
        val name = itemView.tv_name!!
        val address = itemView.tv_address!!
        val infoContainer = itemView.info_container!!
        val bg = itemView.item_bg
    }
}
