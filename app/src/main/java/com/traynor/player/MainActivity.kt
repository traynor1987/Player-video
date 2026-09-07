package com.traynor.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.traynor.player.ui.PlayerApp

class MainActivity : ComponentActivity() {
    private var inPictureInPicture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { PlayerApp((application as PlayerApplication).container, ::enterPip, inPictureInPicture) }
    }
    private fun enterPip(aspectRatio: Rational) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature("android.software.picture_in_picture")) {
            val params = PictureInPictureParams.Builder().setAspectRatio(aspectRatio).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setSeamlessResizeEnabled(true)
            }.build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }
    override fun onUserLeaveHint() { super.onUserLeaveHint() }
    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        inPictureInPicture = inPip
        super.onPictureInPictureModeChanged(inPip, newConfig)
    }
}
