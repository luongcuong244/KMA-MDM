package com.example.kmamdm.ui.screen.mdm_setup

import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.kmamdm.AdminReceiver
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ActivityMdmChoiceBinding
import com.example.kmamdm.databinding.DialogEnterDeviceIdBinding
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.utils.Const

class MdmChoiceSetupActivity : AppCompatActivity() {
    private var binding: ActivityMdmChoiceBinding? = null

    protected var enterDeviceIdDialog: Dialog? = null
    protected var enterDeviceIdDialogBinding: DialogEnterDeviceIdBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(Const.LOG_TAG, "Launching the provisioning mode choice activity")

        binding = DataBindingUtil.setContentView(this, R.layout.activity_mdm_choice)

        val intent = intent
        val bundle =
            intent.getParcelableExtra<PersistableBundle>(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        if (bundle?.getString(Const.QR_OPEN_WIFI_ATTR) != null) {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
        AdminReceiver.updateSettings(this, bundle)

        val settingsHelper: SettingsHelper = SettingsHelper.getInstance(this)
        if (settingsHelper.getDeviceId() == null || settingsHelper.getDeviceId()?.length === 0) {
            Log.d(Const.LOG_TAG, "Device ID is empty")
            val deviceIdUse: String? = settingsHelper.getDeviceIdUse()
            Log.d(Const.LOG_TAG, "Device ID choice: $deviceIdUse")
            val deviceId =
                if ("imei" == deviceIdUse) {
                    // These extras could not be set so we should retry setting these values in InitialSetupActivity!
                    intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_IMEI)
                } else if ("serial" == deviceIdUse) {
                    intent.getStringExtra(DevicePolicyManager.EXTRA_PROVISIONING_SERIAL_NUMBER)
                } else {
                    displayEnterDeviceIdDialog()
                    return
                }
            settingsHelper.setDeviceId(deviceId)
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }

    fun continueSetup(view: View?) {
        val intent = Intent()
        intent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        )
        setResult(RESULT_OK, intent)
        finish()
    }

    protected fun displayEnterDeviceIdDialog() {
        enterDeviceIdDialog = Dialog(this)
        enterDeviceIdDialogBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_enter_device_id,
            null,
            false
        )
        enterDeviceIdDialog!!.setCancelable(false)
        enterDeviceIdDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        enterDeviceIdDialogBinding?.btnSave?.setOnClickListener {
            saveDeviceId(it)
        }

        enterDeviceIdDialog?.setContentView(enterDeviceIdDialogBinding!!.root)
        enterDeviceIdDialog!!.show()
    }

    fun saveDeviceId(view: View?) {
        val deviceId: String = enterDeviceIdDialogBinding?.deviceId?.text.toString().trim()
        if ("" == deviceId) {
            return
        } else {
            val settingsHelper: SettingsHelper = SettingsHelper.getInstance(this)
            settingsHelper.setDeviceId(deviceId)
            if (enterDeviceIdDialog != null) {
                enterDeviceIdDialog!!.dismiss()
            }
        }
    }
}