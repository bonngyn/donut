package edu.uw.bonngyn.donut

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.location.*
import com.google.android.gms.location.places.ui.PlaceAutocomplete

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.common.collect.ImmutableList
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

import kotlinx.android.synthetic.main.activity_maps.*
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationSettingsRequest: LocationSettingsRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var currentLocationMarker: Marker
    private var currentLocation: Location? = null

    private lateinit var shakeListener: ShakeListener
    private lateinit var sensorManager: SensorManager
    private lateinit var sensor: Sensor

    private lateinit var database: FirebaseFirestore

    private var radiusToggle = false

    private var shakeOption = false
    private var radiusOption = 15
    private var timeOption = "15"
    private var zoomlevel = 18f

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // initializes location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // initializes shake listener
        shakeListener = ShakeListener();
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // initializes firestore
        database = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setTimestampsInSnapshotsEnabled(true)
            .build()
        database.firestoreSettings = settings

        prepareMap()
        onClickZoomFab()
        onClickAddFab()
        onClickSettingsFab()
        updateValuesFromBundle(savedInstanceState)
        getLastKnownLocation()
        createLocationCallback()
        createLocationRequest()
        buildLocationSettingsRequest()
        startLocationUpdates()
        getDropoffs()
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle?) {
        savedInstanceState?.putParcelable(LOCATION_KEY, currentLocation)
        super.onSaveInstanceState(savedInstanceState)
    }

    private fun updateValuesFromBundle(savedInstanceState: Bundle?) {
        savedInstanceState ?: return

        // gets current location
        if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
            currentLocation = savedInstanceState.getParcelable(LOCATION_KEY)
        }
    }

    // obtain the SupportMapFragment and get notified when the map is ready to be used.
    private fun prepareMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    // when the map is ready
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.setOnInfoWindowClickListener {
            dropoffAlert(it)
        }
    }

    // alert message to mark as delivered
    private fun dropoffAlert(marker: Marker) {
        val alertDialog = AlertDialog.Builder(this).create()
        alertDialog.setTitle("Delivery")
        alertDialog.setMessage("Mark this as delivered?")
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ -> dialog.dismiss()}
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Delivered!") { dialog, which ->
            val dropoff  = database.collection("dropoffs").document(marker.tag.toString())
            dropoff.update("delivered", true)
            marker.remove()
            // TODO: Delivered screen shows up
            startActivity(Intent(applicationContext, Delivered::class.java))

        }
        alertDialog.show()
    }

    // creates a dropoff for given information about a marker
    private fun createDropOff(title: String, description: String?, location: GeoPoint, delivered: Boolean, timestamp: Date) {
        val dropoff = Dropoff(title, description, location, delivered, timestamp)
        database.collection("dropoffs")
            .add(dropoff)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.id)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }
    }

    // gets all the dropoffs from the database and adds them to the map
    private fun getDropoffs() {
        val distResult = FloatArray(1)
        database.collection("dropoffs").whereEqualTo("delivered", false)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    for (dropoff in result) {
                        val dropoffData = dropoff.data

                        val title = dropoffData.get("title") as String
                        val description = dropoffData.get("description") as String
                        val location = dropoffData.get("location") as Map<String, Number>
                        val timestamp = dropoffData.get("timestamp") as Timestamp
                        val geoLoc = GeoPoint(location.get("latitude"), location.get("longitude"))
                        val delivered = dropoffData.get("delivered") as Boolean
                        Log.v("donutTime", "" + timestamp)
                        val pos = LatLng(location.get("latitude") as Double, location.get("longitude") as Double)
                        // TODO: use radius and time to filter the received markers
                        //Filter by time
                        val time = getTimeDifference(timestamp, Calendar.getInstance().time)
                        val filterTime = Integer.parseInt(timeOption)
                        Log.v("donutFilteredTime", "" + filterTime)
                        Log.v("donutDifferenceTime", "" + time)
                        //Filter by radius
                        if(radiusToggle){
                            Location.distanceBetween(currentLocation!!.latitude, currentLocation!!.longitude, pos.latitude, pos.longitude, distResult)
                            if((distResult[0] / 1000) <= radiusOption && time <= filterTime) {
                                val marker = map.addMarker(
                                    MarkerOptions().position(pos)
                                        .title(title)
                                        .snippet(description)
                                        .draggable(false)
                                )
                                marker.tag = dropoff.id
                            }
                        } else {
                            if(time <= filterTime) {
                                val marker = map.addMarker(
                                    MarkerOptions().position(pos)
                                        .title(title)
                                        .snippet(description)
                                        .draggable(false)
                                )
                                marker.tag = dropoff.id
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting dropoffs", exception)
            }
    }

    private fun getTimeDifference(timestamp: Timestamp, currTimestamp: Date):Double {
        val difference = currTimestamp.time - (timestamp.seconds * 1000)
        return ((difference * .001) / 60)
    }


    // handles the dialog box that appears when adding a marker
    private fun handleMarkerDialog(position: LatLng) {
        val builder = AlertDialog.Builder(this)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val title = EditText(this)
        title.hint = "Title"
        layout.addView(title)

        val description = EditText(this)
        description.hint = "Description"
        layout.addView(description) // Another add method

        builder.setView(layout) // Again this is a set method, not add

        // adds the marker
        builder.setPositiveButton("Accept") { _, _ ->
            val title = title.text.toString()
            val description = description.text.toString()
            val location = GeoPoint(position.latitude, position.longitude)
            map.addMarker(
                MarkerOptions().position(position)
                    .title(title)
                    .snippet(description)
            )
            val calendar = Calendar.getInstance()
            val datetime = calendar.time
            createDropOff(title, description, location, false, datetime)
        }

        // cancels share
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()

        // sets long click listener back to null
        map.setOnMapLongClickListener {
            // ignore
        }
    }

    // handles adding a marker through long click on the map
    private fun handleAddMarker() {
        map.setOnMapLongClickListener {
            handleMarkerDialog(it)
    }
    }

    // sets an add floating action button
    private fun onClickAddFab() {
        fab_add.setOnClickListener {
            Toast.makeText(this@MapsActivity, getString(R.string.add_directions), Toast.LENGTH_LONG).show()
            handleAddMarker()
        }
    }

    // sets a settings floating action button
    private fun onClickSettingsFab() {
        fab_settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    //zooming buttons
    private fun onClickZoomFab() {
        zoomin.setOnClickListener {
            zoomlevel += 1
            map.moveCamera(CameraUpdateFactory.zoomTo(zoomlevel))
        }

        zoomout.setOnClickListener {
            zoomlevel -= 1
            map.moveCamera(CameraUpdateFactory.zoomTo(zoomlevel))
        }
    }

    // finds the last known location to initialize UI
    private fun getLastKnownLocation() {
        if (checkLocationPermission()) {
            // get last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                currentLocation = location
                initializeUI(location)
            }
        } else {
            // check permissions now
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE
            )
        }
    }

    // initializes the map user interface
    private fun initializeUI(location: Location?) {
        if (location != null) {
            val currentLatLng = LatLng(location.latitude, location.longitude)
            val markerHue = 205f
            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(currentLatLng)
                    .title("Your location")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
            )
            zoomlevel = 18f
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, zoomlevel))
        }
    }

    // builds the location settings request
    private fun buildLocationSettingsRequest() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        locationSettingsRequest = builder.build()
    }

    // taken from docs
    // creates a location request
    private fun createLocationRequest() {
        locationRequest =  LocationRequest().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    // referenced google samples
    // animates the marker through interpolation
    private fun animateMarker(marker: Marker, toPosition: LatLng) {
        val handler = Handler()
        val start = SystemClock.uptimeMillis()
        val proj = map.getProjection()
        val startPoint = proj.toScreenLocation(marker.position)
        val startLatLng = proj.fromScreenLocation(startPoint)
        val duration: Long = 500

        val interpolator = LinearInterpolator()

        handler.post(object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val t = interpolator.getInterpolation(elapsed.toFloat() / duration)
                val lng = t * toPosition.longitude + (1 - t) * startLatLng.longitude
                val lat = t * toPosition.latitude + (1 - t) * startLatLng.latitude
                marker.setPosition(LatLng(lat, lng))
                if (t < 1.0) {
                    // Post again 16ms later.
                    handler.postDelayed(this, 16)
                }
            }
        })
    }

    // updates marker on the map
    private fun updateUI() {
        if (currentLocation != null) {
            val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
            animateMarker(currentLocationMarker, currentLatLng)
        }
    }

    // creates a location callback that gets the updated current location
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    currentLocation = location

                    updateUI()
                }
            }
        }
    }

    // checks the location permissions
    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // starts updating the location
    private fun startLocationUpdates() {
        if (checkLocationPermission()) {
            // lines for where person moves
            // get location updates
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
        } else {
            // check Permissions Now
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE
            )
        }
    }

    // stops the location from updating
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // starts listening for shakes
    private fun startShakeListener() {
        if (sensorManager != null) {
            if (sensor != null) {
                sensorManager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            } else {
                // throws exception if accelerometer are not supported
                throw UnsupportedOperationException("accelerometer not supported");
            }
        } else {
            // throws exception if sensors are not supported
            throw UnsupportedOperationException("Sensors not supported");
        }
    }

    // stops listening for shakes
    private fun stopShakeListener() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(shakeListener)
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()

        shakeOption = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean("shake_option", false)
        if (shakeOption) startShakeListener()
        radiusToggle = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean("radius_toggle", false)
        radiusOption = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getInt("radius_option", 15)
        timeOption = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString("time_option", "15")
        Log.v("timeDonut", "" + timeOption)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        stopShakeListener()
    }

    // creates menu options
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.map_menu, menu)
        return true
    }

    // when a menu option is selected
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        return when (id) {
            R.id.menu_my_location -> onClickMenuMyLocation()
            R.id.menu_search -> onClickMenuSearch()
            else -> super.onOptionsItemSelected(item)
        }
    }

    // zooms in on the current location
    private fun onClickMenuMyLocation(): Boolean {
        if (currentLocation != null) {
            val zoomLevel = 18f
            val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, zoomLevel))
        }
        return true
    }

    // opens search bar on search click
    private fun onClickMenuSearch(): Boolean {
        // referencing google docs
        try {
            val intent: Intent = PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                .build(this);
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        } catch (e: GooglePlayServicesRepairableException) {
            // TODO: Handle the error.
        } catch (e: GooglePlayServicesNotAvailableException) {
            // TODO: Handle the error.
        }
        return true
    }

    // referencing google docs
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val place = PlaceAutocomplete.getPlace(this, data!!)
                val zoomLevel = 15f
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(place.latLng, zoomLevel))
                Log.i(TAG, "Place: " + place.name)
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                val status = PlaceAutocomplete.getStatus(this, data!!)
                // TODO: Handle the error.
                Log.i(TAG, status.statusMessage)

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
    }

    companion object {
        private const val LOCATION_REQUEST_CODE = 0
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;
        private const val LOCATION_KEY = "location"
        private const val TAG = "donut"
    }
}
