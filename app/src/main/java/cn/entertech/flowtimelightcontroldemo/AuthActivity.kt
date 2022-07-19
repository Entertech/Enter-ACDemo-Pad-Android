package cn.entertech.flowtimelightcontroldemo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import cn.entertech.flowtimelightcontroldemo.app.Constant
import cn.entertech.flowtimelightcontroldemo.databinding.ActivityAuthBinding
import cn.entertech.flowtimelightcontroldemo.utils.SettingManager

class AuthActivity : BaseActivity() {

    private var setting: SettingManager? = null
    lateinit var bingding:ActivityAuthBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bingding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(bingding.root)
        initFullScreenDisplay(false)
        setting = SettingManager.getInstance()
        var appKey = setting?.appKey
        var appSecret = setting?.appSecret
        if (appKey != null && appKey != "") {
            bingding.etAppKey.setText(appKey)
        }
        if (appSecret != null && appSecret != "") {
            bingding.etAppSecret.setText(appSecret)
        }
    }

    fun onContinue(@Suppress("UNUSED_PARAMETER")view: View) {
        var appKey = bingding.etAppKey.text
        var appSecret = bingding.etAppSecret.text
        if (!appKey.isNullOrEmpty() && !appSecret.isNullOrEmpty()) {
            setting?.appKey = appKey.toString()
            setting?.appSecret = appSecret.toString()
            var intent = Intent(this, MainActivity::class.java)
            intent.putExtra(Constant.INTENT_APP_KEY, setting?.appKey)
            intent.putExtra(Constant.INTENT_APP_SECRET, setting?.appSecret)
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, getString(R.string.auth_page_title), Toast.LENGTH_SHORT).show()
        }
    }
}
