package cn.entertech.flowtimelightcontroldemo.utils

import android.animation.ValueAnimator
import android.animation.ValueAnimator.INFINITE
import android.animation.ValueAnimator.REVERSE
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.concurrent.CopyOnWriteArrayList

class YeelightControlHelper(var context: Context) {
    companion object {
        const val TAG = "YeelightControlHelper"
        const val HEART_VALUE_MIX = 45
        const val HEART_VALUE_MAX = 180
        const val LIGHT_RED_HUE = 0.0
        const val LIGHT_ORANGE_HUE = 30.0
        const val LIGHT_YELLOW_HUE = 60.0
        const val LIGHT_OLIVE_HUE = 90.0
        const val LIGHT_GREEN_HUE = 120.0

        @Volatile
        var mInstance: YeelightControlHelper? = null
        fun getInstance(context: Context): YeelightControlHelper {
            if (mInstance == null) {
                synchronized(YeelightControlHelper::class.java) {
                    if (mInstance == null) {
                        mInstance = YeelightControlHelper(context)
                    }
                }
            }
            return mInstance!!
        }
    }

    var mainHandler = Handler(Looper.getMainLooper())
    var yeelightManager =
        YeelightManager.getInstance(context)
    var mDeviceId: Int? = null
    fun init(success: ((Int) -> Unit)? = null, failure: ((String) -> Unit)? = null) {
        yeelightManager.searchDevices(success = fun(devices) {
            Log.d(TAG, "yeelight device is ${devices.toString()}")
            yeelightManager.connect(devices[0], fun(deviceId) {
                mDeviceId = deviceId
                setLightBrightness(40)
//                startBrightnessAnim()
                success?.invoke(deviceId)
            }, failure)
        }, failure = fun(error) {
            failure?.invoke(error)
        })
    }

    var hue: Double = LIGHT_RED_HUE
    fun reflectCoherenceToHue(coherence: Double): Double {
        if (hue == LIGHT_RED_HUE) {
            if (coherence in 20.0..39.0) {
                hue = LIGHT_ORANGE_HUE
            } else if (coherence in 40.0..59.0) {
                hue = LIGHT_YELLOW_HUE
            } else if (coherence in 60.0..79.0) {
                hue = LIGHT_OLIVE_HUE
            } else if (coherence > 79) {
                hue = LIGHT_GREEN_HUE
            }
        }
        if (hue == LIGHT_ORANGE_HUE) {
            if (coherence in 0.0..10.0) {
                hue = LIGHT_RED_HUE
            } else if (coherence in 40.0..59.0) {
                hue = LIGHT_YELLOW_HUE
            } else if (coherence in 60.0..79.0) {
                hue = LIGHT_OLIVE_HUE
            } else if (coherence > 79) {
                hue = LIGHT_GREEN_HUE
            }
        }
        if (hue == LIGHT_YELLOW_HUE) {
            if (coherence <= 10) {
                hue = LIGHT_RED_HUE
            } else if (coherence in 11.0..30.0) {
                hue = LIGHT_ORANGE_HUE
            } else if (coherence in 60.0..79.0) {
                hue = LIGHT_OLIVE_HUE
            } else if (coherence > 79) {
                hue = LIGHT_GREEN_HUE
            }
        }
        if (hue == LIGHT_OLIVE_HUE) {
            if (coherence <= 10) {
                hue = LIGHT_RED_HUE
            } else if (coherence in 11.0..30.0) {
                hue = LIGHT_ORANGE_HUE
            } else if (coherence in 31.0..50.0) {
                hue = LIGHT_YELLOW_HUE
            } else if (coherence > 79) {
                hue = LIGHT_GREEN_HUE
            }
        }
        if (hue == LIGHT_GREEN_HUE) {
            if (coherence <= 10) {
                hue = LIGHT_RED_HUE
            } else if (coherence in 11.0..30.0) {
                hue = LIGHT_ORANGE_HUE
            } else if (coherence in 31.0..50.0) {
                hue = LIGHT_YELLOW_HUE
            } else if (coherence in 51.0..70.0) {
                hue = LIGHT_OLIVE_HUE
            }
        }
        return hue
    }


    fun setHue(hue: Double?) {
        setLightBrightness(40)
        yeelightManager.writeCmd(
            YeelightManager.CMD_HSV,
            mDeviceId,
            hue.toString()
        )
    }

    fun setHeartValue(value: Double?) {
        if (value == null || mDeviceId == null) {
            return
        }
//        var percent = (value!! - HEART_VALUE_MIX) / (HEART_VALUE_MAX - HEART_VALUE_MIX)
//        if (percent < 0) {
//            percent = 0.01
//        }
//        if (percent > 1) {
//            percent = 1.0
//        }
//        var brightness = (percent * 100).toInt()
//        yeelightManager.writeCmd(YeelightManager.CMD_BRIGHTNESS, mDeviceId, brightness.toString())
    }

    fun turnOn() {
        if (mDeviceId == null) {
            return
        }
        yeelightManager.writeCmd(YeelightManager.CMD_ON, mDeviceId)
    }

    fun turnOff() {
        if (mDeviceId == null) {
            return
        }
        yeelightManager.writeCmd(YeelightManager.CMD_OFF, mDeviceId)
    }

    fun setLightBrightness(lightBrightness: Int) {
        yeelightManager.writeCmd(
            YeelightManager.CMD_BRIGHTNESS,
            mDeviceId,
            lightBrightness.toString()
        )
    }

    fun startBrightnessAnim() {
        mainHandler.post {
            var valueAnimator = ValueAnimator.ofInt(30, 60)
            valueAnimator.interpolator = AccelerateDecelerateInterpolator()
            valueAnimator.addUpdateListener {
                var value = it.animatedValue as Int
                yeelightManager.writeCmd(
                    YeelightManager.CMD_BRIGHTNESS,
                    mDeviceId,
                    value.toString()
                )
            }
            valueAnimator.duration = 1000
            valueAnimator.repeatCount = INFINITE
            valueAnimator.repeatMode = REVERSE
            valueAnimator.start()
        }
    }

    fun smoothValue(value: Double, lastSmoothValue: Double, beta: Float = 0.7f): Double {
        return (1 - beta) * value + beta * lastSmoothValue
    }

    fun release() {
        yeelightManager.release()
    }
}