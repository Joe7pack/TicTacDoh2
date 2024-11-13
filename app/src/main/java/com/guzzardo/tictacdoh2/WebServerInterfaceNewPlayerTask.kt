package com.guzzardo.tictacdoh2

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.guzzardo.tictacdoh2.MainActivity.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject

class WebServerInterfaceNewPlayerTask {
    private var mCallerActivity: android.content.Context? = null
    private lateinit var mToastMessage: ToastMessage
    private var mPlayerName: String? = null
    private var mPlayerId: Int? = null

    fun main(callerActivity: android.content.Context, url: String, playerName: String, resources: android.content.res.Resources) =
        kotlinx.coroutines.runBlocking {
            mCallerActivity = callerActivity
            mToastMessage = callerActivity as ToastMessage
            mResources = resources
            mPlayerName = playerName
            writeToLog("WebServerInterfaceNewPlayerTask", "main function called")
            try {
                val newUser = SendMessageToAppServer.main(url, mToastMessage, mResources, false)
                mPlayerId = getNewUserId(newUser)
            } catch (e: Exception) {
                writeToLog(
                    "WebServerInterfaceNewPlayerTask",
                    "doInBackground exception called " + e.message
                )
                mToastMessage.sendToastMessage(e.message)
            }
            findOtherPlayersCurrentlyOnline()
        }

    private fun findOtherPlayersCurrentlyOnline() {
        try {
            writeToLog("WebServerInterfaceNewPlayerTask", "findOtherPlayersCurrentlyOnline() called playerId: $mPlayerId")
            if (mPlayerId == null) {
                writeToLog("WebServerInterfaceNewPlayerTask", "findOtherPlayersCurrentlyOnline() returned null, this is very bad!!")
                mToastMessage.sendToastMessage("findOtherPlayersCurrentlyOnline() returned null, this is very bad!!")
                return
            }
            val settings = mCallerActivity!!.getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = settings.edit()
            editor.putInt(GameActivity.PLAYER1_ID, mPlayerId!!)
            // Commit the edits!
            editor.apply()
            CoroutineScope( Dispatchers.Default).launch {
                val webServerInterfaceUsersOnlineTask = WebServerInterfaceUsersOnlineTask()
                webServerInterfaceUsersOnlineTask.main(mCallerActivity, mPlayerName, mResources, mPlayerId!!)
            }
        } catch (e: Exception) {
            writeToLog("WebServerInterfaceNewPlayerTask", "onPostExecute exception called " + e.message)
            mToastMessage.sendToastMessage(e.message)
        }
    }

    private fun getNewUserId(newUser: String): Int {
        try {
            val jsonObject = JSONObject(newUser)
            val userObject = jsonObject.getJSONObject("User")
            val userId = userObject.getString("id")
            return userId.toInt()
        } catch (e: JSONException) {
            writeToLog("WebServerInterfaceNewPlayerTask", "getNewUserId exception called " + e.message)
            mToastMessage.sendToastMessage(e.message)
        }
        return 0
    }

    companion object {
        private lateinit var mResources: Resources
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }
}