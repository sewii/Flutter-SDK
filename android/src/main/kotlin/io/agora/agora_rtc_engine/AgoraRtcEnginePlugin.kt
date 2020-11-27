package io.agora.agora_rtc_engine

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.NonNull
import com.serenegiant.usb.USBMonitor
import com.serenegiant.utils.UIThreadHelper
import io.agora.rtc.RtcEngine
import io.agora.rtc.base.RtcEngineManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.plugin.platform.PlatformViewRegistry
import java.io.*
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod

/** AgoraRtcEnginePlugin */
class AgoraRtcEnginePlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var applicationContext: Context
    private var eventSink: EventChannel.EventSink? = null
    private val manager = RtcEngineManager { methodName, data -> emit(methodName, data) }
    private val handler = Handler(Looper.getMainLooper())
    private val rtcChannelPlugin = AgoraRtcChannelPlugin(this)
    private lateinit var activity: Activity

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            AgoraRtcEnginePlugin().apply {
                initPlugin(registrar.context(), registrar.messenger(), registrar.platformViewRegistry())
                rtcChannelPlugin.initPlugin(registrar.messenger())
            }
        }
    }

    private fun initPlugin(context: Context, binaryMessenger: BinaryMessenger, platformViewRegistry: PlatformViewRegistry) {
        applicationContext = context.applicationContext
        methodChannel = MethodChannel(binaryMessenger, "agora_rtc_engine")
        methodChannel.setMethodCallHandler(this)
        eventChannel = EventChannel(binaryMessenger, "agora_rtc_engine/events")
        eventChannel.setStreamHandler(this)

        platformViewRegistry.registerViewFactory("AgoraSurfaceView", AgoraSurfaceViewFactory(binaryMessenger, this, rtcChannelPlugin))
        platformViewRegistry.registerViewFactory("AgoraTextureView", AgoraTextureViewFactory(binaryMessenger, this, rtcChannelPlugin))
    }

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        rtcChannelPlugin.onAttachedToEngine(binding)
        initPlugin(binding.applicationContext, binding.binaryMessenger, binding.platformViewRegistry)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        rtcChannelPlugin.onDetachedFromEngine(binding)
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        manager.release()
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    private fun emit(methodName: String, data: Map<String, Any?>?) {
        handler.post {
            val event: MutableMap<String, Any?> = mutableMapOf("methodName" to methodName)
            data?.let { event.putAll(it) }
            eventSink?.success(event)
        }
    }

    fun engine(): RtcEngine? {
        return manager.engine
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        if (call.method == "initializeUsbCam") {
            startWriteLog()

            Log.d("", "called initializeUsbCam")
            showShortMsg("called initializeUsbCam")


//      val intent = Intent(context, UsbCameraTestActivity::class.java)
//      activity.startActivity(intent)

            UsbCamManager.getInstance().usbCamByteArrayListener = object : UsbCamManager.UsbCamByteArrayListener {
                override fun OnPreviewFrame(data: ByteArray) {
                    Log.d("", "-OnPreviewFrame: $data")
                    try {
                        UIThreadHelper.runOnUiThread {
                            methodChannel.invokeMethod("callbackUsbCamByteArray", data)
                        }
                    } catch (e: Exception) {
                        Log.d("", "error, Exception: $e")
                    }
                }
            }
            result.success("")
        }
        else if (call.method == "initTextureView") {

            UsbCamManager.getInstance().initTextureView(activity)

            // レイアウトの設定
            val layout = RelativeLayout(activity)
            // レイアウト中央寄せ
            layout.setGravity(Gravity.CENTER)

            // mTextureViewがContentViewに存在しないとエラーとなる為、ダミーで追加。
            // ただ追加するとMainActivityが起動されてしまう為どうするか検討要
            UIThreadHelper.runOnUiThread {
                activity.setContentView(layout)
                layout.addView(UsbCamManager.getInstance().mTextureView,
                        RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.FILL_PARENT,
                                RelativeLayout.LayoutParams.FILL_PARENT))
            }

        }
        else if (call.method == "configAfterInit") {
            UsbCamManager.getInstance().initializeAfterTextureViewInit(activity, activity)
            UsbCamManager.getInstance().registerUSB()
        }

        manager::class.declaredMemberFunctions.find { it.name == call.method }?.let { function ->
            function.javaMethod?.let { method ->
                try {
                    val parameters = mutableListOf<Any?>()
                    call.arguments<Map<*, *>>()?.toMutableMap()?.let {
                        if (call.method == "create") {
                            it["context"] = applicationContext
                        }
                        parameters.add(it)
                    }
                    method.invoke(manager, *parameters.toTypedArray(), ResultCallback(result))
                    return@onMethodCall
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        result.notImplemented()
    }

    fun test() {

    }

    private fun showShortMsg(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    private fun startWriteLog() {
        val wreiteLogThread = WriteLogThread(applicationContext)
        wreiteLogThread.start()
        Log.d("", "------ログ保存スタート")
        Log.d("", "context: ${applicationContext.toString()}, activity: ${applicationContext.toString()}")
    }

    override fun onDetachedFromActivity() {

    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {

    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {

    }
}

// デバッグ終了後削除
class WriteLogThread(private val context: Context) : Thread() {

    override
    fun run() {
        var proc: Process?
        var reader: BufferedReader? = null
        var writer: PrintWriter? = null

        val pId = Integer.toString(android.os.Process.myPid())

        try {
            proc = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time"))
            reader = BufferedReader(InputStreamReader(proc!!.getInputStream()), 1024)
            var line: String
            while (true) {
                line = reader!!.readLine()
                if (line.length == 0) {
                    try {
                        Thread.sleep(200)
                    } catch (e: InterruptedException) {
                    }

                    continue
                }

                if (line.indexOf(pId) !== -1) {

                    try {
                        val out: OutputStream
                        out = context.openFileOutput("log.text", Context.MODE_PRIVATE or Context.MODE_APPEND)
                        writer = PrintWriter(OutputStreamWriter(out, "UTF-8"))
                        writer!!.println(line)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (writer != null) {
                            writer!!.close()
                        }
                    }

                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (reader != null) {
                try {
                    reader!!.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }
}