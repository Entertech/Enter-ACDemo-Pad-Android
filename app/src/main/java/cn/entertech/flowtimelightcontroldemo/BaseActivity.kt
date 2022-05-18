package cn.entertech.flowtimelightcontroldemo

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat


open class BaseActivity : AppCompatActivity() {
    var currentActivity: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        FirebaseAnalytics.getInstance(this).setCurrentScreen(this, currentActivityName, null)
    }

    //设置字体为默认大小，不随系统字体大小改而改变
    override fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.fontScale != 1f) //非默认值
            resources
        super.onConfigurationChanged(newConfig)
    }


    override fun getResources(): Resources? {
        val res: Resources = super.getResources()
        if (res.getConfiguration().fontScale != 1f) { //非默认值
            val newConfig = Configuration()
            newConfig.setToDefaults() //设置默认
            res.updateConfiguration(newConfig, res.getDisplayMetrics())
        }
        return res
    }
    fun initFullScreenDisplay(isStatusTextBlack:Boolean = true) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        if (!getDarkModeStatus(this) && isStatusTextBlack) {
            setStatusBarLight()
        }
    }


    fun setStatusBarVisible(isVisible: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, isVisible)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            if (isVisible) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    //检查当前系统是否已开启暗黑模式
    fun getDarkModeStatus(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
    //修改状态栏字体
    fun setStatusBarLight() {
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    fun getColorInDarkMode(lightColor: Int, darkColor: Int): Int {
        if (getDarkModeStatus(this)) {
            return ContextCompat.getColor(
                this,
                darkColor
            )
        } else {
            return ContextCompat.getColor(
                this,
                lightColor
            )
        }
    }
}