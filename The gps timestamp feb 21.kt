object : LocationListener {
                                    override fun onLocationChanged(location: Location) {
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