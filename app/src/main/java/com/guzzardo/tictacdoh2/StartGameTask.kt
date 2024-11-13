package com.guzzardo.tictacdoh2

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat.startActivity
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

class StartGameTask {

    fun main(callerActivity: PlayersOnlineActivity, playerId: Int, playerName: String, opponentPlayerId: String, opponentPlayerName: String,resources: android.content.res.Resources) =
        kotlinx.coroutines.runBlocking {
            val willyShmoApplicationContext = WillyShmoApplication.willyShmoApplicationContext
            mCallerActivity = callerActivity
            mResources = resources
            writeToLog(
                "StartGameTask",
                "main() called at: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
            )

            val intent = Intent(willyShmoApplicationContext, GameActivity::class.java)
            intent.putExtra(GameActivity.START_SERVER, "true")
            intent.putExtra(
                GameActivity.START_CLIENT,
                "true"
            ) //this will send the new game to the client
            intent.putExtra(GameActivity.PLAYER1_ID, playerId)
            intent.putExtra(GameActivity.PLAYER1_NAME, playerName)
            intent.putExtra(GameActivity.START_CLIENT_OPPONENT_ID, opponentPlayerId)
            intent.putExtra(GameActivity.PLAYER2_NAME, opponentPlayerName)
            intent.putExtra(GameActivity.START_FROM_PLAYER_LIST, "true")

            val item = GameActivity.ParcelItems(123456789, "Dr. Strangelove")
            intent.putExtra(GameActivity.PARCELABLE_VALUES, item)

            mCallerActivity.startActivity(intent)
            writeToLog("StartGameTask", "starting client and server")
        }

    companion object {
        private lateinit var mCallerActivity: PlayersOnlineActivity
        private var mResources: Resources? = null
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources!!.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }
}