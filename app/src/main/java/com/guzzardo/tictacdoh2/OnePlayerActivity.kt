package com.guzzardo.tictacdoh2

import android.content.Intent
import com.google.android.gms.ads.AdRequest
import kotlin.jvm.java

class OnePlayerActivity : android.app.Activity() {
    private var mPlayer1Name: String? = null //getString(R.string.player_1) //"Player 1"
    private var mPlayer2Name: String? = null //getString(R.string.willy_name) //"Willy"
    private var mButtonPlayer1MoveFirst: android.widget.Button? = null
    private var mButtonPlayer2MoveFirst: android.widget.Button? = null
    private var mAdView: com.google.android.gms.ads.AdView? = null

    public override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.one_player)

        mPlayer1Name = getString(R.string.player_1) //"Player 1"
        mPlayer2Name = getString(R.string.willy_name) //"Willy"

        val player1Name = intent.getStringExtra(GameActivity.PLAYER1_NAME)
        if (player1Name != null) mPlayer1Name = player1Name

        mButtonPlayer1MoveFirst = findViewById<android.view.View>(R.id.start_player) as android.widget.Button
        mButtonPlayer1MoveFirst!!.text = getString(R.string.moves_first, mPlayer1Name);
        mButtonPlayer2MoveFirst = findViewById<android.view.View>(R.id.start_comp) as android.widget.Button
        mButtonPlayer2MoveFirst!!.text = getString(R.string.moves_first, mPlayer2Name);

        findViewById<android.view.View>(R.id.start_player).setOnClickListener { startGame(true) }
        findViewById<android.view.View>(R.id.start_comp).setOnClickListener { startGame(false) }

        mAdView = findViewById<android.view.View>(R.id.ad_one_player) as com.google.android.gms.ads.AdView
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        mAdView!!.loadAd(adRequest)
    }

    private fun startGame(startWithHuman: Boolean) {
        val i = Intent(this, GameActivity::class.java)
        i.putExtra(
            GameActivity.EXTRA_START_PLAYER,
            if (startWithHuman) GameView.State.PLAYER1.value else GameView.State.PLAYER2.value
        )
        i.putExtra(GameActivity.PLAYER1_NAME, mPlayer1Name)
        i.putExtra(GameActivity.PLAYER2_NAME, mPlayer2Name)
        i.putExtra(GameActivity.PLAY_AGAINST_WILLY, "true")
        startActivity(i)
    }

    override fun onResume() {
        super.onResume()
    }
}