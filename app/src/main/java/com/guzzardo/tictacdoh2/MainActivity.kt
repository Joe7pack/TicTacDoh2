package com.guzzardo.tictacdoh2

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.vending.licensing.*
import com.guzzardo.tictacdoh2.SplashScreen.ErrorHandler
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.willyShmoApplicationContext
/* Firebase app indexing will no longer be supported by Google after August 2025.
   Consider using Android App Links instead, although I prefer patties over links
import com.google.firebase.appindexing.Action
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.FirebaseUserActions
import com.google.firebase.appindexing.Indexable
import com.google.firebase.appindexing.builders.Actions
import com.google.firebase.appindexing.builders.Indexables
*/

import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity()  {
    private var mAdView: AdView? = null

    // A handler on the UI thread.
    private var mHandler: Handler? = null
    public override fun onStart() {
        super.onStart()
        //mApplicationContext = applicationContext
        /*
        val indexableNotes = ArrayList<Indexable>()
        val noteToIndex = Indexables.noteDigitalDocumentBuilder()
            .setName(getString(R.string.note_to_index))
            .setText(getString(R.string.action_string))
            .setUrl(getString(R.string.action_url))
            .build()
        indexableNotes.add(noteToIndex)
        var notesArr = arrayOfNulls<Indexable>(indexableNotes.size)
        notesArr = indexableNotes.toArray(notesArr)
        FirebaseApp.initializeApp(this)
        FirebaseAppIndex.getInstance(this).update(*notesArr)
        FirebaseUserActions.getInstance(this).start(action)
        */
        writeToLog("MainActivity", "onStart called at " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
    }

    /*
    private val action: Action
        get() = Actions.newView(getString(R.string.action_string), getString(R.string.action_url))

    public override fun onStop() {
        FirebaseUserActions.getInstance(this).end(action)
        super.onStop()
    }
     */

    // Called when the activity is first created.
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        /* We'll put this code back in later
        mAdView = findViewById<View>(R.id.ad_main) as AdView
        val adRequest = AdRequest.Builder().build()
        mAdView!!.loadAd(adRequest)
         */
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        super.onSaveInstanceState(savedInstanceState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun writeToLog(filter: String, msg: String) {
        if ("true".equals(resources!!.getString(R.string.debug), ignoreCase = true)) {
            Log.d(filter, msg)
        }
    }
}
