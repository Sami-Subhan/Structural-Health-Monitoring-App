{
        // Initialize Firebase database reference
        Log.d("FirebaseData", "Sending data to Firebase...")
        database = FirebaseDatabase.getInstance().getReference("Labels")
        //val c=labels.size.toString()
        // Loop through the labels and send data to Firebase
        //for (label in labels) {
            val key = database.child("all_labels").push().key
            // Create a data map
            val dataMap = HashMap<String, Any>()
            dataMap["label"] = labels
            //dataMap["label"] = c
            dataMap["timestamp"] = formattedTime
            dataMap["weight"] = weight
            // Update the child with the new data
            database.child("all_labels").child(key ?: "").updateChildren(dataMap)
            Log.d("FirebaseData", "Data sent to Firebase for label: $labels")
        //}
    }