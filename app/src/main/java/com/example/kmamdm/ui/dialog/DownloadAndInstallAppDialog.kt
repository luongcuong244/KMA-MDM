package com.example.kmamdm.ui.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.kmamdm.R
import com.example.kmamdm.databinding.DialogDownloadAndInstallAppBinding
import com.example.kmamdm.model.Application
import kotlin.math.roundToInt

class DownloadAndInstallAppDialog(
    private val onInitBinding: () -> Unit
) : DialogFragment() {

    private var _binding: DialogDownloadAndInstallAppBinding? = null
    private val binding get() = _binding!!  // Đảm bảo binding không null
    private var isInitBinding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BaseDialog)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDownloadAndInstallAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isInitBinding) {
            isInitBinding = true
            onInitBinding()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // Tránh memory leak
    }

    fun showDownloadApp(application: Application) {
        if (!isAdded || !isInitBinding) return

        binding.tvTitle.text = getString(R.string.downloading)
        binding.tvAppName.text = application.name
        binding.tvProgress.visibility = View.VISIBLE
        // change progress bar style
        binding.progress.isIndeterminate = false
    }

    @SuppressLint("SetTextI18n")
    fun showDownloadProgress(progress: Int, total: Long, current: Long) {
        if (!isAdded || !isInitBinding) return

        binding.progress.max = 100
        binding.progress.progress = progress

        val totalSize = formatFileSize(total)
        val currentSize = formatFileSize(current)

        binding.tvProgress.text = "$currentSize / $totalSize"
    }

    fun showAppInstalling(application: Application) {
        if (!isAdded || !isInitBinding) return

        binding.tvTitle.text = getString(R.string.installing)
        binding.tvAppName.text = application.name
        binding.tvProgress.visibility = View.GONE
        binding.progress.isIndeterminate = true
    }

    // Chuyển đổi byte thành MB hoặc KB cho dễ đọc
    private fun formatFileSize(size: Long): String {
        return if (size >= 1024 * 1024) {
            "${(size / (1024.0 * 1024.0)).roundToInt()} MB"
        } else {
            "${(size / 1024.0).roundToInt()} KB"
        }
    }
}