package com.v2ray.actinium.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.AppCompatEditText
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.v2ray.actinium.R
import com.v2ray.actinium.defaultDPreference
import com.v2ray.actinium.dto.VpnNetworkInfo
import com.v2ray.actinium.extension.alert
import com.v2ray.actinium.extension.loadVpnNetworkInfo
import com.v2ray.actinium.extension.saveVpnNetworkInfo
import com.v2ray.actinium.extension.toTrafficString
import com.v2ray.actinium.util.ConfigManager
import com.v2ray.actinium.util.currConfigName
import kotlinx.android.synthetic.main.item_recycler_main.view.*
import org.jetbrains.anko.*
import java.util.*

class MainRecyclerAdapter(val activity: AppCompatActivity) : RecyclerView.Adapter<MainRecyclerAdapter.MainViewHolder>() {
    private lateinit var configs: Array<out String>

    var actionMode: ActionMode? = null
    var renameItem: MenuItem? = null
    var delItem: MenuItem? = null
    var notSavedNetworkInfo = VpnNetworkInfo()

    val selectedConfigs by lazy { HashSet<String>() }

    var changeable: Boolean = true
        set(value) {
            if (field == value)
                return
            field = value
            notSavedNetworkInfo = VpnNetworkInfo()
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
                R.id.rename_config -> {
                    activity.alert(R.string.title_dialog_input_config_name) {
                        val oriName = selectedConfigs.single()

                        val input = AppCompatEditText(activity)
                        input.singleLine = true
                        input.setText(oriName)
                        customView(input)

                        positiveButton(android.R.string.ok) {
                            val newName = input.text.toString()
                            val newFile = ConfigManager.getConfigFileByName(newName)
                            val oriFile = ConfigManager.getConfigFileByName(oriName)

                            if (oriFile.renameTo(newFile) &&
                                    activity.currConfigName == oriName)
                                activity.defaultDPreference.setPrefString(ConfigManager.PREF_CURR_CONFIG, newName)

                            activity.saveVpnNetworkInfo(newName,
                                    activity.loadVpnNetworkInfo(oriName, VpnNetworkInfo())!!)

                            updateConfigList()
                            actionMode?.finish()
                        }

                        negativeButton(android.R.string.cancel)

                        show()
                    }
                    true
                }
                else -> false
            }

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                activity.menuInflater.inflate(R.menu.action_main_recycler, menu)
                renameItem = menu?.findItem(R.id.rename_config)
                delItem = menu?.findItem(R.id.del_config)
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                actionMode = null
                renameItem = null
                delItem = null
                selectedConfigs.clear()
                notifyDataSetChanged()
            }
        }
    }

    init {
        updateConfigList()
    }

    override fun getItemCount() = configs.size


    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val name = configs[position]
        val conf = ConfigManager.getConfigFileByName(name).readText()

        holder.name.text = name
        holder.radio.isChecked = name == activity.currConfigName

        var info = activity.loadVpnNetworkInfo(name, VpnNetworkInfo())!!
        if (holder.radio.isChecked && !changeable)
            info += notSavedNetworkInfo

        @SuppressLint("SetTextI18n")
        holder.statistics.text = "${info.rxByte.toTrafficString()} ↓ ${info.txByte.toTrafficString()} ↑"

        if (actionMode != null) {
            holder.radio.isEnabled = false

            holder.infoContainer.setOnClickListener {
                if (selectedConfigs.contains(name)) {
                    selectedConfigs.remove(name)

                    if (selectedConfigs.isEmpty())
                        actionMode?.finish()
                } else
                    selectedConfigs.add(name)

                updateActionModeStatus()
                notifyDataSetChanged()
            }

            holder.itemView.backgroundResource = if (selectedConfigs.contains(name))
                R.color.bg_item_selected else Color.TRANSPARENT
        } else {
            holder.infoContainer.setOnClickListener {
                holder.infoContainer.context.startActivity<TextActivity>("title" to name, "text" to conf)
            }
            holder.itemView.backgroundColor = Color.TRANSPARENT

            if (changeable) {
                holder.radio.isEnabled = true
                holder.radio.setOnClickListener {
                    activity.defaultDPreference.setPrefString(ConfigManager.PREF_CURR_CONFIG, name)
                    notifyDataSetChanged()
                }

                holder.infoContainer.setOnLongClickListener {
                    actionMode = activity.supportActionBar?.startActionMode(actionModeCallback)
                    selectedConfigs.add(name)
                    updateActionModeStatus()
                    notifyDataSetChanged()
                    true
                }
            } else {
                holder.radio.isEnabled = false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(parent.context.layoutInflater
                .inflate(R.layout.item_recycler_main, parent, false))
    }

    fun updateConfigList() {
        configs = ConfigManager.configs.sortedArray()
        notifyDataSetChanged()
    }

    fun updateActionModeStatus() {
        renameItem?.isVisible = selectedConfigs.size == 1
        delItem?.isVisible = !selectedConfigs.contains(activity.currConfigName)
    }

    fun updateSelectedItem() {
        notifyItemChanged(configs.indexOf(activity.currConfigName))
    }

    class MainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val radio = itemView.btn_radio!!
        val name = itemView.tv_name!!
        val statistics = itemView.tv_statistics!!
        val infoContainer = itemView.info_container!!
    }
}
