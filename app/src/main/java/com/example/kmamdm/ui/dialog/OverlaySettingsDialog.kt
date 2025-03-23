package com.example.kmamdm.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.kmamdm.R
import com.example.kmamdm.databinding.DialogAdministratorModeBinding
import com.example.kmamdm.databinding.DialogOverlaySettingsBinding

class OverlaySettingsDialog(
    private val onSkip: () -> Unit,
    private val onContinue: () -> Unit
) : DialogFragment() {
    private lateinit var binding: DialogOverlaySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BaseDialog)
        isCancelable = false
        binding = DialogOverlaySettingsBinding.inflate(layoutInflater)
        binding.btnSkip.setOnClickListener {
            dismiss()
            onSkip()
        }
        binding.btnContinue.setOnClickListener {
            dismiss()
            onContinue()
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