package com.example.kmamdm.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.kmamdm.R
import com.example.kmamdm.databinding.DialogNetworkErrorrBinding
import com.example.kmamdm.helper.SettingsHelper

class NetworkErrorDialog(
    private val showWifiButton: Boolean,
    private val onClickRetry: () -> Unit,
    private val onClickReset: () -> Unit,
    private val onClickWifi: () -> Unit,
    private val onClickCancel: () -> Unit,
    private val onClickDetails: () -> Unit,
) : DialogFragment() {
    private lateinit var binding: DialogNetworkErrorrBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BaseDialog)
        isCancelable = false
        binding = DialogNetworkErrorrBinding.inflate(layoutInflater)

        context?.let {
            binding.tvError.text = "Error connecting to: ${SettingsHelper.getInstance(it).getBaseUrl()}. Please check your network connection."
        }

        if (showWifiButton) {
            binding.btnWifi.visibility = View.VISIBLE
        } else {
            binding.btnWifi.visibility = View.GONE
        }

        binding.btnRetry.setOnClickListener {
            dismiss()
            onClickRetry()
        }
        binding.btnReset.setOnClickListener {
            dismiss()
            onClickReset()
        }
        binding.btnWifi.setOnClickListener {
            dismiss()
            onClickWifi()
        }
        binding.btnCancel.setOnClickListener {
            dismiss()
            onClickCancel()
        }
        binding.tvDetails.setOnClickListener {
            dismiss()
            onClickDetails()
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