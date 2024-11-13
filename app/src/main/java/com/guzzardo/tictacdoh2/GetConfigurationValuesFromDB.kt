package com.guzzardo.tictacdoh2

import android.content.res.Resources
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.*
import kotlinx.coroutines.*

// An AsyncTask that will be used to load Configuration values from the DB

class GetConfigurationValuesFromDB {
    private lateinit var mCallerActivity: ToastMessage
    private var applicationContext: android.content.Context? = null

    fun main(callerActivity: ToastMessage, resources: android.content.res.Resources) =
        kotlinx.coroutines.runBlocking {
            var configValues: String? = null
            mCallerActivity = callerActivity
            mResources = resources
            val urlData = "/config/getConfigValues"
            try {
                configValues = SendMessageToAppServer.main(
                    urlData,
                    mCallerActivity,
                    resources,
                    false
                )
            } catch (e: Exception) {
                writeToLog("GetConfigurationValuesFromDB", "doInBackground: " + e.message)
                mCallerActivity.sendToastMessage(e.message)
            }
            writeToLog(
                "GetConfigurationValuesFromDB",
                "GetConfigurationValuesFromDB doInBackground return values: $configValues"
            )
            if (!configValues.equals("noResultReturned")) {
                setConfigValues(configValues)
            }
        }

    fun setConfigValues(configValues: String?) {
        try {
            writeToLog("GetConfigurationValuesFromDB", "onPostExecute called configValues: $configValues")
            val objectMapper = ObjectMapper()
            val result: MutableList<*>? = objectMapper.readValue(configValues, MutableList::class.java)

            if (result != null) {
                for (x in result.indices) {
                    val myHashMap: HashMap<*, *> = result[x] as HashMap<*, *>
                    val key = myHashMap["key"] as String?
                    val value = myHashMap["value"] as String?
                    WillyShmoApplication.setConfigMap(key, value)
                }
            }
        } catch (e: Exception) {
            writeToLog("GetConfigurationValuesFromDB", "setConfigValues exception called " + e.message)
            mCallerActivity.sendToastMessage("GetConfigurationValuesFromDB setConfigValues error: " + e.message)
        }
    }

    companion object {
        private var mResources: Resources? = null
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources!!.getString(R.string.debug), ignoreCase = true))
                { Log.d(filter, msg) }
        }
    }
}