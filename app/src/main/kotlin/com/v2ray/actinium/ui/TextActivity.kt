package com.v2ray.actinium.ui

import android.os.Bundle
import com.v2ray.actinium.R
import kotlinx.android.synthetic.main.activity_text.*

class TextActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text)

        title = intent.getStringExtra("title")
        text_view.text = intent.getStringExtra("text")

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}