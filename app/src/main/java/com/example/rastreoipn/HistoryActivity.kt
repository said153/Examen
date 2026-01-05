package com.ipn.rastreo

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ipn.rastreo.database.AppDatabase
import com.ipn.rastreo.database.LocationEntity
import com.ipn.rastreo.databinding.ActivityHistoryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var locationsAdapter: LocationsAdapter
    private var locationsList = mutableListOf<LocationEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadLocations()
        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnExport.setOnClickListener {
            exportToCSV()
        }

        binding.btnClearAll.setOnClickListener {
            clearAllLocations()
        }

        // Opciones de exportación
        val exportOptions = arrayOf("Exportar a CSV", "Exportar a JSON", "Exportar a TXT")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exportOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerExport.adapter = adapter
    }

    private fun setupRecyclerView() {
        locationsAdapter = LocationsAdapter(locationsList) { location ->
            // Mostrar detalles de la ubicación seleccionada
            showLocationDetails(location)
        }

        binding.recyclerViewLocations.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = locationsAdapter
        }
    }

    private fun loadLocations() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(this@HistoryActivity)
            val locations = db.locationDao().getAll()

            withContext(Dispatchers.Main) {
                locationsList.clear()
                locationsList.addAll(locations)
                locationsAdapter.notifyDataSetChanged()

                // Actualizar contador
                binding.tvCount.text = "Registros: ${locationsList.size}"

                if (locationsList.isEmpty()) {
                    binding.tvEmpty.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun showLocationDetails(location: LocationEntity) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val date = Date(location.timestamp)

        val message = """
            📍 Ubicación
            Latitud: ${"%.6f".format(location.latitude)}
            Longitud: ${"%.6f".format(location.longitude)}
            Fecha: ${dateFormat.format(date)}
            Precisión: ${"%.1f".format(location.accuracy)} metros
        """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle("Detalles de Ubicación")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun exportToCSV() {
        if (locationsList.isEmpty()) {
            Toast.makeText(this, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val csvContent = StringBuilder()
                csvContent.append("Latitud,Longitud,Fecha,Precisión\n")

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                locationsList.forEach { location ->
                    val date = Date(location.timestamp)
                    csvContent.append("${location.latitude},${location.longitude},")
                    csvContent.append("${dateFormat.format(date)},${location.accuracy}\n")
                }

                // Guardar archivo
                val fileName = "rastreo_${System.currentTimeMillis()}.csv"
                openFileOutput(fileName, Context.MODE_PRIVATE).use {
                    it.write(csvContent.toString().toByteArray())
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HistoryActivity,
                        "Archivo exportado: $fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HistoryActivity,
                        "Error al exportar: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun clearAllLocations() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Confirmar")
            .setMessage("¿Estás seguro de eliminar todo el historial?")
            .setPositiveButton("Sí") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(this@HistoryActivity)
                    db.locationDao().deleteAll()

                    withContext(Dispatchers.Main) {
                        locationsList.clear()
                        locationsAdapter.notifyDataSetChanged()
                        binding.tvCount.text = "Registros: 0"
                        binding.tvEmpty.visibility = android.view.View.VISIBLE
                        Toast.makeText(this@HistoryActivity, "Historial eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}

// Adapter para RecyclerView
class LocationsAdapter(
    private val locations: List<LocationEntity>,
    private val onItemClick: (LocationEntity) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<LocationsAdapter.LocationViewHolder>() {

    class LocationViewHolder(val binding: ItemLocationBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemLocationBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val location = locations[position]
        val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        val date = Date(location.timestamp)

        holder.binding.tvCoordinates.text = String.format(
            "📍 %.6f, %.6f",
            location.latitude,
            location.longitude
        )
        holder.binding.tvTime.text = dateFormat.format(date)
        holder.binding.tvAccuracy.text = String.format("Precisión: %.1f m", location.accuracy)

        holder.itemView.setOnClickListener {
            onItemClick(location)
        }
    }

    override fun getItemCount() = locations.size
}