package com.example.kmamdm.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.kmamdm.R
import com.example.kmamdm.databinding.DialogEnterServerUrlBinding

class EnterServerUrlDialog(
    private val serverUrl: String? = null,
    private val onClickSave: (String) -> Unit,
) : DialogFragment() {
    private lateinit var binding: DialogEnterServerUrlBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BaseDialog)
        isCancelable = false
        binding = DialogEnterServerUrlBinding.inflate(layoutInflater)

        if (serverUrl != null) {
            binding.etServerUrl.setText(serverUrl)
        }
        binding.btnOk.setOnClickListener {
            val deviceId = binding.etServerUrl.text.toString()
            if (deviceId.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Server url must not be empty",
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