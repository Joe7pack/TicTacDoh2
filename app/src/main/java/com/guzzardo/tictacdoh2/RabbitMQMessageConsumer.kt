package com.guzzardo.tictacdoh2

import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rabbitmq.client.QueueingConsumer.Delivery
import com.rabbitmq.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.Exception
import java.util.*

// Consumes messages from a RabbitMQ broker
class RabbitMQMessageConsumer(private val toastMessage: ToastMessage, private var resources: android.content.res.Resources?) {
    private val mExchange = "test"
    var channel: Channel? = null
    var connection: Connection? = null
    //private val mExchangeType = "fanout"
    private var queue: String? = null //The Queue name for this consumer
    var consumer: QueueingConsumer? = null
    private var mConsumerRunning = false
    private var mResources = resources
    private lateinit var mLastMessage: ByteArray //last message to post back

    // An interface to be implemented by an object that is interested in messages(listener)
    // This is the hook to connect the MainActivity message handler response processing with the received message from RabbitMQ
    interface OnReceiveMessageHandler {
        fun onReceiveMessage(message: ByteArray?)
    }

    //a reference to the listener, we can only have one at a time (for now)
    private lateinit var mOnReceiveMessageHandler: OnReceiveMessageHandler

    /**
     * Set the callback for received messages
     * @param handler The callback
     */
    fun setOnReceiveMessageHandler(handler: OnReceiveMessageHandler) {
        mOnReceiveMessageHandler = handler
    }

    private val mMessageHandler = Handler(Looper.getMainLooper())

    // Create runnable for posting back to main thread
    val mReturnMessage = Runnable { mOnReceiveMessageHandler.onReceiveMessage(mLastMessage) }
    private val mConsumeHandler = Handler(Looper.getMainLooper())
    private val mConsumeRunner = Runnable { consume() }

    /**
     * Create Exchange and then start consuming. A binding needs to be added before any messages will be delivered
     */
    fun startConsuming(connection: Connection?, channel: Channel?, queue: String?, consumer: QueueingConsumer?): Boolean {
        this.channel = channel
        this.connection = connection
        this.queue = queue
        this.consumer = consumer
        //if (mExchangeType == "fanout")
        //  AddBinding("");//fanout has default binding
        mConsumeHandler.post(mConsumeRunner)
        mConsumerRunning = true
        return true
    }

    /**
     * Add a binding between this consumers Queue and the Exchange with routingKey
     * @param routingKey the binding key eg GOOG
     */
    fun addBinding(routingKey: String?) {
        try {
            channel!!.queueBind(queue, mExchange, routingKey)
        } catch (e: IOException) {
            //e.printStackTrace();
            toastMessage.sendToastMessage("RabbitMQMessageConsumer addBinding  " + e.message)
        }
    }

    /**
     * Remove binding between this consumers Queue and the Exchange with routingKey
     * @param routingKey the binding key eg GOOG
     */
    fun removeBinding(routingKey: String?) {
        try {
            channel!!.queueUnbind(queue, mExchange, routingKey)
        } catch (e: IOException) {
            //e.printStackTrace();
            toastMessage.sendToastMessage("RabbitMQMessageConsumer removeBinding " + e.message)
        }
    }

    private fun consume() {
        val thread: Thread = object : Thread() {
            override fun run() {
                var delivery: Delivery
                while (mConsumerRunning) {
                    //Log.d("RabbitMQMessageConsumer", "inside consume run loop");
                    try {
                        delivery = consumer!!.nextDelivery() //blocks until a message is received
                        mLastMessage = delivery.body
                        writeToLog("RabbitMQMessageConsumer", "last message: " + String(mLastMessage))
                        mMessageHandler.post(mReturnMessage)
                        val lastMessage = String(mLastMessage)
                        if (lastMessage.startsWith("finishConsuming")) {
                            mConsumerRunning = false
                        }
                    } catch (ie: InterruptedException) {
                        writeToLog("RabbitMQMessageConsumer", "InterruptedException: " + ie.message)
                        toastMessage.sendToastMessage(ie.message)
                        mConsumerRunning = false
                    } catch (sse: ShutdownSignalException) {
                        writeToLog("RabbitMQMessageConsumer", "ShutdownSignalException: $sse")
                        mConsumerRunning = false
                    } catch (cce: ConsumerCancelledException) {
                        writeToLog("RabbitMQMessageConsumer", "ConsumerCancelledException: $cce")
                        mConsumerRunning = false
                    } catch (e: Exception) {
                        writeToLog("RabbitMQMessageConsumer", "Some other Exception: $e")
                        mConsumerRunning = false
                    } finally {
                        writeToLog("RabbitMQMessageConsumer", "run finally completed")
                    }
                }
                writeToLog("RabbitMQMessageConsumer", "thread all done")
            }
        }
        thread.start()
    }

    fun dispose() {
        writeToLog("RabbitMQMessageConsumer", "dispose called")
        try {
            if (channel != null) channel!!.close()
            if (connection != null) connection!!.close()
        } catch (e: Exception) {
            writeToLog("RabbitMQMessageConsumer", "dispose() function error: $e")
            toastMessage.sendToastMessage("RabbitMQMessageConsumer dispose " + e.message)
        }
    }

    private fun writeToLog(filter: String, msg: String) {
        if ("true".equals(resources!!.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
        }
    }

    fun setUpMessageConsumer(qNameQualifier: String, player1Id: Int?, activity: ToastMessage, resources: Resources, source: String) {
        val qName = WillyShmoApplication.getConfigMap("RabbitMQQueuePrefix") + "-" + qNameQualifier + "-" + player1Id
        CoroutineScope(Dispatchers.Default).launch {
            val consumerConnectTask = ConsumerConnectTask()
            consumerConnectTask.main(
                WillyShmoApplication.getConfigMap("RabbitMQIpAddress"),
                this@RabbitMQMessageConsumer,
                qName,
                activity,
                resources,
                source
            )
        }
        writeToLog("RabbitMQMessageConsumer", "$qNameQualifier message consumer listening on queue: $qName")
    }
}