package com.guzzardo.tictacdoh2

import android.app.*
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.guzzardo.tictacdoh2.Utils.getLocationText
import com.guzzardo.tictacdoh2.Utils.getLocationTitle
import com.guzzardo.tictacdoh2.Utils.requestingLocationUpdates
import com.guzzardo.tictacdoh2.Utils.setRequestingLocationUpdates

/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 *
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 *
 * This sample show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification associated with that service is removed.
 */
class LocationUpdatesService : android.app.Service() {
    private val mBinder: android.os.IBinder = LocalBinder()

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false
    private var mNotificationManager: NotificationManager? = null

    // Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
    private lateinit var mLocationRequest: LocationRequest.Builder

    // Provides access to the Fused Location Provider API.
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    // Callback for changes in location.
    private lateinit var mLocationCallback: LocationCallback
    private var mServiceHandler: Handler? = null

    // The current location.
    private var mLocation: Location? = null
    override fun onCreate() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.lastLocation)
            }
        }
        createLocationRequest()
        lastLocation
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            // Create the channel for the notification
            val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
            // Set the Notification Channel for the Notification Manager.
            mNotificationManager!!.createNotificationChannel(mChannel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        writeToLog(TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false)

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates()
            stopSelf()
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    override fun onBind(intent: Intent): IBinder {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        writeToLog(TAG, "in onBind()")
        stopForeground(STOP_FOREGROUND_REMOVE)
        mChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        writeToLog(TAG, "in onRebind()")
        stopForeground(STOP_FOREGROUND_REMOVE)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        writeToLog(TAG, "Last client unbound from service")

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && requestingLocationUpdates(this)) {
            writeToLog(TAG, "Starting foreground service")
            /*
            // TODO(developer). If targeting O, use the following code.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                mNotificationManager.startServiceInForeground(new Intent(this,
                        LocationUpdatesService.class), NOTIFICATION_ID, getNotification());
            } else {
                startForeground(NOTIFICATION_ID, getNotification());
            }
             */startForeground(NOTIFICATION_ID, notification)
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        mServiceHandler!!.removeCallbacksAndMessages(null)
    }

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * [SecurityException].

    fun requestLocationUpdates() {
        writeToLog(TAG, "Requesting location updates")
        setRequestingLocationUpdates(this, true)
        startService(Intent(applicationContext, LocationUpdatesService::class.java))
        try {
            mFusedLocationClient!!.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback, Looper.myLooper()
            )
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, false)
            writeToLog(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }
    */

    /**
     * Removes location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    fun removeLocationUpdates() {
        writeToLog(TAG, "Removing location updates")
        try {
            mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
            setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, true)
            writeToLog(TAG, "Lost location permission. Could not remove updates. $unlikely")
        }
    }

    private val notification: Notification
        get() {
            val intent = Intent(this, LocationUpdatesService::class.java)
            val text: CharSequence = getLocationText(mLocation)

            // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

            // The PendingIntent that leads to a call to onStartCommand() in this service.
            val servicePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            // The PendingIntent to launch activity.
            val activityPendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, "abcd")
                .addAction(R.drawable.ic_launcher_foreground, getString(R.string.launch_activity), activityPendingIntent)
                .addAction(R.drawable.ic_launcher_foreground, getString(R.string.remove_location_updates), servicePendingIntent)
                .setContentText(text)
                .setContentTitle(getLocationTitle(this))
                .setOngoing(true)
                .setPriority(IMPORTANCE_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())

            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID) // Channel ID
            }
            return builder.build()
        }

    private val lastLocation: Unit
        get() {
            try {
                mFusedLocationClient!!.lastLocation
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            mLocation = task.result
                        } else {
                            Log.w(TAG, "Failed to get location.")
                        }
                    }
            } catch (unlikely: SecurityException) {
                Log.e(TAG, "Lost location permission.$unlikely")
            }
        }

    private fun onNewLocation(location: Location?) {
        writeToLog(TAG, "New location: $location")
        mLocation = location

        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager!!.notify(NOTIFICATION_ID, notification)
        }
    }

    // Sets the location request parameters.
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest.Builder(100)
            .setMaxUpdates(Integer.MAX_VALUE)
            .setIntervalMillis(100)
            .setMinUpdateIntervalMillis(50)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    // Returns true if this is a foreground service.
    @Suppress("DEPRECATION")
    fun serviceIsRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    private fun writeToLog(filter: String, msg: String) {
        if ("true".equals(resources.getString(R.string.debug), ignoreCase = true)) {
            Log.d(filter, msg)
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice"
        private val TAG = LocationUpdatesService::class.java.simpleName

        // The name of the channel for notifications.
        private const val CHANNEL_ID = "channel_01"
        const val ACTION_BROADCAST = PACKAGE_NAME + ".broadcast"
        const val EXTRA_LOCATION = PACKAGE_NAME + ".location"
        private const val EXTRA_STARTED_FROM_NOTIFICATION = "$PACKAGE_NAME.started_from_notification"

        // The desired interval for location updates. Inexact. Updates may be more or less frequent.
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

        // The fastest rate for active location updates. Updates will never be more frequent than this value.
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2

        // The identifier for the notification displayed for the foreground service.
        private const val NOTIFICATION_ID = 12345678
    }
}