package com.guzzardo.tictacdoh2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import java.math.BigDecimal

class PrizeListAdapter(
    context: android.content.Context?,
    activity: androidx.fragment.app.FragmentActivity?,
    private val distanceUnitOfMeasure: String,
    private val imageDescription: Array<String?>,
    private val imageBitmap: Array<android.graphics.Bitmap?>,
    private val imageWidth: Array<String?>,
    private val imageHeight: Array<String?>,
    private val prizeDistance: Array<String?>,
    private val prizeLocation: Array<String?>,
    private val resources: android.content.res.Resources
) : android.widget.BaseAdapter(), ToastMessage {

        override fun getCount(): Int {
            return imageDescription.size
        }

        override fun getItem(position: Int): Any {
            return position
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            var vi = convertView
            try {
                if (convertView == null) {
                    vi = inflater!!.inflate(R.layout.prizes, null)
                }
            } catch (e: Exception) {
                sendToastMessage(mApplicationContext!!.getString(R.string.prize_list_inflator_error) + e.message)
                writeToLog("PrizeListAdapter", "getView: $e.message")
            }
            val prizeDescription = vi?.findViewById<View>(R.id.prize_description) as TextView
            prizeDescription.text = imageDescription[position] ?: ""
            prizeDescription.setBackgroundColor(Color.LTGRAY)
            val image = vi.findViewById<View>(R.id.prize_image) as ImageView
            val width = imageWidth[position]?.let { Integer.valueOf(it) }
            val height = imageHeight[position]?.let { Integer.valueOf(it) }
            image.layoutParams = width?.let { height?.let { it1 -> LinearLayout.LayoutParams(it, it1) } }
            image.setImageBitmap(imageBitmap[position])
            val textDistance = vi.findViewById<View>(R.id.prize_distance) as TextView
            when {
                prizeLocation[position] == "1" -> {
                    val distance = prizeDistance[position] //this is in miles
                    var convertedDistance = distance!!.toDouble()
                    if (distanceUnitOfMeasure == "K") {
                        convertedDistance = convertedDistance.times(1.60934) //number of kilometers per mile
                    }
                    var decimal = BigDecimal(convertedDistance)
                    decimal = decimal.setScale(2, BigDecimal.ROUND_UP)
                    textDistance.text = decimal.toString()
                }
                prizeLocation[position] == "0" -> {
                    textDistance.text = resources.getString(R.string.not_applicable)
                }
                prizeLocation[position] == "2" -> {
                    textDistance.text = resources.getString(R.string.multiple_locations)
                }
                else -> {
                    textDistance.text = resources.getString(R.string.distance_unknown)
                }
            }
            return vi
        }

        override fun sendToastMessage(message: String?) {
            val msg = mErrorHandler!!.obtainMessage()
            msg.obj = message
            FusedLocationActivity.mErrorHandler!!.sendMessage(msg)
        }

    class ErrorHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(
                mApplicationContext,
                msg.obj as String,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private var inflater: LayoutInflater? = null
        var mErrorHandler: ErrorHandler? = null
        private var mApplicationContext: Context? = null
        private lateinit var mResources: Resources
        //private lateinit var mCallerActivity: Activity

        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }

    init {
        inflater = activity!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        //mCallerActivity = activity
        mErrorHandler = ErrorHandler()
        mResources = resources
        mApplicationContext = context
    }
}