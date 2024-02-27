@file:Suppress("DEPRECATION")

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
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.objetect.ml.AutoModel11
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    // List of weights corresponding to detected classes
    val weights = listOf(60, 9, 1900, 180, 41413, 11062, 4535924, 8000, 800)
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
    private val labelsSentMap: MutableMap<String, MutableSet<String>> = HashMap()
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private var isGpsTimestampAvailable: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Request necessary permissions
        get_permission()

        // Load labels from file
        labels = FileUtil.loadLabels(this, "labels.txt")
        // Configure image processing for TensorFlow Lite model
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
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

                // Process the captured frame using TensorFlow Lite model
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                // Get the detection outputs from the model
                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer
                // Create a mutable bitmap for drawing on canvas
                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width

                paint.textSize = h / 15f
                paint.strokeWidth = h / 95f

                var x: Int
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        Log.d("FirebaseData", "Approved Confidence")

                        // Try to get the weight for the detected class, handle IndexOutOfBoundsException
                        val weight: Int = try {
                            weights[classes[index].toInt()]
                        } catch (e: IndexOutOfBoundsException) {
                            // Handle the case where the class index is out of bounds
                            // Set a default value or take appropriate action
                            0
                        }
                        val label = labels[classes[index].toInt()]
                        // Check if the detected label is a vehicle (1-10)
                        if (classes[index].toInt() in listOf(2, 3, 5, 7)) {
                            Log.d("FirebaseData", "Inside the correct Labels")
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
                            if (!selectedLabels.contains(label)) {
                            l?.let {
                                selectedLabels.add(it)
                                //labelLocations.add(currentLocation)
                                Log.d("FirebaseData", "LABEL LIST Appended")
                            }}


                            // Get the current timestamp
                            val currentSecond = System.currentTimeMillis() / 1000
                            //val n=numberOfDetections.toString()
                            if (!labelsSentMap.containsKey(currentSecond.toString())) {
                                Log.d("FirebaseData", "Inside Map condition")

                                labelsSentMap[currentSecond.toString()] = HashSet()
                                locationManager =
                                    getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                // Check if GPS provider is enabled
                                /*val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                                if (!isGPSEnabled) {
                                    Log.d("FirebaseData", "GPS provider is not enabled")
                                    // Handle case where GPS provider is not enabled
                                }*/
                                //if(!isGpsTimestampAvailable) {
                                    val currentTimeMillis = System.currentTimeMillis()
                                    val dateFormat = SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm:ss",
                                        Locale.getDefault()
                                    ).format(currentTimeMillis)
                                    val formattedTime = dateFormat.toString()
                                    val a = (prevtime == formattedTime).toString()

                                    Log.d("FirebaseData", "Value of a: $a")
                                    if (a == "false") {
                                        // Log message before calling sendFirebaseData function
                                        Log.d("FirebaseData", "Calling sendFirebaseData function")

                                        // Send data to Firebase
                                        sendFirebaseData(
                                            selectedLabels,
                                            formattedTime, weight
                                        )
                                        prevtime = formattedTime
                                        Log.d("FirebaseData", "Previous Time:$prevtime")
                                        for (p in selectedLabels){
                                            Log.d("FirebaseData", "Label value:$p")
                                        }
                                        selectedLabels.clear()
                                    }

                                //}
                                object : LocationListener {
                                    override fun onLocationChanged(location: Location) {
                                        isGpsTimestampAvailable = true // Set to true when GPS timestamp is available
                                        // This method should be called when the location changes
                                        Log.d("FirebaseData", "Location changed: $location")
                                        // Use location.timestamp as the GPS timestamp
                                        val gpsTimestamp = location.time

                                        // Format the timestamp
                                        val formattedTime = SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss",
                                            Locale.getDefault()
                                        ).format(gpsTimestamp)
                                        /*val a = (prevtime == formattedTime).toString()
                                        Log.d("FirebaseData", "Value of a: $a")
                                        if (a == "false") {
                                            // Log message before calling sendFirebaseData function
                                            Log.d("FirebaseData", "Calling sendFirebaseData function")
                                            // Send data to Firebase
                                            sendFirebaseData(
                                                labelsSentMap[currentSecond.toString()]!!,
                                                formattedTime, weight,n
                                            )
                                            prevtime = formattedTime
                                        }*/
                                    }

                                    @Deprecated("Deprecated in Java", ReplaceWith(
                                        "Log.d(\"FirebaseData\", \"Provider status changed: \$provider, Status: \$status\")",
                                        "android.util.Log"
                                    )
                                    )
                                    override fun onStatusChanged(
                                        provider: String?,
                                        status: Int,
                                        extras: Bundle?
                                    ) {
                                        // This method is called when the provider status changes
                                        Log.d("FirebaseData", "Provider status changed: $provider, Status: $status")
                                    }

                                    override fun onProviderEnabled(provider: String) {
                                        // This method is called when the provider is enabled
                                        Log.d("FirebaseData", "Provider enabled: $provider")}

                                    override fun onProviderDisabled(provider: String) {
                                        // This method is called when the provider is disabled
                                        Log.d("FirebaseData", "Provider disabled: $provider")
                                    }
                                }.also { locationListener = it }

                                // Request location updates
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    try{
                                        locationManager.requestLocationUpdates(
                                            LocationManager.GPS_PROVIDER,
                                            1000,
                                            10f,
                                            locationListener
                                        )}
                                    catch (ex: SecurityException) {
                                        // Handle security exception
                                        Log.e("FirebaseData", "Security exception: ${ex.message}")
                                    }
                                }
                            }

                            if (!labelsSentMap[currentSecond.toString()]?.contains(label)!!) {
                                labelsSentMap[currentSecond.toString()]?.add(label)
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
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        // Open the camera and set up the preview
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
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
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION), 101)
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
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Request permissions again if not granted
                    get_permission()
                }
            }
        }
    }

    private fun sendFirebaseData(labels: List<String>, formattedTime: String, weight: Int) {
        // Initialize Firebase database reference
        Log.d("FirebaseData", "Sending data to Firebase...")
        database = FirebaseDatabase.getInstance().getReference("Labels")
        //val c=labels.size.toString()
        // Loop through the labels and send data to Firebase
        for (label in labels) {
            val key = database.child("all_labels").push().key
            // Create a data map
            val dataMap = HashMap<String, Any>()
            dataMap["label"] = label
            //dataMap["label"] = c
            dataMap["timestamp"] = formattedTime
            val w = labelWeights[label] ?: 0 // Default weight if label not found
            dataMap["weight"] = w
            //dataMap["weight"] = weight
            // Update the child with the new data
            database.child("all_labels").child(key ?: "").updateChildren(dataMap)
            Log.d("FirebaseData", "Data sent to Firebase for label: $labels")
        }
    }
}