package com.guzzardo.tictacdoh2

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.ads.AdRequest
import com.guzzardo.tictacdoh2.PlayersOnlineActivity.Companion.getContext
import kotlin.jvm.java
import kotlin.text.format

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
class TwoPlayerActivity : android.app.Activity() {
    private var mPlayer1Name: String? = null
    private var mPlayer2Name: String? = null
    private var mButtonPlayer1: android.widget.Button? = null
    private var mButtonPlayer2: android.widget.Button? = null
    private var mButtonPlayOverNetwork: android.widget.Button? = null
    private var mAdView: com.google.android.gms.ads.AdView? = null

    public override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.guzzardo.android.willyshmo.kotlintictacdoh.R.layout.two_player)
        mPlayer1Name = getString(com.guzzardo.android.willyshmo.kotlintictacdoh.R.string.player_1)
        mPlayer2Name = getString(com.guzzardo.android.willyshmo.kotlintictacdoh.R.string.willy_name)
        val player1Name = intent.getStringExtra(GameActivity.PLAYER1_NAME)
        if (player1Name != null) mPlayer1Name = player1Name
        val player2Name = intent.getStringExtra(GameActivity.PLAYER2_NAME)
        if (player2Name != null) mPlayer2Name = player2Name
        mButtonPlayer1 = findViewById<android.view.View>(com.guzzardo.android.willyshmo.kotlintictacdoh.R.id.player_1) as android.widget.Button
        mButtonPlayer1!!.text = kotlin.String.Companion.format(resources.getString(com.guzzardo.android.willyshmo.kotlintictacdoh.R.string.player_moves_first), mPlayer1Name)
        mButtonPlayer2 = findViewById<android.view.View>(com.guzzardo.android.willyshmo.kotlintictacdoh.R.id.player_2) as android.widget.Button
        mButtonPlayer2!!.text = kotlin.String.Companion.format(resources.getString(com.guzzardo.android.willyshmo.kotlintictacdoh.R.string.player_moves_first), mPlayer2Name)
        findViewById<android.view.View>(com.guzzardo.android.willyshmo.kotlintictacdoh.R.id.player_1).setOnClickListener {
            startGame(1)
        }
        findViewById<android.view.View>(com.guzzardo.android.willyshmo.kotlintictacdoh.R.id.player_2).setOnClickListener {
            startGame(2)
        }
        mButtonPlayOverNetwork = findViewById<android.view.View>(com.guzzardo.android.willyshmo.kotlintictacdoh.R.id.play_over_network) as android.widget.Button
        mButtonPlayOverNetwork!!.setOnClickListener { playOverNetwork() }

        mAdView = findViewById<android.view.View>(com.guzzardo.android.willyshmo.kotlintictacdoh.R.id.ad_two_player) as com.google.android.gms.ads.AdView
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        mAdView!!.loadAd(adRequest)
    }

    private fun startGame(player: Int) {
        val i = Intent(this, GameActivity::class.java)
        i.putExtra(
            GameActivity.START_PLAYER_HUMAN,
            if (player == 1) GameView.State.PLAYER1.value else GameView.State.PLAYER2.value
        )
        i.putExtra(GameActivity.PLAYER1_NAME, mPlayer1Name)
        i.putExtra(GameActivity.PLAYER2_NAME, mPlayer2Name)
        i.putExtra(GameActivity.PLAY_AGAINST_WILLY, "false")
        startActivity(i)
        //finish()
    }

    private fun playOverNetwork() {
        if (mPlayer1Name == "" || mPlayer1Name.equals("Player 1", ignoreCase = true)) {
            displayNameRequiredAlert()
            return
        }
        val i = android.content.Intent(
            this,
            com.guzzardo.android.willyshmo.kotlintictacdoh.PlayOverNetwork::class.java
        )
        i.putExtra(GameActivity.PLAYER1_NAME, mPlayer1Name)
        i.putExtra(GameActivity.PLAY_AGAINST_WILLY, "false")
        startActivity(i)
        finish()
    }

    private fun displayNameRequiredAlert() {
        try {
            androidx.appcompat.app.AlertDialog.Builder(this@TwoPlayerActivity)
                .setTitle(getString(R.string.enter_player_name_in_settings))
                .setNeutralButton(R.string.ok) { _, _ -> finish() }
                .setIcon(R.drawable.willy_shmo_small_icon)
                .show()
        } catch (e: Exception) {
            sendToastMessage(e.message)
        }
    }

    override fun onPause() {
        super.onPause()
        writeToLog("TwoPlayerActivity", "onPause called for TwoPlayerActivity")
    }

    override fun onDestroy() {
        super.onDestroy()
        writeToLog("TwoPlayerActivity", "onDestroy called for TwoPlayerActivity")
    }

    private fun writeToLog(filter: String, msg: String) {
        if ("true".equals(resources.getString(R.string.debug), ignoreCase = true)) {
            Log.d(filter, msg)
        }
    }

    private fun sendToastMessage(message: String?) {
        val msg = errorHandler!!.obtainMessage()
        msg.obj = message
        errorHandler!!.sendMessage(msg)
    }

    class ErrorHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(getContext(), msg.obj as String, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        var errorHandler: ErrorHandler? = null
    }
}