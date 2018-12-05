package edu.uw.bonngyn.donut

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import java.util.*
import kotlin.concurrent.schedule

class Delivered: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivered)
        Timer("SettingUp", false).schedule(1000) {
            startActivity(Intent(applicationContext, MapsActivity::class.java))
        }
    }
}