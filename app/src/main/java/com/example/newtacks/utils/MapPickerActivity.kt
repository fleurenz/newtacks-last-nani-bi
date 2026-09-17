package com.example.newtacks.utils

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.newtacks.R
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.*

class MapPickerActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private lateinit var tvPickedAddress: TextView
    private var lastPickedLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        tvPickedAddress = findViewById(R.id.tvPickedAddress)
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        val initialLat = intent.getDoubleExtra("LAT", 7.0648)
        val initialLng = intent.getDoubleExtra("LNG", 125.6079)

        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle("asset://map_style.json") {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initialLat, initialLng), 15.0))
                updateAddress(LatLng(initialLat, initialLng))
            }

            map.addOnCameraIdleListener {
                val target = map.cameraPosition.target
                if (target != null) {
                    updateAddress(target)
                }
            }
        }

        findViewById<View>(R.id.btnConfirmLocation).setOnClickListener {
            val target = lastPickedLatLng
            if (target != null) {
                val resultIntent = Intent()
                resultIntent.putExtra("LAT", target.latitude)
                resultIntent.putExtra("LNG", target.longitude)
                resultIntent.putExtra("ADDRESS", tvPickedAddress.text.toString())
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Please pick a location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAddress(latLng: LatLng) {
        lastPickedLatLng = latLng
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                tvPickedAddress.text = addresses[0].getAddressLine(0)
            } else {
                tvPickedAddress.text = String.format(Locale.getDefault(), "%.5f, %.5f", latLng.latitude, latLng.longitude)
            }
        } catch (e: Exception) {
            tvPickedAddress.text = String.format(Locale.getDefault(), "%.5f, %.5f", latLng.latitude, latLng.longitude)
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}