package com.guzzardo.tictacdoh2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

class RulesActivity : Activity() {
    private var mAdView: com.google.android.gms.ads.AdView? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rules)
        findViewById<View>(R.id.rules_ok).setOnClickListener {
            //showRules()
            finish()
        }
        mAdView = findViewById<android.view.View>(R.id.ad_rules) as AdView
        val adRequest = AdRequest.Builder().build()
        mAdView!!.loadAd(adRequest)
    }

    private fun showRules() {
        val i = Intent(this, MainActivity::class.java)
        startActivity(i)
    }
}