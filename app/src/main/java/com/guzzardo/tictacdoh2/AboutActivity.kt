package com.guzzardo.tictacdoh2

import com.google.android.gms.ads.AdRequest

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class AboutActivity : android.app.Activity() {
    private var mAdView: com.google.android.gms.ads.AdView? = null

    public override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)
        findViewById<android.view.View>(R.id.about_ok).setOnClickListener {
            finish()
        }
        mAdView = findViewById<android.view.View>(R.id.ad_about) as com.google.android.gms.ads.AdView
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        mAdView!!.loadAd(adRequest)
    }
}