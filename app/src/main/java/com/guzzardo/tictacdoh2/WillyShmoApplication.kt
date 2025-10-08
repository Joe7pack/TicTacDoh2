package com.guzzardo.tictacdoh2

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.vending.licensing.AESObfuscator
import com.google.android.vending.licensing.LicenseChecker
import com.google.android.vending.licensing.LicenseCheckerCallback
import com.google.android.vending.licensing.Policy
import com.google.android.vending.licensing.ServerManagedPolicy
import com.guzzardo.tictacdoh2.WillyShmoApplication.UserPreferences
import java.util.*

class WillyShmoApplication : AppCompatActivity(), ToastMessage {
    private var mPrizeButton: Button? = null
    private var mStatusText: TextView? = null
    private var mCheckLicenseButton: Button? = null
    private var mLicenseCheckerCallback: LicenseCheckerCallback? = null
    private var mChecker: LicenseChecker? = null
    var MSG_KEY = "message key"

    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val bundle = msg.data
            val string = bundle.getString(MSG_KEY)
            val myTextView = findViewById<View>(R.id.textView) as TextView
            myTextView.text = string
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { return@setKeepOnScreenCondition false }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        mErrorHandler = ErrorHandler()
        findViewById<View>(R.id.rules).setOnClickListener { showRules() }
        findViewById<View>(R.id.about).setOnClickListener { showAbout() }
        findViewById<View>(R.id.two_player).setOnClickListener { showTwoPlayers() }
        findViewById<View>(R.id.one_player).setOnClickListener { showOnePlayer() }
        findViewById<View>(R.id.settings_dialog).setOnClickListener { showDialogs() }
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        mApplicationContext = applicationContext
        mResources = resources
        mPlayer1Name = settings.getString(GameActivity.PLAYER1_NAME, getString(R.string.player_1)).toString()
        mPlayer2Name = settings.getString(GameActivity.PLAYER2_NAME, getString(R.string.player_2)).toString()
        mStatusText = findViewById<View>(R.id.status_text) as TextView
        mCheckLicenseButton = findViewById<View>(R.id.check_license_button) as Button
        mCheckLicenseButton!!.setOnClickListener { doCheck() }
        mConfigMap = HashMap<String?, String?>()
/*
        // Try to use more data here. ANDROID_ID is a single point of attack.
        //androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.NAME)
        // Library calls this when it's done.
        mLicenseCheckerCallback = MyLicenseCheckerCallback()
        // Construct the LicenseChecker with a policy.
        mChecker = LicenseChecker(
            this,
            ServerManagedPolicy(this, AESObfuscator(SALT, packageName, deviceId)),
            BASE64_PUBLIC_KEY
        )
 */
        var mPrizesAvailable = false
        if ("true".equals(mResources?.getString(R.string.prizesAvailable), ignoreCase = true)) {
            mPrizesAvailable = true
        }
        if (mPrizesAvailable) {
            val anim: Animation = AlphaAnimation(0.0f, 1.0f)
            anim.duration = 500 //You can manage the time of the blink with this parameter
            anim.startOffset = 20
            anim.repeatMode = Animation.REVERSE
            anim.repeatCount = Animation.INFINITE
            mPrizeButton = findViewById<View>(R.id.prizes_dialog) as Button
            mPrizeButton!!.setOnClickListener { showPrizes() }
            mPrizeButton!!.background =
                AppCompatResources.getDrawable(applicationContext, R.drawable.backwithgreenborder)
            mPrizeButton!!.startAnimation(anim)
            if (isNetworkAvailable && prizesAreAvailable) {
                mPrizeButton!!.visibility = View.VISIBLE
            } else {
                mPrizeButton!!.visibility = View.GONE
            }
            mLatitude = 0.0
            mLongitude = 0.0
            willyShmoApplicationContext = this.applicationContext
            prizesAreAvailable = false
            val willyShmoApplicationContext = willyShmoApplicationContext
            val myIntent = Intent(willyShmoApplicationContext, FusedLocationActivity::class.java)
            startActivity(myIntent)
        }
        writeToLog("MainActivity", "onCreate finished")
    }

    override fun onCreateDialog(id: Int): Dialog? {
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
        mHandler.post {
            mStatusText!!.text = result
            mCheckLicenseButton!!.isEnabled = true
        }
        sendToastMessage("Licensing result: $result")
    }

    private fun displayDialog(showRetry: Boolean) {
        mHandler.post {
            mCheckLicenseButton!!.isEnabled = true
        }
    }

    // Acquire a reference to the system Location Manager
    interface UserPreferences {
        companion object {
            const val PREFS_NAME = "TicTacDohPrefsFile"
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
            val result = (R.string.application_error).plus(errorCode)
            displayResult(result.toString())
        }
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

    public override fun onDestroy() {
        super.onDestroy()
        //mChecker!!.onDestroy()
    }

    override fun sendToastMessage(message: String?) {
        val msg = mErrorHandler!!.obtainMessage()
        msg.obj = message
        mErrorHandler!!.sendMessage(msg)
    }

    class ErrorHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(mApplicationContext, msg.obj as String, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private var mApplicationContext: Context? = null
        var mErrorHandler: ErrorHandler? = null
        private lateinit var mPrizeNames: Array<String?>
        private lateinit var mPrizeIds: Array<String?>
        private lateinit  var mBitmapImages: Array<Bitmap?>
        lateinit var imageWidths: Array<String?>
        lateinit var imageHeights: Array<String?>
        lateinit var prizeDistances: Array<String?>
        lateinit var prizeUrls: Array<String?>
        lateinit var prizeLocations: Array<String?>
        private var mConfigMap: HashMap<String?, String?>? = null
        var mLongitude = 0.0
        var mLatitude = 0.0
        private var mNetworkAvailable = false
        private var mPrizesAvailable = false
        private var mPlayersTooClose = false
        var callerActivity: ToastMessage? = null
        private var mResources: Resources? = null
        var androidId: String? = null
        var willyShmoApplicationContext: Context? = null
        //private var mGoogleApiClient: GoogleApiClient? = null
        lateinit var mPlayer1Name: String
        lateinit var mPlayer2Name: String

        private const val BASE64_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA99ZomXn6WBzKJixBIWw3TmV832CqBDlcP4XXtBl5LxOTYaxkwDBiNGKYhdwdjP/vOcKEQCnRQyK4FKRqCShGR2lnZ/h/O52g/P8ymCvk/c+cD7zR3utO8yoYB8Ssy7ia5DCRNghyz3t1N87AyLg8DhviEC9ubkUHq+zj0Tmgd35xE0mG2DC4I2KjOWoFH39YtCgm03THovIn6KIIj6pNYtRdQpluf7e6x3g3oOBa9XLk773QdJxORoTvY97z90uNgxDSo+r13FHqQQbK7nW9zRS663eqibsN6+rYXlF41fAyVp4vH5fGLRBrl7Nj4R+xD0hKNVKSckA2UVaf9UxHTQIDAQAB"

        // Generate your own 20 random bytes, and put them here.
        private val SALT = byteArrayOf(
            -26, 85, 30, -128, -112, -57, 74, -64, 32, 88, -90, -45, 88, -117, -36, -113, -11, 32, -61, 89
        )

        var prizeNames: Array<String?>
        get() = mPrizeNames
        set(prizeNames) {
            mPrizeNames = prizeNames
        }

        var prizeIds: Array<String?>
        get() = mPrizeIds
        set(prizeIds) {
            mPrizeIds = prizeIds
        }

        var bitmapImages: Array<Bitmap?>
        get() = mBitmapImages
        set(bitmapImages) {
            mBitmapImages = bitmapImages
        }

        var isNetworkAvailable: Boolean
        get() = mNetworkAvailable
        set(networkAvailable) {
            mNetworkAvailable = networkAvailable
            writeToLog("WillyShmoApplication","setNetworkAvailable(): $mNetworkAvailable")
        }

        var prizesAreAvailable: Boolean
        get() = mPrizesAvailable
        set(prizesAvailable) {
            mPrizesAvailable = prizesAvailable
            writeToLog("WillyShmoApplication","setPrizesAvailable(): $mPrizesAvailable")
        }

        var playersTooClose: Boolean
        get() = mPlayersTooClose
        set(playersTooClose) {
            mPlayersTooClose = playersTooClose
            writeToLog("WillyShmoApplication","setPlayersTooClose(): $mPlayersTooClose")
        }

        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources!!.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }

        fun setConfigMap(key: String?, value: String?) {
            mConfigMap!![key] = value
        }

        fun getConfigMap(key: String?): String? {
            return mConfigMap!![key]
        }
    }
}