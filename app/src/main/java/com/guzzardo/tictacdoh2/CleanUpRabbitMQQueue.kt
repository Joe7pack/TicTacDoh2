package com.guzzardo.tictacdoh2

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class CleanUpRabbitMQQueue(private val player1Id: Int, private val player1Name: String, val resources: android.content.res.Resources, val toastMessage: ToastMessage) :
    com.guzzardo.tictacdoh2.HandleRabbitMQMessage {

    fun main() {
        mResources = resources
        val queueNameList: Array<String> = arrayOf("client", "server", "playerList")
        val messageConsumerList: MutableList<RabbitMQMessageConsumer> = ArrayList()
        val rabbitMQConnectionList: MutableList<RabbitMQConnection> = ArrayList()
        for (x in queueNameList.indices) {
            val queueNameQualifier = queueNameList[x]
            messageConsumerList.add(RabbitMQMessageConsumer(toastMessage, mResources))
            messageConsumerList[x].setUpMessageConsumer(queueNameQualifier, player1Id, toastMessage, mResources, "CleanUpRabbitMQQueue")
            messageConsumerList[x].setOnReceiveMessageHandler(object :
                RabbitMQMessageConsumer.OnReceiveMessageHandler {
                override fun onReceiveMessage(message: ByteArray?) {
                    try {
                        val text = String(message!!, StandardCharsets.UTF_8)
                        writeToLog("CleanUpRabbitMQQueue","OnReceiveMessageHandler has received message: $text")
                        handleRabbitMQMessage(text)
                    } catch (e: Exception) {
                        writeToLog("CleanUpRabbitMQQueue", "exception in onReceiveMessage: $e")
                    }
                } // end onReceiveMessage
            }) // end setOnReceiveMessageHandler

            rabbitMQConnectionList.add(setUpRabbitMQConnection(queueNameQualifier))
            mClearQueueThread = ClearQueueThread(queueNameQualifier, messageConsumerList[x], rabbitMQConnectionList[x])
            mClearQueueThread!!.start()
            mClearQueueThreadRunning = true
        }
    }

    inner class ClearQueueThread(private val queueNameQualifier: String, private val messageConsumer: RabbitMQMessageConsumer, private val rabbitMQConnection: RabbitMQConnection): Thread() {
        override fun run() {
            try {
                writeToLog("CleanUpRabbitMQQueue", "ClearQueueThread started for $queueNameQualifier")
                while (mClearQueueThreadRunning) {
                    if (mMessageRetrieved == null) {
                        mClearQueueThreadRunning = false
                    } else {
                        writeToLog("CleanUpRabbitMQQueue", "ClearQueueThread message retrieved: $mMessageRetrieved")
                        mMessageRetrieved = null
                    }
                    sleep(THREAD_SLEEP_INTERVAL.toLong())
                } // while end
                closeRabbitMQConnection(rabbitMQConnection, queueNameQualifier, messageConsumer)
            } catch (e: Exception) {
                writeToLog("CleanUpRabbitMQQueue", "error in ClearQueueThread: " + e.message)
                sendToastMessage(e.message)
            } finally {
                writeToLog("CleanUpRabbitMQQueue", "ClearQueueThread finally done for $queueNameQualifier")
            }
        }
    }

    private fun setUpRabbitMQConnection(queueNameQualifier: String): RabbitMQConnection {
        val qName = WillyShmoApplication.getConfigMap("RabbitMQQueuePrefix") + "-" + queueNameQualifier + "-" + player1Id
        return runBlocking {
            withContext(CoroutineScope(Dispatchers.Default).coroutineContext) {
                SetUpRabbitMQConnection().main(qName, toastMessage, mResources)
            }
        }
    }

    fun closeRabbitMQConnection(mRabbitMQConnection: RabbitMQConnection, queueNameQualifier: String, messageConsumer: RabbitMQMessageConsumer) {
        writeToLog("CleanUpRabbitMQQueue", "at start of closeRabbitMQConnection() for $queueNameQualifier")
        return runBlocking {
            writeToLog("CleanUpRabbitMQQueue", "about to stop RabbitMQ consume thread for $queueNameQualifier")
            val messageToSelf = "finishConsuming,${player1Name},${player1Id}"
            withContext(CoroutineScope(Dispatchers.Default).coroutineContext) {
                val qName = WillyShmoApplication.getConfigMap("RabbitMQQueuePrefix") + "-" + queueNameQualifier + "-" + player1Id
                val sendMessageToRabbitMQ = SendMessageToRabbitMQ()
                sendMessageToRabbitMQ.main(mRabbitMQConnection, qName, messageToSelf, toastMessage, mResources)
            }
            writeToLog("CleanUpRabbitMQQueue", "about to close RabbitMQ connection for $queueNameQualifier")
            withContext(CoroutineScope(Dispatchers.Default).coroutineContext) {
                CloseRabbitMQConnection().main(mRabbitMQConnection, toastMessage, mResources)
            }
            writeToLog("CleanUpRabbitMQQueue", "about to Dispose RabbitMQ consumer for $queueNameQualifier")
            withContext(CoroutineScope(Dispatchers.Default).coroutineContext) {
                val disposeRabbitMQTask = DisposeRabbitMQTask()
                disposeRabbitMQTask.main(messageConsumer, mResources, toastMessage)
            }
        }
    }

    override fun handleRabbitMQMessage(message: String) {
        writeToLog("CleanUpRabbitMQQueue", "message retrieved: $message")
        mMessageRetrieved = message
    }

    fun sendToastMessage(message: String?) {
        val msg = mErrorHandler!!.obtainMessage()
        msg.obj = message
        mErrorHandler!!.sendMessage(msg)
    }

    class ErrorHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(mApplicationContext, msg.obj as String, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private lateinit var mResources: Resources
        private var mErrorHandler: ErrorHandler? = null
        private var mApplicationContext: Context? = null
        private var mMessageRetrieved: String? = "seed message"
        private var mClearQueueThread: ClearQueueThread? = null
        private var mClearQueueThreadRunning = false
        private const val THREAD_SLEEP_INTERVAL = 200 //milliseconds
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(mResources.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }
    }
}