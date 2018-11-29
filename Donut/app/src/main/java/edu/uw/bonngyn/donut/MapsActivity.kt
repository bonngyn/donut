package edu.uw.bonngyn.donut

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.animation.LinearInterpolator
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.location.*
import com.google.android.gms.location.places.GeoDataClient
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlaceAutocomplete
import com.google.android.gms.location.places.ui.PlacePicker

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geoDataClient: GeoDataClient

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationSettingsRequest: LocationSettingsRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var currentLocationMarker: Marker
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // initializes location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        prepareMap()
        onClickAddFab()
        onClickSettingsFab()
        updateValuesFromBundle(savedInstanceState)
        getLastKnownLocation()
        createLocationCallback()
        createLocationRequest()
        buildLocationSettingsRequest()
        startLocationUpdates()
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
    }

    // sets an add floating action button
    private fun onClickAddFab() {
        fab_add.setOnClickListener {
            // TODO: allows for adding marker location to map and database
        }
    }

    // sets a settings floating action button
    private fun onClickSettingsFab() {
        fab_settings.setOnClickListener {
            // TODO: opens settings
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // finds the last known location to initialize UI
    private fun getLastKnownLocation() {
        if (checkLocationPermission()) {
            // get last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                currentLocation = location
                initializeUI()
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
    private fun initializeUI() {
        if (currentLocation != null) {
            val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
            val markerHue = 205f
            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(currentLatLng)
                    .title("Your location")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
            )
            val zoomLevel = 18f
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, zoomLevel))
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
                for (location in locationResult.locations){
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

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
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
