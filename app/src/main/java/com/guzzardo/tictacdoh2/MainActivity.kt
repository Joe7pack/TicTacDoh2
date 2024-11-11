package com.guzzardo.tictacdoh2

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.vending.licensing.*
import com.google.firebase.FirebaseApp
import com.google.firebase.appindexing.Action
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.FirebaseUserActions
import com.google.firebase.appindexing.Indexable
import com.google.firebase.appindexing.builders.Actions
import com.google.firebase.appindexing.builders.Indexables
import com.guzzardo.android.willyshmo.kotlintictacdoh.WillyShmoApplication.Companion.isNetworkAvailable
import com.guzzardo.android.willyshmo.kotlintictacdoh.WillyShmoApplication.Companion.prizesAreAvailable
import com.guzzardo.android.willyshmo.kotlintictacdoh.WillyShmoApplication.Companion.mPlayer1Name
import com.guzzardo.android.willyshmo.kotlintictacdoh.WillyShmoApplication.Companion.mPlayer2Name
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity(), ToastMessage {
    private var mPrizeButton: Button? = null
    private var mAdView: AdView? = null
    private var mStatusText: TextView? = null
    private var mCheckLicenseButton: Button? = null
    private var mLicenseCheckerCallback: LicenseCheckerCallback? = null
    private var mChecker: LicenseChecker? = null

    // A handler on the UI thread.
    private var mHandler: Handler? = null
    public override fun onStart() {
        super.onStart()
        mApplicationContext = applicationContext
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
        writeToLog("MainActivity", "onStart called at " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()))
    }

    private val action: Action
        get() = Actions.newView(getString(R.string.action_string), getString(R.string.action_url))

    public override fun onStop() {
        FirebaseUserActions.getInstance(this).end(action)
        super.onStop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        mChecker!!.onDestroy()
    }

    // Acquire a reference to the system Location Manager
    interface UserPreferences {
        companion object {
            const val PREFS_NAME = "TicTacDohPrefsFile"
        }
    }

    // Called when the activity is first created.
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        mErrorHandler = ErrorHandler()
        findViewById<View>(R.id.rules).setOnClickListener { showRules() }
        findViewById<View>(R.id.about).setOnClickListener { showAbout() }
        findViewById<View>(R.id.two_player).setOnClickListener { showTwoPlayers() }
        findViewById<View>(R.id.one_player).setOnClickListener { showOnePlayer() }
        findViewById<View>(R.id.settings_dialog).setOnClickListener { showDialogs() }
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)

        val connectingString = R.string.about_2

        mPlayer1Name = settings.getString(GameActivity.PLAYER1_NAME, getString(R.string.player_1)).toString()
        mPlayer2Name = settings.getString(GameActivity.PLAYER2_NAME, getString(R.string.player_2)).toString()
        mStatusText = findViewById<View>(R.id.status_text) as TextView
        mCheckLicenseButton = findViewById<View>(R.id.check_license_button) as Button
        mCheckLicenseButton!!.setOnClickListener { doCheck() }
        val anim: Animation = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 500 //You can manage the time of the blink with this parameter
        anim.startOffset = 20
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        mPrizeButton = findViewById<View>(R.id.prizes_dialog) as Button
        mPrizeButton!!.setOnClickListener { showPrizes() }
        mPrizeButton!!.background = AppCompatResources.getDrawable(applicationContext, R.drawable.backwithgreenborder)
        mPrizeButton!!.startAnimation(anim)
        if (isNetworkAvailable && prizesAreAvailable) {
            mPrizeButton!!.visibility = View.VISIBLE
        } else {
            mPrizeButton!!.visibility = View.GONE
        }
        mAdView = findViewById<View>(R.id.ad_main) as AdView
        val adRequest = AdRequest.Builder().build()
        mAdView!!.loadAd(adRequest)
        mHandler = Handler(Looper.getMainLooper())
        // Try to use more data here. ANDROID_ID is a single point of attack.
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        // Library calls this when it's done.
        mLicenseCheckerCallback = MyLicenseCheckerCallback()
        // Construct the LicenseChecker with a policy.
        mChecker = LicenseChecker(
            this, ServerManagedPolicy(
                this,
                AESObfuscator(SALT, packageName, deviceId)
            ),
            BASE64_PUBLIC_KEY
        )
    }

    private fun showRules() {
        val i = Intent(this, RulesActivity::class.java)
        startActivity(i)
    }

    private fun showAbout() {
        val i = Intent(this, AboutActivity::class.java)
        startActivity(i)
    }

    private fun showOnePlayer() {
        val i = Intent(this, OnePlayerActivity::class.java)
        i.putExtra(GameActivity.PLAYER1_NAME, mPlayer1Name)
        i.putExtra(GameActivity.PLAYER2_NAME, mPlayer2Name)
        startActivity(i)
    }

    private fun showTwoPlayers() {
        val i = Intent(this, TwoPlayerActivity::class.java)
        i.putExtra(GameActivity.PLAYER1_NAME, mPlayer1Name)
        i.putExtra(GameActivity.PLAYER2_NAME, mPlayer2Name)
        startActivity(i)
    }

    private fun showDialogs() {
        val i = Intent(this, SettingsDialogs::class.java)
        i.putExtra(GameActivity.PLAYER1_NAME, mPlayer1Name)
        i.putExtra(GameActivity.PLAYER2_NAME, mPlayer2Name)
        startActivity(i)
    }

    private fun showPrizes() {
        val i = Intent(this, PrizesAvailableActivity::class.java)
        startActivity(i)
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

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        writeToLog("MainActivity", "MainActivity onActivityResult")
    }

    private fun writeToLog(filter: String, msg: String) {
        if ("true".equals(resources!!.getString(R.string.debug), ignoreCase = true)) {
            Log.d(filter, msg)
        }
    }

    override fun onCreateDialog(id: Int): Dialog {
        val bRetry = id == 1
        return AlertDialog.Builder(this)
            .setTitle(R.string.unlicensed_dialog_title)
            .setMessage(if (bRetry) R.string.unlicensed_dialog_retry_body else R.string.unlicensed_dialog_body)
            .setPositiveButton(
                if (bRetry) R.string.retry_button else R.string.buy_button,
                object : DialogInterface.OnClickListener {
                    var mRetry = bRetry
                    override fun onClick(dialog: DialogInterface, which: Int) {
                        if (mRetry) {
                            doCheck()
                        } else {
                            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://market.android.com/details?id=$packageName"))
                            startActivity(marketIntent)
                        }
                    }
                })
            .setNegativeButton(R.string.quit_button) { _, _ -> finish() }.create()
    }

    private fun doCheck() {
        mCheckLicenseButton!!.isEnabled = false
        //setProgressBarIndeterminateVisibility(true)
        //setSupportProgressBarIndeterminateVisibility(true) //see requestWindowFeature call above
        mStatusText!!.setText(R.string.checking_license)
        mChecker!!.checkAccess(mLicenseCheckerCallback)
    }

    private fun displayResult(result: String) {
        mHandler!!.post {
            mStatusText!!.text = result
            mCheckLicenseButton!!.isEnabled = true
        }
        sendToastMessage("Licensing result: $result")
    }

    private fun displayDialog(showRetry: Boolean) {
        mHandler!!.post {
            mCheckLicenseButton!!.isEnabled = true
        }
    }

    private inner class MyLicenseCheckerCallback : LicenseCheckerCallback {
        override fun allow(policyReason: Int) {
            if (isFinishing) {
                // Don't update UI if Activity is finishing.
                return
            }
            // Should allow user access.
            displayResult(getString(R.string.allow))
        }

        override fun dontAllow(policyReason: Int) {
            if (isFinishing) {
                // Don't update UI if Activity is finishing.
                return
            }
            displayResult(getString(R.string.dont_allow))
            // Should not allow access. In most cases, the app should assume
            // the user has access unless it encounters this. If it does,
            // the app should inform the user of their unlicensed ways
            // and then either shut down the app or limit the user to a
            // restricted set of features.
            // In this example, we show a dialog that takes the user to Market.
            // If the reason for the lack of license is that the service is
            // unavailable or there is another problem, we display a
            // retry button on the dialog and a different message.
            displayDialog(policyReason == Policy.RETRY)
        }

        override fun applicationError(errorCode: Int) {
            if (isFinishing) {
                // Don't update UI if Activity is finishing.
                return
            }
            // This is a polite way of saying the developer made a mistake
            // while setting up or calling the license checker library.
            // Please examine the error code and fix the error.
            val result = String.format(getString(R.string.application_error, errorCode.toString()))
            //val result = " applicationError: $errorCode"
            displayResult(result)
        }
    }

    companion object {
        private var mApplicationContext: Context? = null
        var mErrorHandler: ErrorHandler? = null
        private const val BASE64_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA99ZomXn6WBzKJixBIWw3TmV832CqBDlcP4XXtBl5LxOTYaxkwDBiNGKYhdwdjP/vOcKEQCnRQyK4FKRqCShGR2lnZ/h/O52g/P8ymCvk/c+cD7zR3utO8yoYB8Ssy7ia5DCRNghyz3t1N87AyLg8DhviEC9ubkUHq+zj0Tmgd35xE0mG2DC4I2KjOWoFH39YtCgm03THovIn6KIIj6pNYtRdQpluf7e6x3g3oOBa9XLk773QdJxORoTvY97z90uNgxDSo+r13FHqQQbK7nW9zRS663eqibsN6+rYXlF41fAyVp4vH5fGLRBrl7Nj4R+xD0hKNVKSckA2UVaf9UxHTQIDAQAB"

        // Generate your own 20 random bytes, and put them here.
        private val SALT = byteArrayOf(
            -26, 85, 30, -128, -112, -57, 74, -64, 32, 88, -90, -45, 88, -117, -36, -113, -11, 32, -61, 89
        )
    }
}