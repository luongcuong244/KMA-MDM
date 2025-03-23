package com.example.kmamdm.ui.screen.main

import android.annotation.SuppressLint
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ActivityMainBinding
import com.example.kmamdm.ui.screen.base.BaseActivity

class MainActivity : BaseActivity<MainViewModel, ActivityMainBinding>() {
    override fun createViewModel() = MainViewModel::class.java

    override fun getContentView(): Int = R.layout.activity_main

    override fun initView() {
    }

    override fun bindViewModel() {
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {

    }
}