package com.traynor.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.traynor.player.ui.PlayerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { PlayerApp((application as PlayerApplication).container, ::enterPip) }
    }
    private fun enterPip() {
        if (packageManager.hasSystemFeature("android.software.picture_in_picture"))
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }
    override fun onUserLeaveHint() { super.onUserLeaveHint() }
    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) { super.onPictureInPictureModeChanged(inPip, newConfig) }
}
