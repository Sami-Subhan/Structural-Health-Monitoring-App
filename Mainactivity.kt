package com.example.objetect

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.objetect.ml.AutoModel11
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    fun closeApp(view: View) {
        finish() // Close the activity (and the app)
    }

    private val labelWeights = mapOf(
        "car" to 1900,
        "motorcycle" to 180,
        "truck" to 8000,
        "bus" to 11062
    )
    var colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    val paint = Paint()
    private lateinit var database: DatabaseReference
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: AutoModel11
    lateinit var labels: List<String>
    private val selectedLabels: MutableList<String> = mutableListOf()
    private val labelLocations: MutableList<List<Float>> = mutableListOf()
    var prevtime: String? =null
    private lateinit var gpsTimestampProvider: GpsTimestampProvider
    var prevgpstime: String?=null
    private var gpsTimestamp: Long = 0
    var n: Int?=null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Request necessary permissions
        get_permission()

        // Keep the screen on while this activity is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize GpsTimestampProvider
        gpsTimestampProvider = GpsTimestampProvider(this)
        gpsTimestampProvider.startGpsUpdates()

        // Load labels from file
        labels = FileUtil.loadLabels(this, "labels.txt")
        // Configure image processing for TensorFlow Lite model
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300,
            ResizeOp.ResizeMethod.BILINEAR)).build()
        // Initialize the TensorFlow Lite model
        model = AutoModel11.newInstance(this)
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        imageView = findViewById(R.id.imageView)

        textureView = findViewById(R.id.textureView)
        // Configure the SurfaceTextureListener for TextureView
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                // Open the camera when the SurfaceTexture is available
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            @SuppressLint("MissingPermission")
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                // Capture the updated frame from TextureView
                bitmap = textureView.bitmap!!

                gpsTimestamp = gpsTimestampProvider.getGpsTimestamp()

                // Process the captured frame using TensorFlow Lite model
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                // Get the detection outputs from the model
                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                // Create a mutable bitmap for drawing on canvas
                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width

                paint.textSize = h / 15f
                paint.strokeWidth = h / 95f

                n=scores.size

                labelLocations.clear()

                var x: Int
                var i=0
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {

                        val label = labels[classes[index].toInt()]
                        // Check if the detected label is a vehicle (1-10)
                        if (classes[index].toInt() in listOf(2, 3, 5, 7)) {
                            i+=1

                            // Set paint color and style for drawing bounding box
                            paint.color = colors[index]
                            paint.style = Paint.Style.STROKE
                            canvas.drawRect(
                                RectF(
                                    /* left = */ locations[x + 1] * w,
                                    /* top = */ locations[x] * h,
                                    /* right = */ locations[x + 3] * w,
                                    /* bottom = */ locations[x + 2] * h
                                ), paint
                            )
                            paint.style = Paint.Style.FILL
                            // Draw the label on the canvas
                            canvas.drawText(
                                label,
                                locations[x + 1] * w,
                                locations[x] * h,
                                paint
                            )
                            val currentLocation = listOf(
                                locations[x + 1] * w,
                                locations[x] * h,
                                locations[x + 3] * w,
                                locations[x + 2] * h
                            )
                            val l = labels.getOrNull(classes[index].toInt())
                            // If label is not null, add it to the selectedLabels list
                            if (!selectedLabels.contains(label) || i > selectedLabels.size){
                                l?.let {
                                    selectedLabels.add(it)
                                    labelLocations.add(currentLocation)
                                }}

                            val currentTimeMillis = System.currentTimeMillis()
                            val dateFormat = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                            ).format(currentTimeMillis)
                            val formattedcurrentTime = dateFormat.toString()
                            val a = (prevtime == formattedcurrentTime).toString()

                            if (a == "false") {
                                // Send data to Firebase
                                sendFirebaseData(
                                    selectedLabels,
                                    formattedcurrentTime
                                )
                                prevtime = formattedcurrentTime
                                selectedLabels.clear()
                            }
                        }
                    }
                }
                // Update the ImageView with the drawn bitmap
                imageView.setImageBitmap(mutable)
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onDestroy() {
        super.onDestroy()
        // Close the TensorFlow Lite model
        model.close()
        // Stop GPS updates when activity is destroyed
        gpsTimestampProvider.stopGpsUpdates()
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        // Open the camera and set up the preview
        cameraManager.openCamera(cameraManager.cameraIdList[0],
            object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object :
                    CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                        // Handle configuration failure
                    }
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {
                // Handle camera disconnection
            }

            override fun onError(p0: CameraDevice, p1: Int) {
                // Handle camera error
            }
        }, handler)
    }

    private fun get_permission() {
        // Request camera and location permissions
        if (ContextCompat.checkSelfPermission(
                /* context = */ this,
                /* permission = */ Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Request permissions again if not granted
                    get_permission()
                }
            }
        }
    }
    private fun sendFirebaseData(labels: List<String>, LocalTime: String, ) {
        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance().getReference("SHM")
        // Get GPS timestamp from GpsTimestampProvider
        val dateFormat = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(gpsTimestamp)
        val formattedTime = dateFormat.toString()

        var sentime : String?=null

        var timetosent : String

        // Convert the labels list to a JSON string
        val labelsJson = Gson().toJson(labels)

        if (gpsTimestamp!=0L) {

            // Split the prevtime string to extract the date and time parts
            val prevDateTimeParts = prevtime?.split(" ")
            // Extract the time part
            val prevTime1 = prevDateTimeParts?.get(1)
            // Split the time part to extract the hours, minutes, and seconds parts
            val prevTimeParts = prevTime1?.split(":")
            // Extract the hours, minutes, and seconds parts and convert them to integers
            val prevHour = prevTimeParts?.get(0)?.toInt()
            val prevMin = prevTimeParts?.get(1)?.toInt()
            val prevSec = prevTimeParts?.get(2)?.toInt()

            // Split the formattedTime string to extract the date and time parts
            val formattedDateTimeParts = formattedTime?.split(" ")
            // Extract the time part
            val formattedTime1 = formattedDateTimeParts?.get(1)
            // Split the time part to extract the hours, minutes, and seconds parts
            val formattedTimeParts = formattedTime1?.split(":")
            // Extract the hours, minutes, and seconds parts and convert them to integers
            val forHour = formattedTimeParts?.get(0)?.toInt()
            val forMin = formattedTimeParts?.get(1)?.toInt()
            val forSec = formattedTimeParts?.get(2)?.toInt()

            // Compare the parts
            if (((forSec ?: 0) < (prevSec ?: 0) && (forMin ?: 0) <= (prevMin ?: 0) &&
                        (forHour ?: 0) <= (prevHour ?: 0)) ||
                    ((forMin ?: 0) < (prevMin ?: 0) && (forHour ?: 0) <= (prevHour ?: 0)) ||
                    ((forHour ?: 0) < (prevHour ?: 0)))
            {
                sentime = formattedTime
            }

        }

        if (gpsTimestamp != 0L && prevgpstime != formattedTime &&
            prevtime != formattedTime && sentime!=formattedTime) {

            timetosent = formattedTime
            prevgpstime = formattedTime
        }

        else {
            timetosent = LocalTime
        }

        // Calculate total weight
        var totalWeight = 0
        for (label in labels) {
            val weight = labelWeights[label] ?: 0 // Default weight if label not found
            totalWeight += weight
        }

        // Create a data map to store all the values
        val dataMap = HashMap<String, Any>()

        // Populate the data map with other values (e.g., labels, timestamp, total weight)
        dataMap["labels"] = labelsJson
        dataMap["timestamp"] = timetosent
        dataMap["total_weight"] = totalWeight

        // Update the child with the new data
        val key = database.child("Load").push().key ?: ""
        database.child("Load").child(key).updateChildren(dataMap)
    }
}
class GpsTimestampProvider(private val context: Context) : LocationListener {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var latestGpsTimestamp: Long = 0

    init {
        startGpsUpdates()
    }

    fun getGpsTimestamp(): Long {
        return latestGpsTimestamp
    }

    fun startGpsUpdates() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,
                10f,
                this
            )
        }
    }

    fun stopGpsUpdates() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        latestGpsTimestamp = location.time
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}
}