package com.example.kmamdm.ui.screen.adminmoderequest

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kmamdm.R
import com.example.kmamdm.utils.LegacyUtils

class AdminModeRequestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val adminComponentName: ComponentName = LegacyUtils.getAdminComponentName(this)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
        try {
            startActivityForResult(intent, 1)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.admin_not_supported), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        setResult(resultCode)
        finish()
    }
}
