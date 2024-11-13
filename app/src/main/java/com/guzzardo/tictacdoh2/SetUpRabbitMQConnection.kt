package com.guzzardo.tictacdoh2

import android.content.res.Resources
import android.util.Log
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.getConfigMap
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import kotlinx.coroutines.*

class SetUpRabbitMQConnection {
    private var mCallingActivity: ToastMessage? = null

    fun main(qName: String, callerActivity: ToastMessage, resources: android.content.res.Resources?): RabbitMQConnection { //runBlocking {
        var channel: Channel? = null
        var connection: Connection? = null
        try {
            mCallingActivity = callerActivity
            mResources = resources
            val connectionFactory = ConnectionFactory()
            connectionFactory.host = getConfigMap("RabbitMQIpAddress")
            connectionFactory.username = getConfigMap("RabbitMQUser")
            connectionFactory.password = getConfigMap("RabbitMQPassword")
            connectionFactory.virtualHost = getConfigMap("RabbitMQVirtualHost")
            val portNumber = Integer.valueOf(getConfigMap("RabbitMQPort"))
            connectionFactory.port = portNumber
            connection = connectionFactory.newConnection()
            channel = connection.createChannel()
//			channel.exchangeDeclare(EXCHANGE_NAME, "fanout", true);
            channel.queueDeclare(qName, false, false, false, null)
        } catch (e: Exception) {
            writeToLog("SetUpRabbitMQConnection", "Exception: " + e.message)
            mCallingActivity!!.sendToastMessage(e.message)
        }
        finally {
            return RabbitMQConnection(connection, channel)
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