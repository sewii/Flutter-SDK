package io.agora.agora_rtc_engine

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.jiangdg.usbcamera.UVCCameraHelper
import com.jiangdg.usbcamera.utils.FileUtils
import com.serenegiant.usb.common.AbstractUVCCameraHandler
import com.serenegiant.usb.widget.CameraViewInterface
import com.serenegiant.usb.widget.UVCCameraTextureView

val PACKAGE_TAG = "UsbCamManager"

class UsbCamManager: CameraViewInterface.Callback  {
    interface UsbCamByteArrayListener {
        fun OnPreviewFrame(data: ByteArray)
    }


    companion object {
        private var instance: UsbCamManager = UsbCamManager()
        fun getInstance() : UsbCamManager {
            return instance
        }
    }

    val mCameraHelper: UVCCameraHelper = UVCCameraHelper.getInstance()
    var mUVCCameraView: CameraViewInterface? = null
    var mTextureView: UVCCameraTextureView? = null
    var isRequest: Boolean = false
    var isPreview: Boolean = false

    var usbCamByteArrayListener: UsbCamByteArrayListener? = null

    private val listener = object : UVCCameraHelper.OnMyDevConnectListener {
        override fun onAttachDev(device: UsbDevice) {
            Log.d(PACKAGE_TAG, "called onAttachDev")
            // request open permission
            if (!isRequest) {
                isRequest = true
                mCameraHelper?.requestPermission(0)
            }
        }

        override fun onDettachDev(device: UsbDevice) {
            Log.d(PACKAGE_TAG, "called onDettachDev")
            // close camera
            if (isRequest) {
                isRequest = false
                mCameraHelper?.closeCamera()
                Log.d(PACKAGE_TAG, device.deviceName + " is out")
            }
        }

        override fun onConnectDev(device: UsbDevice, isConnected: Boolean) {
            Log.d(PACKAGE_TAG, "called onConnectDev")
            if (!isConnected) {
                Log.d(PACKAGE_TAG, "fail to connect,please check resolution params")
                isPreview = false
            } else {
                isPreview = true
                Log.d(PACKAGE_TAG, "connecting")
            }
        }

        override fun onDisConnectDev(device: UsbDevice) {
            Log.d(PACKAGE_TAG, "called disconnecting")
        }
    }

    fun initTextureView(context: Context) {
        mTextureView = UVCCameraTextureView(context)
    }

    fun initializeAfterTextureViewInit(context: Context, activity: Activity) {
        unRegisterUSB()
        if (mTextureView == null) return

        mUVCCameraView = mTextureView as CameraViewInterface
        mUVCCameraView?.setCallback(this)
        mCameraHelper?.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG)
        mCameraHelper?.initUSBMonitor(activity, mUVCCameraView, listener)

        mCameraHelper?.setOnPreviewFrameListener(AbstractUVCCameraHandler.OnPreViewResultListener { nv21Yuv ->
            Log.d(PACKAGE_TAG, "called ️onPreviewResult: " + nv21Yuv.size)
            usbCamByteArrayListener?.OnPreviewFrame(nv21Yuv)

        })
        Log.d(PACKAGE_TAG, "finished ️initializeAfterTextureViewInit")
    }

    fun registerUSB() {
        // step.2 register USB event broadcast
        mCameraHelper?.registerUSB()
        Log.d(PACKAGE_TAG, "finished registerUSB")
    }

    fun unRegisterUSB() {
        mCameraHelper?.unregisterUSB()
        FileUtils.releaseFile()
        // step.4 release uvc camera resources
        mCameraHelper?.release()
        Log.d(PACKAGE_TAG, "finished unRegisterUSB")
    }

    // CameraViewInterface.Callback
    override fun onSurfaceCreated(view: CameraViewInterface, surface: Surface) {
//        if (!isPreview && mCameraHelper?.isCameraOpened() ?: false) {
//            mCameraHelper?.startPreview(mUVCCameraView)
//            isPreview = true
//        }
        Log.d(PACKAGE_TAG, "called onSurfaceCreated")
    }

    override fun onSurfaceChanged(view: CameraViewInterface, surface: Surface, width: Int, height: Int) {
        Log.d(PACKAGE_TAG, "called onSurfaceChanged")
    }

    override fun onSurfaceDestroy(view: CameraViewInterface, surface: Surface) {
//        if (isPreview && mCameraHelper?.isCameraOpened() ?: false) {
//            mCameraHelper?.stopPreview()
//            isPreview = false
//        }
        Log.d(PACKAGE_TAG, "called onSurfaceDestroy")
    }


}