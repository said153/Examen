package com.ipn.rastreo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class LocationBroadcastReceiver(private val onLocationReceived: (Double, Double) -> Unit) :
    BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "LOCATION_UPDATE") {
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)

            if (latitude != 0.0 && longitude != 0.0) {
                onLocationReceived(latitude, longitude)
            }
        }
    }

    fun register(context: Context) {
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(this, IntentFilter("LOCATION_UPDATE"))
    }

    fun unregister(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
    }
}