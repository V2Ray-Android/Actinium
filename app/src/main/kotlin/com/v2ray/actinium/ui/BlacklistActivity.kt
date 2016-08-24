package com.v2ray.actinium.ui

import android.os.Bundle
import com.v2ray.actinium.R
import com.v2ray.actinium.util.AppManagerUtil
import kotlinx.android.synthetic.main.activity_blacklist.*
import org.jetbrains.anko.defaultSharedPreferences
import java.util.*

class BlacklistActivity : BaseActivity() {
    companion object {
        const val PREF_BLACKLIST_SET = "pref_blacklist_set"
    }

    val adapter by lazy {
        val blacklist = defaultSharedPreferences.getStringSet(PREF_BLACKLIST_SET, HashSet<String>())
        BlacklistRecyclerAdapter(AppManagerUtil.loadNetworkAppList(this).sortedBy { it.appName }, blacklist)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blacklist)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler_view.adapter = adapter
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.edit().putStringSet(PREF_BLACKLIST_SET, adapter.blacklist).apply()
    }
}