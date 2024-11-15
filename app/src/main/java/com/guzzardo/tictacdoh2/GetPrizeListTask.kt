package com.guzzardo.tictacdoh2

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// A Coroutine task that will be used to get a list of available prizes

class GetPrizeListTask {
    private lateinit var mCallerActivity: FusedLocationActivity
    private var mPrizesAvailable: String? = null

    fun main(callerActivity: FusedLocationActivity, resources: android.content.res.Resources) =
        kotlinx.coroutines.runBlocking {
            mCallerActivity = callerActivity
            mResources = resources
            writeToLog(
                "GetPrizeListTask",
                "main() called at: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            val longitude = WillyShmoApplication.mLongitude
            val latitude = WillyShmoApplication.mLatitude
            mCallerActivity.setGettingPrizesCalled()
            val urlData = "/prize/getPrizesByDistance/?longitude=$longitude&latitude=$latitude"
            try {
                mPrizesAvailable = SendMessageToAppServer.main(
                    urlData,
                    mCallerActivity as ToastMessage,
                    resources,
                    false
                )
                mCallerActivity.setPrizesRetrievedFromServer()
            } catch (e: Exception) {
                writeToLog("GetPrizeListTask", "doInBackground: " + e.message)
                mCallerActivity.sendToastMessage("GetPrizeListTask playing without host server")
            }
            writeToLog("GetPrizeListTask", "Prizes available: $mPrizesAvailable")
            if (mPrizesAvailable != null && mPrizesAvailable!!.length > 20) {
                processRetrievedPrizeList()
            }
            val willyShmoApplicationContext = WillyShmoApplication.willyShmoApplicationContext
            val myIntent = Intent(willyShmoApplicationContext, MainActivity::class.java)
            mCallerActivity.startActivity(myIntent)
            mCallerActivity.finish()
        }

    private fun processRetrievedPrizeList() {
        try {
            writeToLog("GetPrizeListTask","processRetrievedPrizeList prizes available: $mPrizesAvailable")
            writeToLog("GetPrizeListTask", "processRetrievedPrizeList called at: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            mCallerActivity.prizeLoadInProgress()
            if (mPrizesAvailable != null && mPrizesAvailable!!.length > 20) {
                loadPrizesIntoArrays()
                mCallerActivity.setPrizesLoadIntoObjects()
                convertStringsToBitmaps()
                savePrizeArrays()
                mCallerActivity.setPrizesLoadedAllDone()
                WillyShmoApplication.prizesAreAvailable = true
            }
        } catch (e: Exception) {
            writeToLog("GetPrizeListTask", "processRetrievedPrizeList exception called " + e.message)
            mCallerActivity.sendToastMessage("processRetrievedPrizeList exception called $e.message")
        }
        writeToLog("GetPrizeListTask", "processRetrievedPrizeList completed at: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        )
    }

    private fun loadPrizesIntoArrays() {
        val prizes = parsePrizeList()
        val userKeySet: Set<String> = prizes.keys // this is where the keys (userNames) gets sorted
        val objectArray: Array<Any> = prizes.keys.toTypedArray()
        mPrizeNames = arrayOfNulls(objectArray.size)
        mPrizeIds = arrayOfNulls(objectArray.size)
        mPrizeImages = arrayOfNulls(objectArray.size)
        mPrizeImageWidths = arrayOfNulls(objectArray.size)
        mPrizeImageHeights = arrayOfNulls(objectArray.size)
        mPrizeDistances = arrayOfNulls(objectArray.size)
        mBitmapImages = arrayOfNulls(objectArray.size)
        mPrizeUrls = arrayOfNulls(objectArray.size)
        mPrizeLocations = arrayOfNulls(objectArray.size)
        for (x in objectArray.indices) {
            mPrizeNames[x] = objectArray[x] as String
            val prizeValues: Array<String?>? = prizes[objectArray[x]]
            mPrizeIds[x] = prizeValues!![0]
            val workString = prizeValues[1]?.let { StringBuilder(it) }
            val newImage = workString?.substring(1, workString.length - 1)
            mPrizeImages[x] = newImage
            mPrizeImageWidths[x] = prizeValues[2]
            mPrizeImageHeights[x] = prizeValues[3]
            mPrizeDistances[x] = prizeValues[4]
            mPrizeUrls[x] = prizeValues[5]
            mPrizeLocations[x] = prizeValues[6]
        }
    }

    private fun parsePrizeList(): TreeMap<String, Array<String?>> {
        val userTreeMap = TreeMap<String, Array<String?>>()
        try {
            val convertedPrizesAvailable = convertToArray(StringBuilder(mPrizesAvailable!!))
            val jsonObject = JSONObject(convertedPrizesAvailable)
            val prizeArray = jsonObject.getJSONArray("PrizeList")
            for (x in 0 until prizeArray.length()) {
                val prize = prizeArray.getJSONObject(x)
                val prizeId = prize.getInt("id")
                val distance = prize.getDouble("distance")
                val prizeName = prize.getString("name")
                val image = prize.getString("image")
                val prizeUrl = prize.getString("url")
                val location = prize.getString("location")
                val imageWidth = prize.getInt("imageWidth")
                val imageHeight = prize.getInt("imageHeight")
                val prizeArrayValues = arrayOfNulls<String>(7)
                prizeArrayValues[0] = prizeId.toString()
                prizeArrayValues[1] = image
                prizeArrayValues[2] = imageWidth.toString()
                prizeArrayValues[3] = imageHeight.toString()
                prizeArrayValues[4] = distance.toString()
                prizeArrayValues[5] = prizeUrl
                prizeArrayValues[6] = location
                userTreeMap[prizeName] = prizeArrayValues
            }
        } catch (e: JSONException) {
            writeToLog("GetPrizeListTask", "PrizeList: " + e.message)
            mCallerActivity.sendToastMessage(e.message)
        }
        return userTreeMap
    }

    private fun convertToArray(inString: StringBuilder): String {
        var inputString = inString
        var startValue = 0
        //var start = 0
        //var end = 0
        val replaceString = "\"prize:"
        var start = inputString.indexOf(replaceString, startValue)
        var end = inputString.indexOf("{", start + 1)
        inputString = inputString.replace(start - 1, end, "[")
        startValue = end
        for (x in end until inputString.length) {
            start = inputString.indexOf(replaceString, startValue)
            if (start > -1) {
                end = inputString.indexOf("{", start)
                inputString = inputString.replace(start, end, "")
                startValue = end
            } else {
                break
            }
        }
        end = inputString.length - 5
        start = inputString.indexOf("}}}", end)
        inputString = inputString.replace(start, inputString.length - 1, "}]}")
        return inputString.toString()
    }

    private fun convertStringsToBitmaps() {
        for (x in mPrizeIds.indices) {
            val imageStrings =
                mPrizeImages[x]!!.split(",".toRegex()).toTypedArray()
            val imageBytes = ByteArray(imageStrings.size)
            for (y in imageBytes.indices) {
                imageBytes[y] = imageStrings[y].toByte()
            }
            mBitmapImages[x] =
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
    }

    private fun savePrizeArrays() {
        WillyShmoApplication.prizeIds = mPrizeIds
        WillyShmoApplication.prizeNames = mPrizeNames
        WillyShmoApplication.bitmapImages = mBitmapImages
        WillyShmoApplication.imageWidths = mPrizeImageWidths
        WillyShmoApplication.imageHeights = mPrizeImageHeights
        WillyShmoApplication.prizeDistances = mPrizeDistances
        WillyShmoApplication.prizeUrls = mPrizeUrls
        WillyShmoApplication.prizeLocations = mPrizeLocations
    }

    companion object {
        private var mResources: Resources? = null
        private lateinit var mPrizeImages: Array<String?>
        private lateinit var mPrizeImageWidths: Array<String?>
        private lateinit var mPrizeImageHeights: Array<String?>
        private lateinit var mPrizeNames: Array<String?>
        private lateinit var mPrizeUrls: Array<String?>
        private lateinit var mPrizeLocations: Array<String?>
        private lateinit var mPrizeIds: Array<String?>
        private lateinit var mPrizeDistances: Array<String?>
        private lateinit var mBitmapImages: Array<Bitmap?>
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources!!.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }
}