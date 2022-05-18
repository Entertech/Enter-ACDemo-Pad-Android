package cn.entertech.flowtimelightcontroldemo

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cn.entertech.affectivecloudsdk.*
import cn.entertech.affectivecloudsdk.entity.Error
import cn.entertech.affectivecloudsdk.interfaces.Callback
import cn.entertech.ble.single.BiomoduleBleManager
import cn.entertech.flowtimelightcontroldemo.databinding.ActivityMainBinding
import cn.entertech.flowtimelightcontroldemo.utils.YeelightControlHelper
import cn.entertech.flowtimelightcontroldemo.utils.YeelightControlHelper.Companion.LIGHT_GREEN_HUE
import cn.entertech.flowtimelightcontroldemo.utils.YeelightControlHelper.Companion.LIGHT_OLIVE_HUE
import cn.entertech.flowtimelightcontroldemo.utils.YeelightControlHelper.Companion.LIGHT_ORANGE_HUE
import cn.entertech.flowtimelightcontroldemo.utils.YeelightControlHelper.Companion.LIGHT_RED_HUE
import cn.entertech.flowtimelightcontroldemo.utils.YeelightControlHelper.Companion.LIGHT_YELLOW_HUE
import cn.entertech.flowtimelightcontroldemo.view.EmotionIndicatorAppView
import cn.entertech.uicomponentsdk.realtime.PercentProgressBar.Companion.POWER_MODE_RATE
import com.bumptech.glide.Glide
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.ArrayList

