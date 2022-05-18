package cn.entertech.flowtimelightcontroldemo.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

class YeelightManager(var context: Context) {
    private var handler: Handler
    private var handlerThread: HandlerThread
    private val UDP_HOST = "239.255.255.250"
    private val UDP_PORT = 1982
    private var mDSocket: DatagramSocket? = null
    private var mSocket: Socket? = null
    private var multicastLock: MulticastLock? = null
    private val tag = "yeelight"
    private var mSearching = false
    private var mIsConnected = false
    private var mBos: BufferedOutputStream? = null

    private var mReader: BufferedReader? = null
    var mDeviceList: ArrayList<HashMap<String, String>> =
        ArrayList()

    init {
        var wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock(tag)
        multicastLock?.acquire()
        handlerThread = HandlerThread(tag)
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    companion object {
        var CMD_SEARCH = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST:239.255.255.250:1982\r\n" +
                "MAN:\"ssdp:discover\"\r\n" +
                "ST:wifi_bulb\r\n" //用于发送的字符串
        var CMD_TOGGLE = "{\"id\":%id,\"method\":\"toggle\",\"params\":[]}\r\n"
        var CMD_ON =
            "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"on\",\"smooth\",500]}\r\n"
        var CMD_OFF =
            "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"off\",\"smooth\",500]}\r\n"
        var CMD_CT =
            "{\"id\":%id,\"method\":\"set_ct_abx\",\"params\":[%value, \"smooth\", 500]}\r\n"
        var CMD_HSV =
            "{\"id\":%id,\"method\":\"set_hsv\",\"params\":[%value, 100, \"smooth\", 200]}\r\n"
        var CMD_BRIGHTNESS =
            "{\"id\":%id,\"method\":\"set_bright\",\"params\":[%value, \"sudden\", 1]}\r\n"
        var CMD_BRIGHTNESS_SCENE =
            "{\"id\":%id,\"method\":\"set_bright\",\"params\":[%value, \"smooth\", 500]}\r\n"
        var CMD_COLOR_SCENE =
            "{\"id\":%id,\"method\":\"set_scene\",\"params\":[\"cf\",1,0,\"100,1,%color,1\"]}\r\n"

        @Volatile
        var mInstance: YeelightManager? = null
        fun getInstance(context: Context): YeelightManager {
            if (mInstance == null) {
                synchronized(YeelightManager::class.java) {
                    if (mInstance == null) {
                        mInstance = YeelightManager(context)
                    }
                }
            }
            return mInstance!!
        }
    }

    var searchStartTime: Long? = null
    var searchEndTime: Long? = null
    fun searchDevices(
        cmd: String = CMD_SEARCH,
        success: (List<HashMap<String, String>>) -> Unit,
        failure: (String) -> Unit
    ) {
        mSearching = true
        mDeviceList.clear()
        Log.d(tag, "0000000000")
        Thread {
            try {
                mDSocket = DatagramSocket()
                val dpSend = DatagramPacket(
                    cmd.toByteArray(),
                    cmd.toByteArray().size,
                    InetAddress.getByName(UDP_HOST),
                    UDP_PORT
                )
                mDSocket!!.send(dpSend)
                Log.d(tag, "11111111111")
                Timer().schedule(2000) {
                    mSearching = false
                    if (mDeviceList.size > 0) {
                        Log.d(tag, "444444444444444")
                        success.invoke(mDeviceList)
                    } else {
                        Log.d(tag, "5555555555555")
                        failure.invoke("timeout")
                    }
                }
                while (mSearching) {
                    val buf = ByteArray(1024)
                    val dpRecv = DatagramPacket(buf, buf.size)
                    mDSocket!!.receive(dpRecv)
                    val bytes = dpRecv.data
                    val buffer = StringBuffer()
                    for (i in 0 until dpRecv.length) {
                        // parse /r
                        if (bytes[i].toInt() == 13) {
                            continue
                        }
                        buffer.append(bytes[i].toChar())
                    }
                    Log.d("socket", "got message:$buffer")
                    if (buffer.toString().contains("yeelight")) {
                        val infos =
                            buffer.toString().split("\n".toRegex()).toTypedArray()
                        val bulbInfo =
                            HashMap<String, String>()
                        for (str in infos) {
                            val index = str.indexOf(":")
                            if (index == -1) {
                                continue
                            }
                            val title = str.substring(0, index)
                            val value = str.substring(index + 1)
                            bulbInfo[title] = value
                        }
                        if (!hasAdd(bulbInfo)) {
                            mDeviceList.add(bulbInfo)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "66666666666666666")
                failure.invoke(e.toString())
                e.printStackTrace()
            }
        }.start()
    }


    fun connect(
        device: HashMap<String, String>,
        success: ((Int) -> Unit)? = null,
        failure: ((String) -> Unit)? = null
    ) {

        Log.d(tag, "connect 000000000000")
        if (device["id"] == null) {
            Log.d(tag, "connect 1111111111")
            failure?.invoke("id not found")
            return
        }
        Thread {
            try {
                Log.d(tag, "connect 22222222222")
                val ipinfo: String =
                    device["Location"]?.split("//".toRegex())?.toTypedArray()?.get(1)?:""
                val ip = ipinfo.split(":".toRegex()).toTypedArray()[0]
                val port = ipinfo.split(":".toRegex()).toTypedArray()[1]
                mSocket = Socket(ip, Integer.parseInt(port))
                mSocket!!.keepAlive = true
                mBos = BufferedOutputStream(mSocket!!.getOutputStream())
                mReader =
                    BufferedReader(InputStreamReader(mSocket!!.getInputStream()))
                mIsConnected = true
                Log.d(tag, "connect 33333333333")
                success?.invoke(Integer.parseInt(device["id"]!!.split("0x")[1], 16))
                while (mIsConnected) {
                    try {
                        Log.d(tag, "connect 4444444444")
                        val value: String = mReader!!.readLine()
                        Log.d(tag, "value = $value")
                    } catch (e: java.lang.Exception) {
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.d(tag, "connect 5555555555555")
                mIsConnected = false
                failure?.invoke(e.toString())
                e.printStackTrace()
            }
        }.start()
    }


    fun isConnected(): Boolean {
        return mIsConnected
    }

    fun writeCmd(cmd: String, id: Int?, value: String? = null) {
        if (id == null) {
            throw IllegalArgumentException("device id can not be null")
        }
        var realCmd = if (value == null) {
            cmd.replace("%id", id.toString())
        } else {
            cmd.replace("%id", id.toString()).replace("%value", value)
        }
        Thread {
            if (mBos != null && mSocket!!.isConnected) {
                try {
                    mBos!!.write(realCmd.toByteArray())
                    mBos!!.flush()
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            } else {
                Log.d(tag, "mBos = null or mSocket is closed")
            }
        }.start()
    }

    fun release() {
        mSearching = false
        mIsConnected = false
        mSocket?.close()
        multicastLock?.release()
    }


    private fun hasAdd(bulbinfo: HashMap<String, String>): Boolean {
        for (info in mDeviceList) {
            Log.d(tag, "location params = " + bulbinfo["Location"])
            if (info["Location"] == bulbinfo["Location"]) {
                return true
            }
        }
        return false
    }
}