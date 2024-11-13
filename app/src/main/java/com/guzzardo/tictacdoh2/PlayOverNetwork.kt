package com.guzzardo.tictacdoh2

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.guzzardo.tictacdoh2.MainActivity.UserPreferences
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.androidId
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mLatitude
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mLongitude
import kotlinx.coroutines.*

class PlayOverNetwork: android.app.Activity(), ToastMessage {
    private lateinit var mPlayer1Name: String
    private lateinit var mCallerActivity: PlayOverNetwork

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mCallerActivity = this
        mApplicationContext = applicationContext
        mPlayOverNetwork = this
        mErrorHandler = ErrorHandler()
        mResources = resources
        sharedPreferences
        val player1Name = intent.getStringExtra(GameActivity.PLAYER1_NAME)
        mPlayer1Name = player1Name ?: "Corriander"
        if (WillyShmoApplication.getConfigMap("RabbitMQQueuePrefix").equals(null)) {
            writeToLog("PlayOverNetwork", "onCreate() unable to communicate with host server, gonna finish")
            sendToastMessage(getString(R.string.communication_lost))
            finish()
        }
        writeToLog("PlayOverNetwork", "onCreate() finished")
    }

    override fun onStart() {
        super.onStart()
        writeToLog("PlayOverNetwork", "onStart() called")
        CleanUpRabbitMQQueue(mPlayer1Id, mPlayer1Name, mResources!!, this).main()
    }

    override fun onResume() {
        super.onResume()
        writeToLog("PlayOverNetwork", "onResume() called")
        mHostWaitDialog = createHostWaitDialog()
        mHostWaitDialog!!.show()
        if (mPlayer1Id == 0) {
            //setSharedPreferences()
            addMyselfToPlayerList()
            //mPlayer1Id = -1 //lets see if this prevents adding a new player twice
            //I think its a database connection problem, seems to work fine about half the time or so
            writeToLog("PlayOverNetwork", "onResume() added brand new player")
            val newPlayerMessage = getString(R.string.new_player_added, mPlayer1Name);
            sendToastMessage(newPlayerMessage)
            finish()
        } else {
            var returnMessage: String? = null
            runBlocking {
                val job = CoroutineScope(Dispatchers.IO).launch {
                    val webServerInterfaceUsersOnlineTask = WebServerInterfaceUsersOnlineTask()
                    returnMessage = webServerInterfaceUsersOnlineTask.main(mCallerActivity, mPlayer1Name, resources, Integer.valueOf(mPlayer1Id))
                }
                job.join()
            }
            writeToLog("PlayOverNetwork", "WebServerInterfaceUsersOnlineTask_called, return value: $returnMessage")
            if (returnMessage.isNullOrEmpty() || returnMessage.equals("noResultReturned")) {
                mHostUnavailableDialog = createHostUnavailableDialog()
                mHostUnavailableDialog!!.show()
            } else {
                finish()
            }
        }
        writeToLog("PlayOverNetwork", "onResume() finished")
    }

    private fun addMyselfToPlayerList() {
        // add a new entry to the GamePlayer table
        writeToLog("PlayOverNetwork", "addMyselfToPlayerList() called")
        val androidId = "?deviceId=$androidId"
        val latitude = "&latitude=$mLatitude"
        val longitude = "&longitude=$mLongitude"
        val trackingInfo = androidId + latitude + longitude
        val url = "/gamePlayer/createAndroid/$trackingInfo&userName=$mPlayer1Name"
        CoroutineScope( Dispatchers.Default).launch {
            val webServerInterfaceNewPlayerTask =  WebServerInterfaceNewPlayerTask()
            webServerInterfaceNewPlayerTask.main(mCallerActivity as Context, url, mPlayer1Name, resources)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mHostWaitDialog != null) {
            mHostWaitDialog!!.dismiss()
        }
        if (mHostUnavailableDialog != null) {
            mHostUnavailableDialog!!.dismiss()
        }
        writeToLog("PlayOverNetwork", "onDestroy() finished")
    }

    private fun createHostWaitDialog(): AlertDialog {
        if (mHostWaitDialog != null) {
            mHostWaitDialog!!.dismiss()
        }
        val hostingDescription = getString(R.string.wait_for_opponent)
        return AlertDialog.Builder(this@PlayOverNetwork)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(hostingDescription)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.alert_dialog_cancel)) { _, _ -> finish() }
            .create()
    }

    private fun createHostUnavailableDialog(): AlertDialog {
        if (mHostUnavailableDialog != null) {
            mHostUnavailableDialog!!.dismiss()
        }
        val hostingDescription = getString(R.string.host_connect_failure)
        return AlertDialog.Builder(this@PlayOverNetwork)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(hostingDescription)
            .setMessage(getString(R.string.host_connect_failure_2))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.alert_dialog_cancel)) { _, _ -> finish() }
            .create()
    }

    private val sharedPreferences: Unit
        get() {
            val settings = getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            mPlayer1Id = settings.getInt(GameActivity.PLAYER1_ID, 0)
            mPlayer1Name = settings.getString(GameActivity.PLAYER1_NAME, null) ?: "CheeseSteak"
        }

    private fun setSharedPreferences() {
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = settings.edit()
        editor.putString(GameActivity.PLAYER1_NAME, mPlayer1Name)
        // Commit the edits!
        editor.apply()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("gn_player1_Id", mPlayer1Id)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mPlayer1Id = savedInstanceState.getInt("gn_player1_Id")
    }

    class ErrorHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(mApplicationContext, msg.obj as String, Toast.LENGTH_LONG).show()
        }
    }

    override fun sendToastMessage(message: String?) {
        val msg = mErrorHandler!!.obtainMessage()
        msg.obj = message
        mErrorHandler!!.sendMessage(msg)
    }

    companion object {
        private var mApplicationContext: Context? = null
        private var mPlayer1Id = 0
        private var mResources: Resources? = null
        var mErrorHandler: ErrorHandler? = null
        private var mHostWaitDialog: AlertDialog? = null
        private var mHostUnavailableDialog: AlertDialog? = null
        private var mPlayOverNetwork: PlayOverNetwork? = null
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources!!.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }
}