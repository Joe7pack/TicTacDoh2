package com.guzzardo.tictacdoh2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.multidex.BuildConfig
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.guzzardo.tictacdoh2.PermissionUtil.PermissionAskListener
import com.guzzardo.tictacdoh2.PermissionUtil.checkPermission
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mLatitude
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mLongitude
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.willyShmoApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FusedLocationActivity : android.app.Activity(), ToastMessage {
    private lateinit var mCallerActivity: FusedLocationActivity
    /**
     * Provides access to the Fused Location Provider API.
     */
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    //private val mLocationRequest: LocationRequest? = null
    private lateinit var mLocationRequest: LocationRequest //= null

    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private var mLocationSettingsRequest: LocationSettingsRequest? = null

    /**
     * Callback for Location events.
     */
    private val mLocationCallback: LocationCallback? = null

    /**
     * Represents a geographical location.
     */
    private var mCurrentLocation: Location? = null
    private var mLatitudeLabel: String? = null
    private var mLongitudeLabel: String? = null
    private var mLastUpdateTimeLabel: String? = null

    /**
     * Tracks the status of the location updates request. Value changes when the user presses the
     * Start Updates and Stop Updates buttons.
     */
    private var mRequestingLocationUpdates: Boolean? = null

    /**
     * Time when the location was updated represented as a String.
     */
    private var mLastUpdateTime: String? = null
    private var myLatitude = 0.0
    private var myLongitude = 0.0
    private var pgsBar: ProgressBar? = null
    private var handlerThread: HandlerThread? = null
    private var looper: Looper? = null
    private var looperHandler: Handler? = null
    val START_LOCATION_CHECK_ACTION = 0
    val MAIN_ACTIVITY_ACTION = 1
    val COMPLETED_LOCATION_CHECK_ACTION = 2
    val START_GET_PRIZES_FROM_SERVER = 3
    val FORMATTING_PRIZE_DATA = 4
    val PRIZES_LOADED = 5
    val PRIZE_LOAD_IN_PROGRESS = 6
    val PRIZES_READY_TO_DISPLAY = 7

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_with_guidelines)
        mCallerActivity = this
        pgsBar = findViewById<View>(R.id.progressBar) as ProgressBar
        pgsBar!!.progress = 10
        // Set labels
        mLatitudeLabel = resources.getString(R.string.latitude_label)
        mLongitudeLabel = resources.getString(R.string.longitude_label)
        mLastUpdateTimeLabel = resources.getString(R.string.last_update_time_label)
        mRequestingLocationUpdates = false
        mLastUpdateTime = ""

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        buildLocationSettingsRequest()
        mErrorHandler = ErrorHandler()
        startMyThread()
        checkPermissions()
        writeToLog("FusedLocationActivity", "onCreate finished")
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private fun updateValuesFromBundle(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(KEY_REQUESTING_LOCATION_UPDATES)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(KEY_REQUESTING_LOCATION_UPDATES)
            }

            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION, Location::class.java)
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(KEY_LAST_UPDATED_TIME_STRING)) {
                mLastUpdateTime = savedInstanceState.getString(KEY_LAST_UPDATED_TIME_STRING)
            }
        }
    }

    /**
     * Uses a [com.google.android.gms.location.LocationSettingsRequest.Builder] to build
     * a [com.google.android.gms.location.LocationSettingsRequest] that is used for checking
     * if a device has the needed location settings.
     */
    private fun buildLocationSettingsRequest() {
        mLocationRequest = LocationRequest.Builder(100L).build()
        val builder = LocationSettingsRequest.Builder()
        //TODO - uncomment when ready to test with a real phone
        builder.addLocationRequest(mLocationRequest)
        mLocationSettingsRequest = builder.build()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                RESULT_OK -> writeToLog("FusedLocationActivity", "User agreed to make required location settings changes.")
                RESULT_CANCELED -> {
                    writeToLog("FusedLocationActivity", "User chose not to make required location settings changes.")
                    mRequestingLocationUpdates = false
                }
            }
        }
    }

    /**
     * Handles the Start Updates button and requests start of location updates. Does nothing if
     * updates have already been requested.
     */
    //note - this handler method is coded right in the view xml file!
    fun startUpdatesButtonHandler() {
        if (!mRequestingLocationUpdates!!) {
            mRequestingLocationUpdates = true
        }
    }

    //@RequiresApi(api = Build.VERSION_CODES.M)
    private fun checkPermissions() {
        checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION,
            object : PermissionAskListener {
                override fun onPermissionAsk() {
                    writeToLog("FusedLocationActivity", "onPermissionAsk() called.")
                    ActivityCompat.requestPermissions(
                        this@FusedLocationActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                    )
                }

                override fun onPermissionPreviouslyDenied() {
                    //show a dialog explaining permission and then request permission
                    writeToLog("FusedLocationActivity", "requestPermissions() called.")
                    requestPermissions()
                }

                override fun onPermissionDisabled() {
                    //TODO - may want to call requestPermissions() again?
                    writeToLog("FusedLocationActivity", "onPermissionDisabled() called.")
                    sendToastMessage("Permission Disabled")
                    requestPermissions()
                }

                override fun onPermissionGranted() {
                    writeToLog("FusedLocationActivity", "onPermissionGranted() called.")
                    location
                }
            })
    } // Got last known location. In some rare situations this can be null.

    // TODO: Consider calling ActivityCompat#requestPermissions
    // here to request the missing permissions, and then overriding
    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
    //                                          int[] grantResults)
    // to handle the case where the user grants the permission. See the documentation
    // for ActivityCompat#requestPermissions for more details.
    // return;
    private val location: Unit
        get() {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                // return;
                writeToLog("FusedLocationActivity", "Permission not granted")
            }
            mFusedLocationClient!!.lastLocation.addOnSuccessListener(this) { location -> // Got last known location. Will always be null when called from emulator.
                if (location != null) {
                    mLatitude = location.latitude
                    mLongitude = location.longitude
                    writeToLog("FusedLocationActivity", "My latitude: $myLatitude my Longitude: $myLongitude")
                    CoroutineScope(Dispatchers.Default).launch {
                        val getPrizeListTask = GetPrizeListTask()
                        getPrizeListTask.main(mCallerActivity, resources)
                    }
                } else {
                    val willyShmoApplicationContext = WillyShmoApplication.willyShmoApplicationContext
                    val myIntent = Intent(willyShmoApplicationContext, MainActivity::class.java)
                    startActivity(myIntent)
                    finish()
                }
            }
            setStartLocationLookupCompleted()
        }

    public override fun onResume() {
        super.onResume()
    }

    // Stores activity data in the Bundle.
    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean(KEY_REQUESTING_LOCATION_UPDATES, mRequestingLocationUpdates!!)
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation)
        savedInstanceState.putString(KEY_LAST_UPDATED_TIME_STRING, mLastUpdateTime)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
    }

    /**
     * Shows a [Snackbar].
     *
     * @param mainTextStringId The id for the string resource for the Snackbar text.
     * @param actionStringId   The text of the action item.
     * @param listener         The listener associated with the Snackbar action.
     */
    private fun showSnackbar(mainTextStringId: Int, actionStringId: Int, listener: View.OnClickListener) {
        Snackbar.make(
            findViewById(android.R.id.content),
            getString(mainTextStringId),
            Snackbar.LENGTH_INDEFINITE
        )
            .setBackgroundTint(Color.WHITE)
            .setActionTextColor(Color.BLACK)
            .setAction(getString(actionStringId), listener).show()
        val snackBarView = findViewById<View>(android.R.id.content)
        //snackBarView.setBackgroundColor(Color.GREEN);
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            writeToLog("FusedLocationActivity", "Displaying permission rationale to provide additional context.")
            showSnackbar(R.string.permission_rationale, android.R.string.ok) { // Request permission
                ActivityCompat.requestPermissions(
                    this@FusedLocationActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                )
            }
        } else {
            writeToLog("FusedLocationActivity", "Requesting permission.")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                this@FusedLocationActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),  //new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        try {
            writeToLog(
                "FusedLocationActivity",
                "at start of onRequestPermissionsResult request code = " + requestCode + ", grantResults[0] = " + grantResults[0]
            )
            if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
                writeToLog(
                    "FusedLocationActivity",
                    "inside onRequestPermissionsResult request code = " + requestCode + ", grantResults[0] = " + grantResults[0]
                )
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    location
                    writeToLog(
                        "FusedLocationActivity",
                        "location permission granted: " + requestCode + ", grantResults[0] = " + grantResults[0]
                    )
                } else {
                    writeToLog(
                        "FusedLocationActivity",
                        "location permission DENIED! " + requestCode + ", grantResults[0] = " + grantResults[0]
                    )
                    // Permission denied.

                    // Notify the user via a SnackBar that they have rejected a core permission for the
                    // app, which makes the Activity useless. In a real app, core permissions would
                    // typically be best requested during a welcome-screen flow.

                    // Additionally, it is important to remember that a permission might have been
                    // rejected without asking the user for permission (device policy or "Never ask
                    // again" prompts). Therefore, a user interface affordance is typically implemented
                    // when permissions are denied. Otherwise, your app could appear unresponsive to
                    // touches or interactions which have required permissions.
                    showSnackbar(R.string.permission_denied_explanation)
                }
            }
        } catch (e: Exception) {
            writeToLog("FusedLocationActivity","onRequestPermissionsResult exception: $e")
        }
    }

    private fun showSnackbar(displayStringResource: Int) {
        showSnackbar(displayStringResource, R.string.settings) { view: View? ->
            // Build intent that displays the App settings screen.
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            val uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
            intent.data = uri
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    class ErrorHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(willyShmoApplicationContext, msg.obj as String, Toast.LENGTH_LONG).show()
        }
    }

    override fun sendToastMessage(message: String?) {
        val msg = mErrorHandler!!.obtainMessage()
        msg.obj = message
        mErrorHandler!!.sendMessage(msg)
    }

    private fun startMyThread() {
        handlerThread = HandlerThread("MyHandlerThread")
        handlerThread!!.start()
        looper = handlerThread!!.looper
        looperHandler = object : Handler(looper!!) {
            override fun handleMessage(msg: Message) {
                //TODO - consolidate this code a little better
                when (msg.what) {
                    START_LOCATION_CHECK_ACTION -> {
                        setProgressBar(20)
                    }
                    COMPLETED_LOCATION_CHECK_ACTION -> {
                        setProgressBar(30)
                    }
                    START_GET_PRIZES_FROM_SERVER -> {
                        setProgressBar(40) //most of the waiting is here
                    }
                    FORMATTING_PRIZE_DATA -> {
                        setProgressBar(60)
                    }
                    PRIZE_LOAD_IN_PROGRESS -> {
                        setProgressBar(70)
                    }
                    PRIZES_LOADED -> {
                        setProgressBar(80)
                    }
                    PRIZES_READY_TO_DISPLAY -> {
                        setProgressBar(90)
                    }
                    MAIN_ACTIVITY_ACTION -> {
                        setProgressBar(100)
                    }
                    else -> {
                    }
                }
            }
        }
    }

    private fun setStartLocationLookup() {
        val msg = looperHandler!!.obtainMessage(START_LOCATION_CHECK_ACTION)
        looperHandler!!.sendMessage(msg)
    }

    private fun setStartLocationLookupCompleted() {
        val msg = looperHandler!!.obtainMessage(COMPLETED_LOCATION_CHECK_ACTION)
        looperHandler!!.sendMessage(msg)
    }

    fun setGettingPrizesCalled() {
        val msg = looperHandler!!.obtainMessage(START_GET_PRIZES_FROM_SERVER)
        looperHandler!!.sendMessage(msg)
    }

    fun prizeLoadInProgress() {
        val msg = looperHandler!!.obtainMessage(PRIZE_LOAD_IN_PROGRESS)
        looperHandler!!.sendMessage(msg)
    }

    fun setPrizesRetrievedFromServer() {
        val msg = looperHandler!!.obtainMessage(FORMATTING_PRIZE_DATA)
        looperHandler!!.sendMessage(msg)
    }

    fun setPrizesLoadIntoObjects() {
        val msg = looperHandler!!.obtainMessage(PRIZES_LOADED)
        looperHandler!!.sendMessage(msg)
    }

    fun setPrizesLoadedAllDone() {
        val msg = looperHandler!!.obtainMessage(PRIZES_READY_TO_DISPLAY)
        looperHandler!!.sendMessage(msg)
    }

    private fun setProgressBar(progress: Int) {
        pgsBar!!.progress = progress
        writeToLog("FusedLocationActivity", "progress bar set to: $progress")
    }

    companion object {
        /*
      Using location settings.
      <p/>
      Uses the {@link com.google.android.gms.location.SettingsApi} to ensure that the device's system
      settings are properly configured for the app's location needs. When making a request to
      Location services, the device's system settings may be in a state that prevents the app from
      obtaining the location data that it needs. For example, GPS or Wi-Fi scanning may be switched
      off. The {@code SettingsApi} makes it possible to determine if a device's system settings are
      adequate for the location request, and to optionally invoke a dialog that allows the user to
      enable the necessary settings.
      <p/>
      This sample allows the user to request location updates using the ACCESS_FINE_LOCATION setting
      (as specified in AndroidManifest.xml).
     */
        /**
         * Code used in requesting runtime permissions.
         */
        // private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
        private const val MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 454

        /**
         * Constant used in the location settings dialog.
         */
        private const val REQUEST_CHECK_SETTINGS = 0x1

        // Keys for storing activity state in the Bundle.
        private const val KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates"
        private const val KEY_LOCATION = "location"
        private const val KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string"
        var mErrorHandler: ErrorHandler? = null
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(willyShmoApplicationContext!!.resources.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }
}