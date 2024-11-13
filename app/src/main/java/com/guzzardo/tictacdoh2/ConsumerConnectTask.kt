package com.guzzardo.tictacdoh2

import android.content.res.Resources
import android.util.Log
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.getConfigMap
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.QueueingConsumer
import kotlinx.coroutines.runBlocking

class ConsumerConnectTask {
    private var mHostName: String? = null
    private var mMessageConsumer: RabbitMQMessageConsumer? = null
    private var mConnection: Connection? = null
    private var mQueueName: String? = null
    private var mChannel: Channel? = null
    private var mConsumer: QueueingConsumer? = null
    private var mToastMessage: ToastMessage? = null
    private var mSource: String? = null

    fun main(hostName: String?, messageConsumer: RabbitMQMessageConsumer?, queueName: String, toastMessage: ToastMessage?, resources: android.content.res.Resources, source: String) =
        kotlinx.coroutines.runBlocking {
            mHostName = hostName
            mMessageConsumer = messageConsumer
            mQueueName = queueName
            mToastMessage = toastMessage
            mResources = resources
            mSource = source
            writeToLog("ConsumerConnectTask", "QueueName: $queueName")

            if (mMessageConsumer!!.channel == null) {
                try { // Connect to broker
                    val connectionFactory = ConnectionFactory()
                    connectionFactory.host = getConfigMap("RabbitMQIpAddress")
                    connectionFactory.username = getConfigMap("RabbitMQUser")
                    connectionFactory.password = getConfigMap("RabbitMQPassword")
                    connectionFactory.virtualHost = getConfigMap("RabbitMQVirtualHost")
                    val portNumber = getConfigMap("RabbitMQPort")!!.toInt()
                    connectionFactory.port = portNumber
                    //TODO - need to determine the default connection timeout
                    //connectionFactory.setConnectionTimeout(5000);
                    connectionFactory.connectionTimeout =
                        ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT //wait forever
                    mConnection = connectionFactory.newConnection()
                    mChannel = mConnection?.createChannel()
                    //channel.exchangeDeclare("test", "fanout", true);
                    val queueDeclare = mChannel?.queueDeclare(mQueueName, false, false, false, null)
                    queueDeclare?.queue
                    mConsumer = QueueingConsumer(mChannel)
                    //mChannel.queuePurge(mQueueName); // get rid of any prior messages
                    mChannel?.basicConsume(mQueueName, true, mConsumer)
                } catch (e: Exception) {
                    mToastMessage!!.sendToastMessage(e.message)
                }
            }
            mMessageConsumer!!.channel = mChannel
            mMessageConsumer!!.connection = mConnection
            mMessageConsumer!!.startConsuming(mConnection, mChannel, mQueueName, mConsumer)
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