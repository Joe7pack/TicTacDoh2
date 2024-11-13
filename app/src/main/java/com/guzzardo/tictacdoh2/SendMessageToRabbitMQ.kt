package com.guzzardo.tictacdoh2

import android.content.res.Resources
import android.util.Log
import kotlinx.coroutines.*

class SendMessageToRabbitMQ {
    private var mCallingActivity: ToastMessage? = null
    fun main(rabbitMQConnection: RabbitMQConnection?, qName: String, message: String, callerActivity: ToastMessage, resources: android.content.res.Resources?) =
        kotlinx.coroutines.runBlocking {
            try {
                mCallingActivity = callerActivity
                mResources = resources
                val channel = rabbitMQConnection?.channel
                //channel?.queueDeclare(qName, false, false, false, null)
                //channel?.queueDeclare(qName, false, false, true, null) //autodelete queue
                channel?.basicPublish("", qName, null, message.toByteArray())
                writeToLog("SendMessageToRabbitMQ", "message: $message to queue: $qName")
            } catch (e: Exception) {
                writeToLog("SendMessageToRabbitMQ", "Exception: " + e.message)
                mCallingActivity!!.sendToastMessage(e.message)
            }
        }

    companion object {
        private var mResources: Resources? = null
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources!!.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }
}