class MainActivity : BaseActivity() {
    private var loading: AlertDialog? = null
    private val TAG = "MainActivity"
    private var bleManager: BiomoduleBleManager? = null
    private var enterAffectiveCloudManager: EnterAffectiveCloudManager? = null
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initFullScreenDisplay(false)
        initAffectiveCloudManager()
        initBleManager()
        initPermission()
        initView()
    }

    fun showLoading(message: String) {
        loading = AlertDialog.Builder(this).setMessage(message).create()
        loading?.show()
    }

    fun dismissLoading() {
        loading?.dismiss()
    }

    /**
     * Android6.0 auth
     */
    fun initPermission() {
        val needPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
        val needRequestPermissions = ArrayList<String>()
        for (i in needPermission.indices) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    needPermission[i]
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needRequestPermissions.add(needPermission[i])
            }
        }
        if (needRequestPermissions.size != 0) {
            val permissions = arrayOfNulls<String>(needRequestPermissions.size)
            for (i in needRequestPermissions.indices) {
                permissions[i] = needRequestPermissions[i]
            }
            ActivityCompat.requestPermissions(this@MainActivity, permissions, 1)
        }
    }

    fun initView() {
        initEmotionCardView()
        binding.ivDevice.setOnClickListener {
            showLoading("正在连接蓝牙...")
            bleManager?.scanNearDeviceAndConnect(fun() {

            }, fun(e) {
                runOnUiThread {
                    dismissLoading()
                    Toast.makeText(this@MainActivity, "设备未找到!", Toast.LENGTH_LONG).show()
                    binding.ivDevice.setImageResource(R.drawable.vector_drawable_ic_device_disconnect)
                }
            }, fun(mac) {
                runOnUiThread {
                    dismissLoading()
                    Toast.makeText(this@MainActivity, "设备连接成功!", Toast.LENGTH_LONG).show()
                    connectAffectiveCloud()
                }
            }, fun(error) {
                runOnUiThread {
                    dismissLoading()
                    Toast.makeText(this@MainActivity, "设备连接失败${error}!", Toast.LENGTH_LONG).show()
                    binding.ivDevice.setImageResource(R.drawable.vector_drawable_ic_device_disconnect)
                }
            })
        }
    }

    var eegDataListener = fun(eeg: ByteArray) {
        enterAffectiveCloudManager?.appendEEGData(eeg)
    }
    var hrDataListener = fun(hr: Int) {
        enterAffectiveCloudManager?.appendHeartRateData(hr)
    }
    var contactListener = fun(state: Int) {
        runOnUiThread {
            showDeviceContactState(state)
        }
    }

    fun showDeviceContactState(state: Int) {
        if (state == 0) {
            binding.viewDeviceContact.setBackgroundResource(R.drawable.shape_device_contact_bg_good)
        } else {
            binding.viewDeviceContact.setBackgroundResource(R.drawable.shape_device_contact_bg_bad)
        }
    }

    fun initBleManager() {
        bleManager = BiomoduleBleManager.getInstance(this)
        bleManager?.addRawDataListener(eegDataListener)
        bleManager?.addHeartRateListener(hrDataListener)
        bleManager?.addContactListener(contactListener)
    }


    var pleasure: Double? = 0.0
    var arousal: Double? = 0.0
    fun initAffectiveCloudManager() {
        var userId = "ACDemo${Random().nextInt(20)}"
        var availableAffectiveServices =
            listOf(
                cn.entertech.affectivecloudsdk.entity.Service.ATTENTION,
                cn.entertech.affectivecloudsdk.entity.Service.PRESSURE,
                cn.entertech.affectivecloudsdk.entity.Service.RELAXATION,
                cn.entertech.affectivecloudsdk.entity.Service.AROUSAL,
                cn.entertech.affectivecloudsdk.entity.Service.PLEASURE,
                cn.entertech.affectivecloudsdk.entity.Service.COHERENCE
            )
        var availableBioServices = listOf(
            cn.entertech.affectivecloudsdk.entity.Service.EEG,
            cn.entertech.affectivecloudsdk.entity.Service.HR
        )
        var biodataSubscribeParams = BiodataSubscribeParams.Builder()
            .requestEEG()
            .requestHR()
            .build()

        var biodataTolerance = BiodataTolerance.Builder().eeg(2).build()

        var affectiveSubscribeParams = AffectiveSubscribeParams.Builder()
            .requestAttention()
            .requestRelaxation()
            .requestPressure()
            .requestPleasure()
            .requestCoherence()
            .requestArousal()
            .build()
        var algorithmParamsEEG =
            AlgorithmParamsEEG.Builder()
                .filterMode(AlgorithmParams.FilterMode.HARD)
                .powerMode(AlgorithmParams.PowerMode.RATE)
                .build()
        var algorithmParams = AlgorithmParams.Builder().eeg(algorithmParamsEEG).build()
        var enterAffectiveCloudConfig = EnterAffectiveCloudConfig.Builder(
//            "015b7118-b81e-11e9-9ea1-8c8590cb54f9",
//            "cd9c757ae9a7b7e1cff01ee1bb4d4f98",
            "93e3cf84-dea1-11e9-ae15-0242ac120002",
            "c28e78f98f154962c52fcd3444d8116f",
            "$userId"
        )
            .url("wss://server.affectivecloud.cn/ws/algorithm/v2/")
            .timeout(10000)
            .availableBiodataServices(availableBioServices)
            .availableAffectiveServices(availableAffectiveServices)
            .biodataSubscribeParams(biodataSubscribeParams!!)
            .affectiveSubscribeParams(affectiveSubscribeParams!!)
            .biodataTolerance(biodataTolerance)
            .algorithmParams(algorithmParams)
            .uploadCycle(1)
            .build()
        enterAffectiveCloudManager = EnterAffectiveCloudManager(enterAffectiveCloudConfig)
        enterAffectiveCloudManager!!.addBiodataRealtimeListener {
            runOnUiThread {
                if (it?.realtimeEEGData != null) {
                    showBrainwaveSpectrum(
                        it.realtimeEEGData!!.alphaPower!!.toFloat(),
                        it.realtimeEEGData!!.betaPower!!.toFloat(),
                        it.realtimeEEGData!!.deltaPower!!.toFloat(),
                        it.realtimeEEGData!!.thetaPower!!.toFloat(),
                        it.realtimeEEGData!!.gammaPower!!.toFloat()
                    )
                    showBrainwave(it.realtimeEEGData!!.leftwave!!, it.realtimeEEGData!!.rightwave!!)
                }
                if (it?.realtimeHrData != null) {
                    showHeartRate(
                        it.realtimeHrData!!.hr!!.toInt(),
                        it.realtimeHrData!!.hrv!!.toInt()
                    )
                }
                if (it?.realtimeEEGData?.quality != null) {
                    showQuality(it.realtimeEEGData?.quality!!)
                }
            }
        }
        enterAffectiveCloudManager!!.addAffectiveDataRealtimeListener {
            runOnUiThread {
                if (it?.realtimePleasureData?.pleasure != null) {
                    pleasure = it?.realtimePleasureData?.pleasure
                }
                if (it?.realtimeArousalData?.arousal != null) {
                    arousal = it?.realtimeArousalData?.arousal
                }
                showArousalAndPleasureEmotion(arousal!!, pleasure!!)
                showAttention(it?.realtimeAttentionData?.attention)
                showRelaxation(it?.realtimeRelaxationData?.relaxation)
                showCoherence(it?.realtimeCoherenceData?.coherence)
                showPleasure(it?.realtimePleasureData?.pleasure)
                showPressure(it?.realtimePressureData?.pressure)
                showArousal(it?.realtimeArousalData?.arousal)
                showAffectiveLine(
                    it?.realtimePressureData?.pressure,
                    it?.realtimePleasureData?.pleasure,
                    it?.realtimeArousalData?.arousal,
                    it?.realtimeRelaxationData?.relaxation,
                    it?.realtimeAttentionData?.attention
                )
//                if (it?.realtimeCoherenceData?.coherence != null) {
//                    YeelightControlHelper.getInstance(this)
//                        .setCoherence(it.realtimeCoherenceData?.coherence!! * 1.2)
//                }
            }
        }

    }

    fun connectAffectiveCloud() {
        showLoading("正在连接情感云...")
        enterAffectiveCloudManager!!.init(object : Callback {
            override fun onError(error: Error?) {
                runOnUiThread {
                    dismissLoading()
                }
                try {
                    enterAffectiveCloudManager!!.restore(object : Callback {
                        override fun onError(error: Error?) {

                        }

                        override fun onSuccess() {
                        }

                    })
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "情感云连接失败：${error}", Toast.LENGTH_LONG)
                            .show()
                    }
                }
                Log.d(TAG, "情感云初始化失败：${error}")
            }

            override fun onSuccess() {
                Log.d(TAG, "情感云初始化成功")
                runOnUiThread {
                    dismissLoading()
                    Toast.makeText(this@MainActivity, "情感云连接成功!", Toast.LENGTH_LONG).show()
                    bleManager?.startHeartAndBrainCollection()
                    binding.ivDevice.setImageResource(R.mipmap.ic_settings)
                    YeelightControlHelper.getInstance(this@MainActivity).init(
                        fun(deviceId) {
                            Log.d(TAG, "light init success")
                            YeelightControlHelper.getInstance(this@MainActivity).setHue(0.0)
                        }, fun(error) {
                            Log.d(TAG, "light init fail :${error}")
                        })
                }

            }

        })
    }

    fun showQuality(quality: Double) {
        if (quality == 0.0 || quality == 1.0) {
            binding.ivQuality.setImageResource(R.drawable.vector_drawable_ic_quality_bad)
        } else if (quality == 2.0) {
            binding.ivQuality.setImageResource(R.drawable.vector_drawable_ic_quality_normal)
        } else {
            binding.ivQuality.setImageResource(R.drawable.vector_drawable_ic_quality_good)
        }
    }

    fun initEmotionCardView() {
        var attentionScale = arrayOf(0, 30, 70, 100)
        var relaxationScale = arrayOf(0, 30, 70, 100)
        var coherenceScale = arrayOf(0, 20, 40, 60, 80, 100)
        var stressScale = arrayOf(0, 20, 70, 100)
        var pleasureScale = arrayOf(0, 1, 2, 3, 4, 5)
        var arousalScale = arrayOf(0, 1, 2, 3, 4, 5)
        var attentionIndicatorItems = arrayListOf<EmotionIndicatorAppView.IndicateItem>()
        var relaxationIndicatorItems = arrayListOf<EmotionIndicatorAppView.IndicateItem>()
        var stressIndicatorItems = arrayListOf<EmotionIndicatorAppView.IndicateItem>()
        var pleasureIndicatorItems = arrayListOf<EmotionIndicatorAppView.IndicateItem>()
        var arousalIndicatorItems = arrayListOf<EmotionIndicatorAppView.IndicateItem>()
        var coherenceIndicatorItems = arrayListOf<EmotionIndicatorAppView.IndicateItem>()
        attentionIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#332D99FF")
            )
        )
        attentionIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.4f,
                Color.parseColor("#802D99FF")
            )
        )
        attentionIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#2D99FF")
            )
        )
        relaxationIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#3316CEB9")
            )
        )
        relaxationIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.4f,
                Color.parseColor("#8016CEB9")
            )
        )
        relaxationIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#16CEB9")
            )
        )

        coherenceIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.2f,
                Color.parseColor("#FF5722")
            )
        )
        coherenceIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.2f,
                Color.parseColor("#FF9F8A")
            )
        )
        coherenceIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.2f,
                Color.parseColor("#D0FFAB")
            )
        )
        coherenceIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.2f,
                Color.parseColor("#65FFA3")
            )
        )
        coherenceIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.2f,
                Color.parseColor("#4CAF50")
            )
        )
        stressIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.2f,
                Color.parseColor("#33cc5268")
            )
        )
        stressIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.5f,
                Color.parseColor("#80cc5268")
            )
        )
        stressIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#cc5268")
            )
        )
        pleasureIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#336648FF")
            )
        )
        pleasureIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.4f,
                Color.parseColor("#806648FF")
            )
        )
        pleasureIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#6648FF")
            )
        )
        arousalIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#33F7517F")
            )
        )
        arousalIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.4f,
                Color.parseColor("#80F7517F")
            )
        )
        arousalIndicatorItems.add(
            EmotionIndicatorAppView.IndicateItem(
                0.3f,
                Color.parseColor("#F7517F")
            )
        )
        binding.eivAttention.setScales(attentionScale)
        binding.eivAttention.setIndicatorItems(attentionIndicatorItems)
        binding.eivRelaxation.setScales(relaxationScale)
        binding.eivRelaxation.setIndicatorItems(relaxationIndicatorItems)
        binding.eivPressure.setScales(stressScale)
        binding.eivPressure.setIndicatorItems(stressIndicatorItems)
        binding.eivPleasure.setScales(pleasureScale)
        binding.eivPleasure.setIndicatorItems(pleasureIndicatorItems)
        binding.eivCoherence.setScales(coherenceScale)
        binding.eivCoherence.setIndicatorItems(coherenceIndicatorItems)
        binding.eivArousal.setScales(arousalScale)
        binding.eivArousal.setIndicatorItems(arousalIndicatorItems)
    }

    fun showAffectiveLine(
        coherence: Double?,
        pleasure: Double?,
        arousal: Double?,
        relaxation: Double?,
        attention: Double?
    ) {
        binding.realtimeAffective.appendData(0, coherence)
        binding.realtimeAffective.appendData(1, pleasure)
        binding.realtimeAffective.appendData(2, arousal)
        binding.realtimeAffective.appendData(3, relaxation)
        binding.realtimeAffective.appendData(4, attention)
    }

    fun showAttention(value: Double?) {
        if (value == null || value == 0.0) {
            return
        }
        var valueLevel = if (value >= 0 && value < 30) {
            getString(R.string.emotion_level_low)
        } else if (value >= 30 && value < 70) {
            getString(R.string.emotion_level_normal)
        } else {
            getString(R.string.emotion_level_high)
        }
        binding.tvAttentionLevel.text = valueLevel
        binding.eivAttention.setValue(value.toFloat())
        binding.tvAttentionValue.text = "${value.toInt()}"
    }

    fun showRelaxation(value: Double?) {
        if (value == null || value == 0.0) {
            return
        }
        var valueLevel = if (value >= 0 && value < 30) {
            getString(R.string.emotion_level_low)
        } else if (value >= 30 && value < 70) {
            getString(R.string.emotion_level_normal)
        } else {
            getString(R.string.emotion_level_high)
        }
        binding.tvRelaxationLevel.text = valueLevel
        binding.eivRelaxation.setValue(value.toFloat())
        binding.tvRelaxationValue.text = "${value.toInt()}"
    }

    var coherenceBuffer = CopyOnWriteArrayList<Double>()
    fun showCoherence(value: Double?) {
        coherenceBuffer.add(value)
        if (coherenceBuffer.size >= 5) {
            val coherence = coherenceBuffer[0]
            if (coherence != null && coherence != 0.0) {
                binding.eivCoherence.setValue(coherence.toFloat())
                binding.tvCoherenceValue.text = "${coherence.toInt()}"
                controlLight(coherence)
            }
//        var valueLevel = ""
//        if (value >= 0 && value < 25) {
//            valueLevel = "一般"
//        } else if (value >= 25 && value < 60) {
//            valueLevel = "较和谐"
//        } else {
//            valueLevel = "和谐"
//        }
//        binding.tvCoherenceLevel.text = valueLevel
            coherenceBuffer.clear()
        }
    }

    fun controlLight(coherence: Double) {
        val hue =
            YeelightControlHelper.getInstance(this).reflectCoherenceToHue(coherence)
        YeelightControlHelper.getInstance(this).setHue(hue)
        when (hue) {
            LIGHT_RED_HUE -> {
                binding.ivLightBg.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.light_color_red
                    )
                )
                binding.ivCoherenceLight.setImageResource(R.drawable.vector_drawable_light_red)
                binding.tvCoherenceLevel.text = "较低"
            }
            LIGHT_ORANGE_HUE -> {
                binding.ivLightBg.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.light_color_orange
                    )
                )
                binding.ivCoherenceLight.setImageResource(R.drawable.vector_drawable_light_orange)
                binding.tvCoherenceLevel.text = "偏低"
            }
            LIGHT_YELLOW_HUE -> {
                binding.ivLightBg.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.light_color_yellow
                    )
                )
                binding.ivCoherenceLight.setImageResource(R.drawable.vector_drawable_light_yellow)
                binding.tvCoherenceLevel.text = "一般"
            }
            LIGHT_OLIVE_HUE -> {
                binding.ivLightBg.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.light_color_olive
                    )
                )
                binding.ivCoherenceLight.setImageResource(R.drawable.vector_drawable_light_olive)
                binding.tvCoherenceLevel.text = "较和谐"
            }
            LIGHT_GREEN_HUE -> {
                binding.ivLightBg.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.light_color_green
                    )
                )
                binding.ivCoherenceLight.setImageResource(R.drawable.vector_drawable_light_green)
                binding.tvCoherenceLevel.text = "和谐"
            }
        }
        coherenceBuffer.clear()
    }


    fun showPressure(value: Double?) {
        if (value == null || value == 0.0) {
            return
        }

        var valueLevel = if (value >= 0 && value < 20) {
            getString(R.string.emotion_level_low)
        } else if (value >= 20 && value < 70) {
            getString(R.string.emotion_level_normal)
        } else {
            getString(R.string.emotion_level_high)
        }
        binding.tvPressureLevel.text = valueLevel
        binding.eivPressure.setValue(value.toFloat())
        binding.tvPressureValue.text = "${value.toInt()}"
    }

    fun showPleasure(value: Double?) {
        if (value == null || value == 0.0) {
            return
        }

        var valueLevel = if (value >= 0 && value < 30) {
            getString(R.string.emotion_level_low)
        } else if (value >= 30 && value < 70) {
            getString(R.string.emotion_level_normal)
        } else {
            getString(R.string.emotion_level_high)
        }
        var convertValue = value / 20
        var formatValueString = String.format("%.1f", convertValue)
        binding.tvPleasureLevel.text = valueLevel
        binding.eivPleasure.setValue(convertValue.toFloat())
        binding.tvPleasureValue.text = formatValueString
    }

    fun showArousal(value: Double?) {
        if (value == null || value == 0.0) {
            return
        }

        var valueLevel = if (value >= 0 && value < 30) {
            getString(R.string.emotion_level_low)
        } else if (value >= 30 && value < 70) {
            getString(R.string.emotion_level_normal)
        } else {
            getString(R.string.emotion_level_high)
        }
        var convertValue = value / 20
        var formatValueString = String.format("%.1f", convertValue)
        binding.tvArousalLevel.text = valueLevel
        binding.eivArousal.setValue(convertValue.toFloat())
        binding.tvArousalValue.text = formatValueString
    }

    fun showBrainwave(leftWave: ArrayList<Double>, rightWave: ArrayList<Double>) {
        binding.bsvBrainwaveLeft.setData(leftWave)
        binding.bsvBrainwaveRight.setData(rightWave)
    }

    fun showHeartRate(hr: Int, hrv: Int) {
        if (hr != 0) {
            binding.tvHr.text = "${hr}"
        }
        if (hrv != 0) {
            binding.tvHrv.text = "HRV:${hrv}ms"
        }
    }

    fun showBrainwaveSpectrum(alpha: Float, beta: Float, delta: Float, theta: Float, gamma: Float) {
        binding.realtimeBrainwaveSpectrum.setPowerMode(POWER_MODE_RATE)
        binding.realtimeBrainwaveSpectrum.setAlphaWavePercent(alpha)
        binding.realtimeBrainwaveSpectrum.setBetaWavePercent(beta)
        binding.realtimeBrainwaveSpectrum.setDeltaWavePercent(delta)
        binding.realtimeBrainwaveSpectrum.setThetaWavePercent(theta)
        binding.realtimeBrainwaveSpectrum.setGammaWavePercent(gamma)
    }


    private fun showArousalAndPleasureEmotion(arousal: Double, pleasure: Double) {
        binding.realtimeArousalPleasure.setArousal(arousal)
        binding.realtimeArousalPleasure.setPleasure(pleasure)
        var targetView = binding.ivEmotion
        if (pleasure in 0.0..22.0 && arousal in 75.0..100.0) {
            Glide.with(this).load(R.mipmap.p0_22a75_100).into(targetView!!)
        } else if (pleasure in 0.0..40.0 && arousal in 0.0..25.0) {
            Glide.with(this).load(R.mipmap.p0_40a0_25).into(targetView!!)
        } else if (pleasure in 0.0..40.0 && arousal in 25.0..50.0) {
            Glide.with(this).load(R.mipmap.p0_40a25_50).into(targetView!!)
        } else if (pleasure in 0.0..45.0 && arousal in 50.0..75.0) {
            Glide.with(this).load(R.mipmap.p0_45a50_75).into(targetView!!)
        } else if (pleasure in 22.0..45.0 && arousal in 75.0..100.0) {
            Glide.with(this).load(R.mipmap.p22_45a75_100).into(targetView!!)
        } else if (pleasure in 40.0..60.0 && arousal in 0.0..25.0) {
            Glide.with(this).load(R.mipmap.p40_60a0_25).into(targetView!!)
        } else if (pleasure in 45.0..55.0 && arousal in 75.0..100.0) {
            Glide.with(this).load(R.mipmap.p45_55a75_100).into(targetView!!)
        } else if (pleasure in 55.0..78.0 && arousal in 75.0..100.0) {
            Glide.with(this).load(R.mipmap.p55_78a75_100).into(targetView!!)
        } else if (pleasure in 60.0..100.0 && arousal in 0.0..25.0) {
            Glide.with(this).load(R.mipmap.p60_100a0_25).into(targetView!!)
        } else if (pleasure in 60.0..100.0 && arousal in 25.0..50.0) {
            Glide.with(this).load(R.mipmap.p60_100a25_50).into(targetView!!)
        } else if (pleasure in 75.0..100.0 && arousal in 50.0..75.0) {
            Glide.with(this).load(R.mipmap.p75_100a50_75).into(targetView!!)
        } else if (pleasure in 78.0..100.0 && arousal in 75.0..100.0) {
            Glide.with(this).load(R.mipmap.p78_100a75_100).into(targetView!!)
        } else {
            Glide.with(this).load(R.mipmap.pic_arousal_pleasure_emotion_else).into(targetView!!)
        }
    }


    override fun onDestroy() {
        bleManager?.removeHeartRateListener(hrDataListener)
        bleManager?.removeRawDataListener(eegDataListener)
        bleManager?.removeContactListener(contactListener)
        enterAffectiveCloudManager?.release(object : Callback {
            override fun onError(error: Error?) {

            }

            override fun onSuccess() {
            }

        })
        super.onDestroy()
    }
}