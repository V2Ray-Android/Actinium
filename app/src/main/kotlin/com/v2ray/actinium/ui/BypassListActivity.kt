package com.v2ray.actinium.ui

import android.os.Bundle
import com.v2ray.actinium.R
import com.v2ray.actinium.util.AppManagerUtil
import kotlinx.android.synthetic.main.activity_bypass_list.*
import org.jetbrains.anko.defaultSharedPreferences
import java.util.*

class BypassListActivity : BaseActivity() {
    companion object {
        const val PREF_BYPASS_LIST_SET = "pref_bypass_list_set"
    }

    val adapter by lazy {
        val blacklist = defaultSharedPreferences.getStringSet(PREF_BYPASS_LIST_SET, HashSet<String>())
        BypassListRecyclerAdapter(AppManagerUtil.loadNetworkAppList(this).sortedBy { it.appName }, blacklist)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bypass_list)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler_view.adapter = adapter
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.edit().putStringSet(PREF_BYPASS_LIST_SET, adapter.blacklist).apply()
    }
}