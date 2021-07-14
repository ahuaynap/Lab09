package com.example.lab09

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.*
import java.nio.ByteBuffer
import java.util.*


class MainActivity : AppCompatActivity() {
    lateinit var btnCamera: Button
    lateinit var cameraTextureView: TextureView
    lateinit var cameraDevice: CameraDevice
    lateinit var cameraCaptureSession: CameraCaptureSession
    val REQUEST_CAMERA_PERMISSION = 200
    lateinit var imageDimension: Size
    lateinit var captureRequestBuilder: CaptureRequest.Builder
    lateinit var cameraId: String
    lateinit var backgroudHandler: Handler
    lateinit var backgroundThread: HandlerThread
    lateinit var imageView: ImageView

    var stateCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice.close()
            Log.e("ERR", "STATECALLBACK")
        }

    }

    var textureListener = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
            ) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false

            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }
        }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraTextureView = findViewById(R.id.textureView)
        imageView = findViewById(R.id.imageView)
        cameraTextureView.surfaceTextureListener = textureListener
        btnCamera = findViewById(R.id.btnCamera)
        btnCamera.setOnClickListener{
            takePicture()
        }

    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun takePicture(){
        if (cameraDevice == null) return
        val cameraManager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)
            var jpegSizes: Array<Size?>? = null
            if (characteristics != null) jpegSizes = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!.getOutputSizes(ImageFormat.JPEG)
            var width = 640
            var height = 480
            if(jpegSizes != null && jpegSizes.isNotEmpty())
            {
                width = jpegSizes[0]!!.width;
                height = jpegSizes[0]!!.height;
            }
            val reader: ImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurface: MutableList<Surface> = ArrayList(2)
            outputSurface.add(reader.surface)
            outputSurface.add(Surface(cameraTextureView.surfaceTexture))

            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            val readerListener: OnImageAvailableListener = object : OnImageAvailableListener {
                override fun onImageAvailable(imageReader: ImageReader) {
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        //Aca deberia añadirse image a imageView
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        run { image?.close() }
                    }
                }

            }


        } catch (e: Exception){
            Log.e(TAG, "Error", e)
        }

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun openCamera(){
        val cameraManager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]
        val cameraCharacteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(
                cameraId
        )
        val streamConfigurationMap: StreamConfigurationMap? = cameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        imageDimension = streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)[0]
        if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this, arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), REQUEST_CAMERA_PERMISSION
            )
            return
        }
        cameraManager.openCamera(cameraId, stateCallback, null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun createCameraPreview(){
        val surfaceTexture = cameraTextureView.surfaceTexture as SurfaceTexture;
        surfaceTexture.setDefaultBufferSize(imageDimension.width, imageDimension.height)
        val surface = Surface(surfaceTexture)
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)
        cameraDevice.createCaptureSession(
                Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null)
                            return
                        cameraCaptureSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("ERR", "CONFIG")
                    }

                },
                null
        )
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updatePreview(){
        if(cameraDevice == null)
            return
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSession.setRepeatingRequest(
                    captureRequestBuilder.build(),
                    null,
                    backgroudHandler
            )
        }catch (e: Exception){
            Log.e("ERR", e.toString())

        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onPause() {
        stopBackgroundThread()
        super.onPause()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if(cameraTextureView.isAvailable)
            openCamera()
        else
            cameraTextureView.surfaceTextureListener = textureListener
    }

    fun startBackgroundThread(){
        backgroundThread = HandlerThread("Camera")
        backgroundThread.start()
        backgroudHandler = Handler(backgroundThread.looper)
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        }catch (e: Exception){
            Log.e("ERR", "THREAD")
        }
    }
}