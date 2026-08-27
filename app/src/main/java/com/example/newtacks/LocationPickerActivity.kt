package com.example.newtacks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class LocationPickerActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->
            mapLibreMap = map
            // Use the local OSM style
            map.setStyle("asset://map_style.json") {
                // Initial camera position (Ponciano, Davao City)
                val initialPos = LatLng(7.0725, 125.6111)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 13.0))

                // Set zoom boundaries
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(18.0)
            }
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnConfirmLocation).setOnClickListener {
            val center = mapLibreMap?.cameraPosition?.target
            if (center != null) {
                val resultIntent = Intent()
                resultIntent.putExtra("lat", center.latitude)
                resultIntent.putExtra("lng", center.longitude)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
}