package com.czjk.trainwidget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status)
        val grantButton = findViewById<Button>(R.id.grant_button)
        grantButton.setOnClickListener { requestPermission() }
        updateStatus()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        } else {
            updateStatus()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            updateStatus()
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                postCurrentState()
            }
        }
    }

    private fun postCurrentState() {
        val state = Schedule.stateAt(
            java.time.LocalTime.now(),
            java.time.LocalDate.now().dayOfWeek,
        )
        LockScreenNotifier.update(this, state)
    }

    private fun updateStatus() {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        statusText.setText(
            if (granted) R.string.status_granted else R.string.status_not_granted,
        )
        findViewById<Button>(R.id.grant_button).isEnabled = !granted
    }

    companion object {
        private const val REQUEST_CODE = 100
    }
}
