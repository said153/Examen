package com.ipn.rastreo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.ipn.rastreo.database.AppDatabase
import com.ipn.rastreo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleMap: GoogleMap
    private var isTracking = false
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setupUI()
        setupMap()
    }

    // Registrar broadcast receiver
    locationReceiver = LocationBroadcastReceiver { lat, lng ->
        updateLocationOnMap(lat, lng)
    }
    locationReceiver.register(this)

    // Agregir variable de clase:
    private lateinit var locationReceiver: LocationBroadcastReceiver

    // En onDestroy():
    override fun onDestroy() {
        super.onDestroy()
        locationReceiver.unregister(this)
        stopTracking()
    }

    private fun setupUI() {
        // Configurar spinner de intervalos
        val intervals = arrayOf("10 segundos", "60 segundos", "5 minutos")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter

        // Botón Iniciar Rastreo
        binding.btnStart.setOnClickListener {
            if (!isTracking) {
                startTracking()
            }
        }

        // Botón Detener Rastreo
        binding.btnStop.setOnClickListener {
            if (isTracking) {
                stopTracking()
            }
        }

        // Botón Ver Historial
        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // Botón Limpiar Historial
        binding.btnClear.setOnClickListener {
            clearHistory()
        }

        // Selector de tema
        binding.spinnerTheme.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Tema IPN", "Tema ESCOM")
        )

        binding.spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                when(position) {
                    0 -> setTheme(R.style.Theme_IPN)
                    1 -> setTheme(R.style.Theme_ESCOM)
                }
                recreate()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configuración básica del mapa
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
        }

        // Mover cámara a Ciudad de México (IPN)
        val ipnLocation = LatLng(19.5046, -99.1460)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ipnLocation, 15f))
    }

    private fun checkPermissions() {
        val permissionsToRequest = locationPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permisos necesarios para el rastreo", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startTracking() {
        val intent = Intent(this, TrackingService::class.java)

        // Obtener intervalo seleccionado
        val intervalPosition = binding.spinnerInterval.selectedItemPosition
        val interval = when(intervalPosition) {
            0 -> 10000L  // 10 segundos
            1 -> 60000L  // 60 segundos
            2 -> 300000L // 5 minutos
            else -> 10000L
        }
        intent.putExtra("interval", interval)

        // Iniciar servicio en foreground
        ContextCompat.startForegroundService(this, intent)
        isTracking = true
        updateTrackingStatus()
        Toast.makeText(this, "Rastreo iniciado", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        val intent = Intent(this, TrackingService::class.java)
        stopService(intent)
        isTracking = false
        updateTrackingStatus()
        Toast.makeText(this, "Rastreo detenido", Toast.LENGTH_SHORT).show()
    }

    private fun updateTrackingStatus() {
        if (isTracking) {
            binding.tvStatus.text = "Estado: Activo"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            binding.tvStatus.text = "Estado: Inactivo"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun clearHistory() {
        Thread {
            val db = AppDatabase.getDatabase(this)
            db.locationDao().deleteAll()
            runOnUiThread {
                Toast.makeText(this, "Historial limpiado", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    fun updateLocationOnMap(latitude: Double, longitude: Double) {
        runOnUiThread {
            val location = LatLng(latitude, longitude)

            // Limpiar marcadores anteriores
            googleMap.clear()

            // Agregar nuevo marcador
            googleMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title("Tu ubicación")
            )

            // Mover cámara a la nueva ubicación
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))

            // Actualizar coordenadas en pantalla
            binding.tvCoordinates.text = String.format("Lat: %.6f\nLng: %.6f", latitude, longitude)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }
}