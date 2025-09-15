package com.example.kolki.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.kolki.ui.quick.QuickVoiceActivity

class QuickVoiceTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.label = "Kolki"
        try {
            qsTile?.icon = Icon.createWithResource(this, com.example.kolki.R.drawable.ic_coin)
        } catch (_: Exception) {}
        qsTile?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        try {
            if (isLocked) {
                unlockAndRun { launchQuickVoice() }
            } else {
                launchQuickVoice()
            }
        } catch (_: Exception) {
            try { Toast.makeText(applicationContext, "No se pudo iniciar voz", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
        }
    }

    private fun launchQuickVoice() {
        try {
            val intent = Intent(this, QuickVoiceActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivityAndCollapse(intent)
        } catch (_: Exception) {
            // Fallback if startActivityAndCollapse is unavailable
            try { QuickVoiceActivity.launchFromService(this) } catch (_: Exception) {}
        }
    }
}
