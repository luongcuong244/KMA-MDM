package com.example.kmamdm.ui.screen.error_details

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ActivityErrorDetailsBinding
import com.example.kmamdm.utils.Utils

class ErrorDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityErrorDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_error_details)

        val intent = intent
        val resetEnabled = intent.getBooleanExtra(RESET_ENABLED, false)
        binding.resetButton.visibility = if (resetEnabled) View.VISIBLE else View.GONE

        val message = intent.getStringExtra(MESSAGE)
        binding.editMessage.setText(message)
    }

    fun resetClicked(view: View?) {
        // Factory reset!!!
        if (!Utils.factoryReset(this)) {
            // RemoteLogger.log(this, Const.LOG_WARN, "Device reset failed")
            Toast.makeText(
                this,
                "Device reset failed",
                Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }

    fun closeClicked(view: View?) {
        finish()
    }

    companion object {
        const val MESSAGE: String = "MESSAGE"
        const val RESET_ENABLED: String = "RESET_ENABLED"

        fun display(parent: Activity, message: String?, resetEnabled: Boolean) {
            val intent = Intent(parent, ErrorDetailsActivity::class.java)
            intent.putExtra(MESSAGE, message)
            intent.putExtra(RESET_ENABLED, resetEnabled)
            parent.startActivity(intent)
        }
    }
}