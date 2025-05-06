package com.example.kmamdm.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.kmamdm.R
import com.example.kmamdm.databinding.DialogEnterDeviceIdBinding

class EnterDeviceIdDialog(
    private val error: Boolean,
    private val deviceID: String? = null,
    private val onClickExit: () -> Unit,
    private val onClickSave: (String) -> Unit,
    private val onClickDetails: () -> Unit,
) : DialogFragment() {
    private lateinit var binding: DialogEnterDeviceIdBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BaseDialog)
        isCancelable = false
        binding = DialogEnterDeviceIdBinding.inflate(layoutInflater)

        if (!error) {
            binding.tvError.visibility = View.GONE
            binding.tvDetails.visibility = View.GONE
        } else {
            binding.tvError.visibility = View.VISIBLE
            binding.tvDetails.visibility = View.VISIBLE
            binding.tvDetails.setOnClickListener {
                dismiss()
                onClickDetails()
            }
        }

        if (deviceID != null) {
            binding.deviceId.setText(deviceID)
        }

        binding.btnExit.setOnClickListener {
            dismiss()
            onClickExit()
        }
        binding.btnSave.setOnClickListener {
            val deviceId = binding.deviceId.text.toString()
            if (deviceId.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Device ID must not be empty",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            dismiss()
            onClickSave(deviceId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }
}