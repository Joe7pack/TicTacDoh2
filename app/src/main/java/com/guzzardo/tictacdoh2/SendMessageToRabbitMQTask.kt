package com.guzzardo.tictacdoh2

import android.content.res.Resources
import android.util.Log
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.getConfigMap
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.*

class SendMessageToRabbitMQTask {
    private var mCallingActivity: ToastMessage? = null
    fun main(mHostName: String?, qName: String, message: String, callerActivity: ToastMessage, resources: android.content.res.Resources?) =
        kotlinx.coroutines.runBlocking {
            try {
                mCallingActivity = callerActivity
                mResources = resources
                val connectionFactory = ConnectionFactory()
                connectionFactory.host = getConfigMap("RabbitMQIpAddress")
                connectionFactory.username = getConfigMap("RabbitMQUser")
                connectionFactory.password = getConfigMap("RabbitMQPassword")
                connectionFactory.virtualHost = getConfigMap("RabbitMQVirtualHost")
                val portNumber = Integer.valueOf(getConfigMap("RabbitMQPort")!!)
                connectionFactory.port = portNumber
                val connection = connectionFactory.newConnection()
                val channel = connection.createChannel()
//			channel.exchangeDeclare(EXCHANGE_NAME, "fanout", true);
                channel.queueDeclare(qName, false, false, false, null)
//			channel.basicPublish(EXCHANGE_NAME, QUEUE_NAME, null, tempstr.getBytes());
                channel.basicPublish("", qName, null, message.toByteArray())
                writeToLog("SendMessageToRabbitMQTask", "message: $message to queue: $qName")
                channel.close()
                connection.close()
            } catch (e: Exception) {
                writeToLog("SendMessageToRabbitMQTask", "Exception: " + e.message)
                //Log.e("SendMessageToRabbitMQTask", e.getMessage());
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