package com.example.locationtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.dynamiclinks.ktx.androidParameters
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.ktx.Firebase


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocClient: FusedLocationProviderClient
    private var locationId = "dubai"
    private var userLocation = ""

    private var database: FirebaseDatabase = FirebaseDatabase.getInstance()



    private lateinit var find_location_btn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        find_location_btn = findViewById(R.id.btn_find_location)

        setupLocClient()



        Firebase.dynamicLinks.getDynamicLink(intent).addOnSuccessListener {
            if (it != null){
                userLocation = it.link?.getQueryParameter("locationId").toString()
                Toast.makeText(this, "" + userLocation, Toast.LENGTH_SHORT).show()

                val dbReference: DatabaseReference = Firebase.database.reference

                dbReference.addValueEventListener(locationListener)

            }
        }
    }



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        getCurrentLocation()
    }

    private fun setupLocClient() {
        fusedLocClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun requestLocPermissions() {

        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION), //permission in the manifest
            REQUEST_LOCATION)
    }

    companion object {
        private const val REQUEST_LOCATION = 1 //request code to identify specific permission request
        private const val TAG = "MapsActivity" // for debugging
    }

    private fun getCurrentLocation() {
        // Check if the ACCESS_FINE_LOCATION permission was granted before requesting a location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // call requestLocPermissions() if permission isn't granted
            requestLocPermissions()
        } else {

            fusedLocClient.lastLocation.addOnCompleteListener {
                // lastLocation is a task running in the background
                val location = it.result //obtain location
                //reference to the database

                val database: FirebaseDatabase = FirebaseDatabase.getInstance()
                val ref: DatabaseReference = database.getReference(locationId)

                if (location != null) {

                    val latLng = LatLng(location.latitude, location.longitude)
                    // create a marker at the exact location
                    mMap.addMarker(MarkerOptions().position(latLng)
                        .title("You are currently here"))
                    // create an object that will specify how the camera will be updated
                    val update = CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)

                    mMap.moveCamera(update)
                    //Save the location data to the database

                    runOnUiThread {

                        ref.setValue(location)

                    }

                    find_location_btn.setOnClickListener {



                        onLocationShare(ref.key)

                    }

                } else {
                    // if location is null , log an error message
                    Log.e(TAG, "No location found")
                }

            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //check if the request code matches the REQUEST_LOCATION
        if (requestCode == REQUEST_LOCATION)
        {
            //check if grantResults contains PERMISSION_GRANTED.If it does, call getCurrentLocation()
            if (grantResults.size == 1 && grantResults[0] ==
                PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                //if it doesn`t log an error message
                Log.e(TAG, "Location permission has been denied")
            }
        }
    }

    private val locationListener = object : ValueEventListener {
        //     @SuppressLint("LongLogTag")
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.exists()){
                val location = snapshot.child(userLocation).getValue(LocationInfo::class.java)
                val locationLat = location?.latitude
                val locationLong = location?.longitude

                runOnUiThread {

                    Toast.makeText(applicationContext, "" +  location?.latitude, Toast.LENGTH_LONG).show()
                    Toast.makeText(applicationContext, "" +   location?.longitude, Toast.LENGTH_LONG).show()

                    if (locationLat != null && locationLong!= null) {
                        // create a LatLng object from location
                        val latLng = LatLng(locationLat, locationLong)
                        //create a marker at the read location and display it on the map

                        val icon = BitmapDescriptorFactory.fromResource(R.drawable.marker)
                        mMap.addMarker(MarkerOptions().position(latLng).icon(icon)
                            .title("The user is currently here"))
                        //specify how the map camera is updated
                        val update = CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)
                        //update the camera with the CameraUpdate object
                        mMap.moveCamera(update)

                        Toast.makeText(applicationContext, "onDataChange", Toast.LENGTH_LONG).show()

                    } else {
                        // if location is null , log an error message
                        Log.e("TAG", "user location cannot be found")
                    }
                }

            }


        }
        // show this toast if there is an error while reading from the database
        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(applicationContext, "Could not read from database", Toast.LENGTH_LONG).show()
        }

    }

    private fun onLocationShare(key: String?) {

        val url = "https://locationsupport.page.link/shareLocation?locationId=${key}"

        Log.e("TAG", "onLocationShare: $key")

        generateURL(url.toUri()) {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_SUBJECT, "Sharing URL")
            i.putExtra(Intent.EXTRA_TEXT, it)
            startActivity(Intent.createChooser(i, "Share URL"))
        }



    }

    private fun generateURL(
        generateURI : Uri,
        getShareLink : (String) -> Unit = {}
    ){
        val shareLink = FirebaseDynamicLinks.getInstance().createDynamicLink().run {
            link = generateURI
            domainUriPrefix = "https://locationsupport.page.link"

            androidParameters {
                build()
            }

            buildDynamicLink()

        }

        getShareLink.invoke(shareLink.uri.toString())

    }

}