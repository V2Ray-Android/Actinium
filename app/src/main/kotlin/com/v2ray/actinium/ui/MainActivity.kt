package com.v2ray.actinium.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v7.widget.AppCompatEditText
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import com.orhanobut.logger.Logger
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.actinium.R
import com.v2ray.actinium.aidl.IV2RayService
import com.v2ray.actinium.defaultDPreference
import com.v2ray.actinium.dto.VpnNetworkInfo
import com.v2ray.actinium.extension.alert
import com.v2ray.actinium.extension.loadVpnNetworkInfo
import com.v2ray.actinium.extension.saveVpnNetworkInfo
import com.v2ray.actinium.extra.IV2RayServiceCallbackStub
import com.v2ray.actinium.service.V2RayVpnService
import com.v2ray.actinium.util.ConfigManager
import com.v2ray.actinium.util.ConfigUtil
import com.v2ray.actinium.util.currConfigName
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.singleLine
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class MainActivity : BaseActivity() {

    companion object {
        private const val REQUEST_CODE_VPN_PREPARE = 0
        private const val REQUEST_CODE_FILE_SELECT = 1
    }

    var fabChecked = false
        set(value) {
            field = value

            adapter.changeable = !value

            if (value) {
                fab.imageResource = R.drawable.ic_check_24dp
            } else {
                fab.imageResource = R.drawable.ic_action_logo
            }

        }

    private val adapter by lazy { MainRecyclerAdapter(this) }

    var bgService: IV2RayService? = null

    val conn = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            bgService?.unregisterCallback(serviceCallback)
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val service1 = IV2RayService.Stub.asInterface(service)
            bgService = service1
            service1.registerCallback(serviceCallback)
            serviceCallback.onStateChanged(service1.isRunning)

        }
    }

    val serviceCallback = object : IV2RayServiceCallbackStub(this) {
        override fun onNetworkInfoUpdated(info: VpnNetworkInfo?) {
            info?.let {
                adapter.notSavedNetworkInfo = it
                runOnUiThread { adapter.updateSelectedItem() }
            }
        }

        override fun onStateChanged(isRunning: Boolean) {
            runOnUiThread { fabChecked = isRunning }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.d(loadVpnNetworkInfo(currConfigName, VpnNetworkInfo()))

        fab.setOnClickListener {
            if (fabChecked) {
                bgService?.stopV2Ray()
            } else {
                val intent = VpnService.prepare(this)
                if (intent == null)
                    startV2Ray()
                else
                    startActivityForResult(intent, REQUEST_CODE_VPN_PREPARE)
            }
        }

        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = adapter

        importConfigFromIntent(intent)
    }

    fun startV2Ray() {
        if (adapter.actionMode != null)
            adapter.actionMode?.finish()

        V2RayVpnService.startV2Ray(this)
    }

    override fun onStart() {
        super.onStart()

        val intent = Intent(this.applicationContext, V2RayVpnService::class.java)
        bindService(intent, conn, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()

        bgService?.unregisterCallback(serviceCallback)
        unbindService(conn)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_VPN_PREPARE ->
                startV2Ray()
        //vpnPrepareCallback(resultCode == Activity.RESULT_OK)

            REQUEST_CODE_FILE_SELECT -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = data!!.data
                    tryOpenStreamFromUri(uri)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importConfigFromIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_config -> {
            importConfigFromFile()
            true
        }
        R.id.settings -> {
            startActivity<SettingsActivity>()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (adapter.actionMode != null)
            adapter.actionMode?.finish()
        else
            super.onBackPressed()
    }

    private fun tryOpenStreamFromUri(uri: Uri) {
        try {
            val rawInputStream = contentResolver.openInputStream(uri)
            handlerNewConfigFile(rawInputStream)
        } catch (e: FileNotFoundException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                RxPermissions.getInstance(this)
                        .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                        .subscribe {
                            if (it)
                                tryOpenStreamFromUri(uri)
                            else
                                toast(R.string.toast_permission_denied)
                        }
        } catch (t: Throwable) {
        }
    }

    private fun storeConfigFile(rawConfig: String, name: String) {
        val retFile = ConfigManager.getConfigFileByName(name)

        if (ConfigUtil.isConfigCompatible(rawConfig)) {
            val formatted = ConfigUtil.formatJSON(rawConfig)
            retFile.writeText(formatted)

            if (!fabChecked)
                defaultDPreference.setPrefString(ConfigManager.PREF_CURR_CONFIG, name)

            adapter.updateConfigList()
        } else {
            alert(R.string.msg_dialog_convert_config, R.string.title_dialog_convert_config) {
                positiveButton(android.R.string.ok) {
                    val retConfig = ConfigUtil.convertConfig(rawConfig)
                    val formatted = ConfigUtil.formatJSON(retConfig)
                    retFile.writeText(formatted)

                    if (!fabChecked)
                        defaultDPreference.setPrefString(ConfigManager.PREF_CURR_CONFIG, name)

                    saveVpnNetworkInfo(name, VpnNetworkInfo())

                    adapter.updateConfigList()
                }

                negativeButton()

                show()
            }
        }

    }

    private fun importConfigFromIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val file = File(uri.path)
            if (file.canRead())
                handlerNewConfigFile(file.inputStream())
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                RxPermissions.getInstance(this)
                        .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                        .subscribe {
                            if (it)
                                handlerNewConfigFile(file.inputStream())
                            else
                                toast(R.string.toast_permission_denied)
                        }
        }
    }

    private fun handlerNewConfigFile(ins: InputStream) {
        val rawConfig = ins.bufferedReader().readText()

        if (!ConfigUtil.validConfig(rawConfig)) {
            toast(R.string.toast_config_file_invalid)
            return
        }

        alert(R.string.title_dialog_input_config_name) {
            val input = AppCompatEditText(this@MainActivity)
            input.singleLine = true
            customView(input)

            positiveButton(android.R.string.ok) {
                val name = input.text.toString()
                storeConfigFile(rawConfig, name)
            }

            negativeButton(android.R.string.cancel)

            show()
        }
    }

    private fun importConfigFromFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.title_file_chooser)),
                    REQUEST_CODE_FILE_SELECT)
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

}