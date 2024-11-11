package com.guzzardo.android.willyshmo.kotlintictacdoh

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View

class RulesActivity : Activity() {
    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rules)
        findViewById<View>(R.id.rules_ok).setOnClickListener {
            showRules()
            finish()
        }
    }

    private fun showRules() {
        val i = Intent(this, MainActivity::class.java)
        startActivity(i)
    }
}