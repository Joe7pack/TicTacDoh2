package com.guzzardo.tictacdoh2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StartActivity : AppCompatActivity()  {

    public override fun onStart() {
        super.onStart()
        //writeToLog("StartActivity", "onStart called at " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
    }

    // Called when the activity is first created.
    public override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { return@setKeepOnScreenCondition false }
        super.onCreate(savedInstanceState)

        val willyShmoApplicationContext = this.applicationContext
        val myIntent = Intent(willyShmoApplicationContext, WillyShmoApplication::class.java)
        startActivity(myIntent)
    }


}