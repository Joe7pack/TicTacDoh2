package com.guzzardo.tictacdoh2

import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WebServerInterface {
    private var mToastMessage: ToastMessage? = null
    private var mResources: android.content.res.Resources? = null
    @kotlin.jvm.JvmStatic
	fun converseWithWebServer(url: String, urlToEncode: String?, toastMessage: ToastMessage?, resources: android.content.res.Resources): String? {
        var `is`: java.io.InputStream? = null
        var result: String? = null
        mResources = resources
        mToastMessage = toastMessage
        var errorAt: String? = null
        var responseCode = 0
        var networkAvailable = false
        var httpUrlConnection: HttpURLConnection? = null
        try {
            writeToLog("WebServerInterface", "converseWithWebServer() called")
            val myURL = if (urlToEncode == null) {
                URL(url)
            } else {
                val encodedUrl = URLEncoder.encode(urlToEncode, "UTF-8")
                URL(url + encodedUrl)
            }
            errorAt = "openConnection"
            httpUrlConnection = myURL.openConnection() as HttpURLConnection
            httpUrlConnection.requestMethod = "POST"
            httpUrlConnection.connectTimeout = 3000
            responseCode = httpUrlConnection.responseCode
            errorAt = "getInputStream"
            `is` = httpUrlConnection.inputStream // define InputStreams to read from the URLConnection.
            errorAt = "convertStreamToString"
            result = convertStreamToString(`is`) // convert the Bytes read to a String.
            networkAvailable = true
        } catch (e: Exception) {
            writeToLog("WebServerInterface", "response code: $responseCode error: $e.message error at: $errorAt")
            val networkNotAvailable = resources.getString(R.string.network_not_available)
            mToastMessage!!.sendToastMessage(networkNotAvailable)
        } finally {
            try {
                `is`!!.close()
                if (httpUrlConnection != null) {
                    httpUrlConnection.disconnect()
                }
            } catch (e: Exception) {
                //nothing to do here
                writeToLog("WebServerInterface", "finally exception: $e.message")
            }
        }
        WillyShmoApplication.isNetworkAvailable = networkAvailable
        return result
    }

    private fun convertStreamToString(`is`: InputStream?): String {
        val reader = BufferedReader(InputStreamReader(`is`))
        val sb = StringBuilder()
        try {
            val allText = `is`?.bufferedReader()?.readText()
            sb.append(allText)
        } catch (e: IOException) {
            writeToLog( "WebServerInterface", "convertStreamToString IOException: " + e.message)
            if (e.message?.indexOf("it must not be null", 0)!! == -1)
                mToastMessage!!.sendToastMessage("convertStreamToString IOException: " + e.message)
        } catch (e: Exception) {
            writeToLog( "WebServerInterface", "convertStreamToString Exception: " + e.message)
            if (e.message?.indexOf("it must not be null", 0)!! == -1)
                mToastMessage!!.sendToastMessage("convertStreamToString Exception: " + e.message + "Please try again")
        } finally {
            try {
                reader.close()
            } catch (e: IOException) {
                writeToLog("WebServerInterface", "is close IOException:: " + e.message)
                mToastMessage!!.sendToastMessage("is close IOException: " + e.message)
            }
        }
        return sb.toString()
    }

    private fun writeToLog(filter: String, msg: String) {
        if ("true".equals(mResources!!.getString(R.string.debug), ignoreCase = true))
            Log.d(filter, msg)
    }
}