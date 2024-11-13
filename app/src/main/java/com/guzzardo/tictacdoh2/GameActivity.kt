/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guzzardo.tictacdoh2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.guzzardo.tictacdoh2.GameView.ICellListener
import com.guzzardo.tictacdoh2.MainActivity.UserPreferences
import com.guzzardo.tictacdoh2.RabbitMQMessageConsumer.OnReceiveMessageHandler
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.androidId
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.getConfigMap
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mLatitude
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mLongitude
import kotlinx.coroutines.*
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class GameActivity() : Activity(), ToastMessage, Parcelable {
    private var mServer = false
    private var mClient = false
    private val mHandler = Handler(Looper.getMainLooper(), MyHandlerCallback())
    private var mButtonNext: Button? = null
    private var mPlayer1TokenChoice = GameView.BoardSpaceValues.EMPTY
    private var mPlayer2TokenChoice = GameView.BoardSpaceValues.EMPTY // computer or opponent
    private var mPlayer1ScoreTextValue: TextView? = null
    private var mPlayer2ScoreTextValue: TextView? = null
    private var mPlayer1NameTextValue: EditText? = null
    private var mPlayer2NameTextValue: EditText? = null
    private var mGameTokenPlayer1: ImageView? = null
    private var mGameTokenPlayer2: ImageView? = null
    private val humanWinningHashMap: MutableMap<Int, Int> = HashMap()
    private var mClientThread: ClientThread? = null
    private var mServerThread: ServerThread? = null
    private var mMoveWaitingTimerHandlerThread: HandlerThread? = null
    private var mMoveWaitProgressThread: MoveWaitProgressThread? = null
    private var looperForMoveWaiting: Looper? = null
    private var looperForMoveWaitingHandler: Handler? = null
    private var mMoveWaitingTimerProgress: ProgressBar? = null
    private var mMoveWaitingProgress = 100
    private val MOVE_WAITING = 1
    private var mWaitingForMove = false
    private var mTokensFromClient: MutableList<IntArray>? = null
    private val mRandom = Random()
    private var mMessageClientConsumer: RabbitMQMessageConsumer? = null
    private var mMessageServerConsumer: RabbitMQMessageConsumer? = null
    private var mRabbitMQClientResponse: String? = null
    private var mRabbitMQServerResponse: String? = null
    private var mRabbitMQStartGameResponse: String? = null
    private var mServerHasOpponent: String? = null

    interface PrizeValue {
        companion object {
            const val SHMOGRANDPRIZE = 4 //player wins with a Shmo and shmo card was placed on prize card
            const val SHMOPRIZE = 2 //player wins with a shmo
            const val GRANDPRIZE = 3 //player wins by placing winning card on prize token
            const val REGULARPRIZE = 1 //player wins with any card covering the prize
        }
    }

    /** Called when the activity is first created.  */
    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        Companion.resources = resources
        mGameActivity = this
        errorHandler = ErrorHandler()
        setContentView(R.layout.lib_game)
        mApplicationContext = applicationContext
        mGameView = findViewById<View>(R.id.game_view) as GameView
        mButtonNext = findViewById<View>(R.id.next_turn) as Button
        mButtonStartText = mButtonNext!!.text
        mPlayer1ScoreTextValue = findViewById<View>(R.id.player1_score) as TextView
        mPlayer2ScoreTextValue = findViewById<View>(R.id.player2_score) as TextView
        mPlayer1NameTextValue = findViewById<View>(R.id.player1_name) as EditText
        mPlayer2NameTextValue = findViewById<View>(R.id.player2_name) as EditText
        mGameTokenPlayer1 = findViewById<View>(R.id.player1_token) as ImageView
        mGameTokenPlayer2 = findViewById<View>(R.id.player2_token) as ImageView
        mMoveWaitingTimerProgress = findViewById<View>(R.id.gameWaitBar) as ProgressBar
        mGameView!!.isFocusable = true
        mGameView!!.isFocusableInTouchMode = true
        mGameView!!.setCellListener(MyCellListener())
        mButtonNext!!.setOnClickListener(MyButtonListener())
        HUMAN_VS_HUMAN = false
        HUMAN_VS_NETWORK = false
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        mPlayer1Score = settings.getInt(PLAYER1_SCORE, 0)
        mPlayer2Score = settings.getInt(PLAYER2_SCORE, 0)
        mWillyScore = settings.getInt(WILLY_SCORE, 0)
        moveModeTouch = settings.getBoolean(MOVE_MODE, false)
        soundMode = settings.getBoolean(SOUND_MODE, false)
        mGameView!!.setViewDisabled(false)
        mHostName = getConfigMap("RabbitMQIpAddress")
        mQueuePrefix = getConfigMap("RabbitMQQueuePrefix")
        mStartSource = intent.getParcelableExtra<ParcelItems>(PARCELABLE_VALUES).toString()
        writeToLog("GameActivity", "our parcelable extra item: $mStartSource")
        writeToLog("GameActivity", "onCreate() Completed taskId: $taskId")
    }

    private fun createHostWaitDialog(): AlertDialog {
        if (mHostWaitDialog != null) {
            mHostWaitDialog!!.dismiss()
        }
        val waitingString = getString(R.string.alert_dialog_waiting)
        val opponentNameWaiting = getString(R.string.alert_dialog_waiting_player2) + " " + mPlayer1Name + " " +
            getString(R.string.alert_dialog_to_connect)
        val opponentName = if (mPlayer2Name == null) waitingString else opponentNameWaiting
        val hostingDescription = if (mPlayer2Name == null) getString(R.string.alert_dialog_hosting_friend)
            else getString(R.string.alert_dialog_hosting)
        mGameView!!.setGamePrize(true)
        return AlertDialog.Builder(this@GameActivity)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(hostingDescription)
            .setMessage(opponentName)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.alert_dialog_cancel)) { _, _ -> cancelGame() }
            .create()
    }

    private fun createClientWaitDialog(): AlertDialog {
        if (mClientWaitDialog != null) {
            mClientWaitDialog!!.dismiss()
        }
        val connectingString = getString(R.string.alert_dialog_connecting)
        val playerReallyWantsToPlay = getString(R.string.alert_dialog_lets_see) + " " + mPlayer2Name + " " + getString(R.string.alert_dialog_wants_to_play)
        return AlertDialog.Builder(this@GameActivity)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(connectingString)
            .setMessage(playerReallyWantsToPlay)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.alert_dialog_cancel)) { _, _ -> cancelGame() }
            .create()
    }

    private fun cancelGame() {
        writeToLog("GameActivity", "cancelGame() called")
        mClientRunning = false
        finish()
    }

    override fun onResume() {
        super.onResume()
        mGameView!!.setGameActivity(this)
        mGameView!!.setClient(null)
        mServer = java.lang.Boolean.valueOf(intent.getStringExtra(START_SERVER))
        HUMAN_VS_WILLY = intent.getStringExtra(PLAY_AGAINST_WILLY) == "true"
        if (mServer && !isServerRunning) {
            mPlayer1Id = intent.getIntExtra(PLAYER1_ID, 0)
            mServerThread = ServerThread()
            mMessageServerConsumer = RabbitMQMessageConsumer(this@GameActivity, Companion.resources)
            mRabbitMQServerResponse = "serverStarting"
            mMessageServerConsumer!!.setUpMessageConsumer("server", mPlayer1Id, this, resources, "GameActivityServer")
            mMessageServerConsumer!!.setOnReceiveMessageHandler(object: OnReceiveMessageHandler {
                override fun onReceiveMessage(message: ByteArray?) {
                    val text = String(message!!, StandardCharsets.UTF_8)
                    mRabbitMQServerResponse = text
                    val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
                    writeToLog("GameActivity", "server OnReceiveMessageHandler received message: $text at my time $dateTime")
                } // end onReceiveMessage
            }) // end setOnReceiveMessageHandler
            mPlayer1Name = intent.getStringExtra(PLAYER1_NAME)
            isServerRunning = true
            mServerThread!!.start()
            HUMAN_VS_NETWORK = true
            mServerHasOpponent = intent.getStringExtra(HAVE_OPPONENT)
            writeToLog("GameActivity", "onResume - we are serving but server is not running")
        }

        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        val usersOnlineNumber = settings.getInt("ga_users_online_number", 0)

        mClient = java.lang.Boolean.valueOf(intent.getStringExtra(START_CLIENT))
        if (mClient) {
            mPlayer1Id = intent.getIntExtra(PLAYER1_ID, 0)
            mPlayer2Id = intent.getStringExtra(START_CLIENT_OPPONENT_ID)
            mClientThread = ClientThread()
            mMessageClientConsumer = RabbitMQMessageConsumer(this@GameActivity, Companion.resources)
            mMessageClientConsumer!!.setUpMessageConsumer("client", mPlayer1Id, this, resources, "GameActivityClient")
            mMessageClientConsumer!!.setOnReceiveMessageHandler(object: OnReceiveMessageHandler {
                override fun onReceiveMessage(message: ByteArray?) {
                    val text = String(message!!, StandardCharsets.UTF_8)
                    mRabbitMQClientResponse = text
                    val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
                    writeToLog("GameActivity", "client OnReceiveMessageHandler received message: $text at my time $dateTime")
                } // end onReceiveMessage
            }) // end setOnReceiveMessageHandler
            mClientThread!!.start()
            mClientRunning = true
            HUMAN_VS_NETWORK = true
            mGameView!!.setClient(mClientThread) //this is where we inform GameView to send game tokens to network opponent when the GameView is created
            mPlayer2Name = intent.getStringExtra(PLAYER2_NAME)
            mClientWaitDialog = createClientWaitDialog()
            mClientWaitDialog!!.show()
            writeToLog("GameActivity", "onResume - started client thread")
        }
        if (mServer && !mClient) {
            mPlayer2NetworkScore = 0
            mPlayer1NetworkScore = mPlayer2NetworkScore
            //mPlayer2Name = null
            displayScores()
            if (usersOnlineNumber == 0) {
                mHostWaitDialog = createHostWaitDialog()
                mHostWaitDialog!!.show()
            }
            val androidId = "&deviceId=$androidId"
            val latitude = "&latitude=$mLatitude"
            val longitude = "&longitude=$mLongitude"
            val trackingInfo = androidId + latitude + longitude
            val urlData = ("/gamePlayer/update/?onlineNow=true&playingNow=false&opponentId=0" + trackingInfo + "&id="
                + mPlayer1Id + "&userName=" + mPlayer1Name)
            val messageResponse = sendMessageToAppServer(urlData,false)
            writeToLog("GameActivity", "onResume - we are serving but we're not a client, messageResponse: $messageResponse")
            // hack to deal with stale leftGame message that I can't seem to get rid of for some goddamn reason
            if (mStartSource != null && mStartSource!!.contains("Shakespeare")) {
                writeToLog("GameActivity", "onResume - nope, not gonna finish() due to spurious letsPlay message")
                //finish()
            }
            return
        }
        var player = mGameView!!.currentPlayer
        if (player == GameView.State.UNKNOWN) {
            player = GameView.State.fromInt(intent.getIntExtra(START_PLAYER_HUMAN, -3))
            if (player == GameView.State.UNKNOWN) {
                player = GameView.State.fromInt(intent.getIntExtra(EXTRA_START_PLAYER, 1))
            } else {
                HUMAN_VS_HUMAN = true
            }
            mGameView!!.setHumanState(HUMAN_VS_HUMAN)
            mPlayer1Name = intent.getStringExtra(PLAYER1_NAME)
            mPlayer2Name = intent.getStringExtra(PLAYER2_NAME)
            if (!checkGameFinished(player, false)) {
                selectTurn(player)
            }
        }
        writeToLog("GameActivity", "HUMAN_VS_HUMAN: $HUMAN_VS_HUMAN HUMAN_VS_WILLY: $HUMAN_VS_WILLY")
        if (HUMAN_VS_HUMAN or HUMAN_VS_WILLY) {
            mGameView!!.setGamePrize(false) //works only from client side but server side never call onResume when starting a game
            writeToLog("GameActivity", "setGamePrize() false called")
        } else {
            mGameView!!.setGamePrize(true)
        }
        //but if we just play against Willy then onResume is called
        mPlayer2NetworkScore = 0
        mPlayer1NetworkScore = mPlayer2NetworkScore
        displayScores()
        highlightCurrentPlayer(player)
        showPlayerTokenChoice()
        if (player == GameView.State.PLAYER2 && !(HUMAN_VS_HUMAN or HUMAN_VS_NETWORK)) {
            mHandler.sendEmptyMessageDelayed(MSG_COMPUTER_TURN, COMPUTER_DELAY_MS)
        }
        if (player == GameView.State.WIN) {
            setWinState(mGameView!!.winner)
        }
        mGameView!!.setViewDisabled(false)
        writeToLog("GameActivity", "onResume() Completed")
    }

    private fun selectTurn(player: GameView.State): GameView.State {
        mGameView!!.currentPlayer = player
        mButtonNext!!.isEnabled = false
        if (player == GameView.State.PLAYER1) {
            mGameView!!.isEnabled = true
        } else if (player == GameView.State.PLAYER2) {
            mGameView!!.isEnabled = false
        }
        return player
    }

    private inner class MyCellListener : ICellListener {
        override fun onCellSelected() {
            val cell = mGameView!!.selection
            mButtonNext!!.isEnabled = cell >= 0
            if (cell >= 0) {
                playSound(R.raw.human_token_move_sound)
                mLastCellSelected = cell
            }
        }
    }

    private inner class MyButtonListener : View.OnClickListener {
        fun showChooseTokenDialog(): AlertDialog {
            return AlertDialog.Builder(this@GameActivity)
                .setIcon(R.drawable.willy_shmo_small_icon)
                .setTitle(R.string.wild_card_explanation)
                .setSingleChoiceItems(R.array.select_starting_token, 0) { _, whichButton ->
                    when (whichButton) {
                        0 -> {
                            if (HUMAN_VS_HUMAN) {
                                if (mGameView!!.currentPlayer == GameView.State.PLAYER1) {
                                    mPlayer1TokenChoice = GameView.BoardSpaceValues.CIRCLE
                                    mPlayer2TokenChoice = GameView.BoardSpaceValues.CROSS
                                } else {
                                    mPlayer1TokenChoice = GameView.BoardSpaceValues.CROSS // else we're looking at player 2
                                    mPlayer2TokenChoice = GameView.BoardSpaceValues.CIRCLE
                                }
                            } else {
                                mPlayer1TokenChoice = GameView.BoardSpaceValues.CIRCLE
                                mPlayer2TokenChoice = GameView.BoardSpaceValues.CROSS
                            }
                        }
                        1 -> {
                            if (HUMAN_VS_HUMAN) {
                                if (mGameView!!.currentPlayer == GameView.State.PLAYER1) {
                                    mPlayer1TokenChoice = GameView.BoardSpaceValues.CROSS
                                    mPlayer2TokenChoice = GameView.BoardSpaceValues.CIRCLE
                                } else {
                                    mPlayer1TokenChoice = GameView.BoardSpaceValues.CIRCLE
                                    mPlayer2TokenChoice = GameView.BoardSpaceValues.CROSS
                                }
                            } else {
                                mPlayer1TokenChoice = GameView.BoardSpaceValues.CROSS
                                mPlayer2TokenChoice = GameView.BoardSpaceValues.CIRCLE
                            }
                        }
                    }
                    setGameTokenFromDialog()
                }
                .create()
        }

        override fun onClick(v: View) {
            stopMoveWaitingTimerThread()
            val player = mGameView!!.currentPlayer
            val testText = mButtonNext!!.text.toString()
            val playAgainString = getString(R.string.play_again_string)
            if (testText.contains(playAgainString)) { //game is over
                if (mServerIsPlayingNow) {
                    mHostWaitDialog = createHostWaitDialog()
                    mHostWaitDialog!!.show()
                } else if (mClientRunning) { //reset values on client side
                    sendNewGameToServer()
                } else {
                    finish()
                }
            } else if (player == GameView.State.PLAYER1 || player == GameView.State.PLAYER2) {
                playSound(R.raw.finish_move)
                val cell = mGameView!!.selection
                var okToFinish = true
                if (cell >= 0) {
                    mSavedCell = cell
                    var gameTokenPlayer1 = -1
                    mGameView!!.stopBlink()
                    mGameView!!.setCell(cell, player)
                    mGameView!!.setBoardSpaceValue(cell)
                    if (mPlayer1TokenChoice == GameView.BoardSpaceValues.EMPTY) {
                        val tokenSelected = mGameView!!.getBoardSpaceValue(cell)
                        if (tokenSelected == GameView.BoardSpaceValues.CIRCLECROSS) {
                            mChooseTokenDialog = showChooseTokenDialog()
                            mChooseTokenDialog!!.show()
                            okToFinish = false
                        } else {
                            if (player == GameView.State.PLAYER1) {
                                mPlayer1TokenChoice = tokenSelected
                                mPlayer2TokenChoice = if (mPlayer1TokenChoice == GameView.BoardSpaceValues.CIRCLE) GameView.BoardSpaceValues.CROSS else GameView.BoardSpaceValues.CIRCLE
                            } else {
                                mPlayer2TokenChoice = tokenSelected
                                mPlayer1TokenChoice = if (mPlayer2TokenChoice == GameView.BoardSpaceValues.CIRCLE) GameView.BoardSpaceValues.CROSS else GameView.BoardSpaceValues.CIRCLE
                            }
                            mGameView!!.setPlayer1TokenChoice(mPlayer1TokenChoice)
                            mGameView!!.setPlayer2TokenChoice(mPlayer2TokenChoice)
                            gameTokenPlayer1 = mPlayer1TokenChoice
                            showPlayerTokenChoice()
                        }
                    }
                    if (okToFinish) {
                        if (HUMAN_VS_NETWORK) {
                            val movedMessage = "moved, " + mGameView!!.ballMoved + ", " + cell + ", " + gameTokenPlayer1
                            if (mServerIsPlayingNow) {
                                mServerThread!!.setMessageToClient(movedMessage)
                            } else {
                                if (mClientThread == null) {
                                    return // off chance that opposing player left game at same time we pressed the I'm done button
                                }
                                mClientThread!!.setMessageToServer(movedMessage)
                            }
                            stopMoveWaitingTimerThread()
                            finishTurn(false, false, false) //don't send message to make computer move don't switch the player don't use player 2 for win testing
                            val currentPlayer = mGameView!!.currentPlayer
                            highlightCurrentPlayer(getOtherPlayer(currentPlayer))
                            mGameView!!.setViewDisabled(true)
                        } else {
                            finishTurn(true, true, false) //send message to make computer move switch the player don't use player 2 for win testing
                        }
                    } else {
                        mBallMoved = mGameView!!.ballMoved
                        mGameView!!.disableBall() //disableBall sets ball moved to -1, so we need to get it first :-(
                    }
                }
            }
        }
    }

    private fun sendNewGameToServer() {
        mGameView!!.initalizeGameValues()
        mGameView!!.currentPlayer = GameView.State.PLAYER1
        mButtonNext!!.text = mButtonStartText
        mButtonNext!!.isEnabled = false
        mPlayer1TokenChoice = GameView.BoardSpaceValues.EMPTY
        mPlayer2TokenChoice = GameView.BoardSpaceValues.EMPTY
        showPlayerTokenChoice()
        mGameView!!.setTokenCards() //generate new random list of tokens in mGameView.mGameTokenCard[x]
        for (x in GameView.mGameTokenCard.indices) {
            if (x < 8) {
                mGameView!!.updatePlayerToken(x, GameView.mGameTokenCard[x]) //update ball array
            } else {
                mGameView!!.setBoardSpaceValueCenter(GameView.mGameTokenCard[x])
            }
        }
        mGameView!!.sendTokensToServer()
        mGameView!!.invalidate()
        mClientWaitDialog = createClientWaitDialog()
        mClientWaitDialog!!.show()
    }

    private fun setGameTokenFromDialog() {  // when player has chosen value for wildcard token
        mChooseTokenDialog!!.dismiss()
        if (HUMAN_VS_NETWORK) {
            mGameView!!.currentPlayer = GameView.State.PLAYER1
        }
        if (!(HUMAN_VS_HUMAN or HUMAN_VS_NETWORK)) { //playing against Willy
            setComputerMove()
            finishTurn(false, false, false) //added to test if Willy wins
            mGameView!!.disableBall()
        } else if (HUMAN_VS_HUMAN) {
            finishTurn(false, true, false) //don't send message to make computer move but switch the player don't use player 2 for win testing
            highlightCurrentPlayer(mGameView!!.currentPlayer)
        } else {
            mGameView!!.disableBall()
            highlightCurrentPlayer(GameView.State.PLAYER2)
        }
        showPlayerTokenChoice()
        val movedMessage = "moved, $mBallMoved, $mSavedCell, $mPlayer1TokenChoice"
        if (HUMAN_VS_NETWORK) {
            if (mServerIsPlayingNow) {
                mServerThread!!.setMessageToClient(movedMessage)
            } else {
                mClientThread!!.setMessageToServer(movedMessage)
            }
            mGameView!!.setViewDisabled(true)
        }
    }

    private fun saveHumanWinner(winningPositionOnBoard: Int, positionStatus: Int) {
        humanWinningHashMap[winningPositionOnBoard] = positionStatus
        /*
         * second value indicates position is available for use after comparing against other entries in this map
         * second value: initialized to -1 upon creation
         * set to 0 if not available
         * set to 1 if available
         */
    }

    private fun selectBestMove(): IntArray { //this is the heart and soul of Willy Shmo - at least as far as his skill at playing this game
        val selectionArray = IntArray(2)
        var tokenSelected = -1
        var boardSpaceSelected = -1
        humanWinningHashMap.clear()
        if (mPlayer2TokenChoice == GameView.BoardSpaceValues.EMPTY) { //computer makes first move of game
            tokenSelected = mGameView!!.selectRandomComputerToken()
            //TODO - just for testing 1 specific case
            //tokenSelected = mGameView.selectSpecificComputerToken(BoardSpaceValues.CROSS, true);
            boardSpaceSelected = mGameView!!.selectRandomAvailableBoardSpace()
        } else {
            // populate array with available moves
            var availableSpaceCount = 0
            val availableValues = mGameView!!.boardSpaceAvailableValues
            val normalizedBoardPlayer1 = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
            val normalizedBoardPlayer2 = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
            val testAvailableValues = BooleanArray(GameView.BoardSpaceValues.BOARDSIZE) // false = not available
            var tokenChoice = mGameView!!.selectSpecificComputerToken(mPlayer2TokenChoice, true)
            //true = playing offensively which means we can select the xo card if we have one
            val boardSpaceValues = mGameView!!.boardSpaceValues

            //populate test board with xo token changed to player 2 token
            for (x in normalizedBoardPlayer1.indices) {
                normalizedBoardPlayer1[x] = boardSpaceValues[x]
                normalizedBoardPlayer2[x] = boardSpaceValues[x]
                if (normalizedBoardPlayer1[x] == GameView.BoardSpaceValues.CIRCLECROSS) {
                    normalizedBoardPlayer1[x] = mPlayer1TokenChoice
                    normalizedBoardPlayer2[x] = mPlayer2TokenChoice
                }
            }
            var trialBoardSpaceSelected1 = -1
            var trialBoardSpaceSelected2 = -1
            var trialBoardSpaceSelected3 = -1
            for (x in availableValues.indices) {
                if (availableValues[x]) {
                    availableSpaceCount++
                    if (trialBoardSpaceSelected1 == -1) {
                        trialBoardSpaceSelected1 = x
                    } else {
                        if (trialBoardSpaceSelected2 == -1) {
                            trialBoardSpaceSelected2 = x
                        } else {
                            if (trialBoardSpaceSelected3 == -1) {
                                trialBoardSpaceSelected3 = x
                            }
                        }
                    }
                }
            }
            if (availableSpaceCount == 1) { //last move!
                if (tokenChoice == -1) {
                    tokenChoice = mGameView!!.selectLastComputerToken()
                }
                tokenSelected = tokenChoice
                boardSpaceSelected = trialBoardSpaceSelected1
            }
            if (tokenChoice > -1) {
                if (tokenChoice == GameView.BoardSpaceValues.CIRCLECROSS) {
                    tokenChoice = mPlayer2TokenChoice
                }
                for (x in availableValues.indices) {
                    if (availableValues[x]) {
                        val testBoard = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                        //copy normalizedBoard to testBoard
                        for (y in testBoard.indices) {
                            testBoard[y] = normalizedBoardPlayer2[y]
                        }
                        testBoard[x] = mPlayer2TokenChoice
                        val winnerFound = checkWinningPosition(testBoard)
                        if (winnerFound[0] > -1 || winnerFound[1] > -1 || winnerFound[2] > -1) {
                            tokenSelected = tokenChoice
                            boardSpaceSelected = x
                            break
                        }
                    }
                    // if we reach here then the computer cannot win on this move
                }
            }
            /* try to block the human win on the next move here
             *
             * There is a possibility that the human will have more than 1 winning move. So, lets save each
             * winning outcome in a HashMap and re-test them with successive available moves until we find one
             * that results in no winning next available move for human.
             */
            if (tokenSelected == -1) { //try again with human selected token
                tokenChoice = mGameView!!.selectSpecificComputerToken(mPlayer2TokenChoice, false)
                if (tokenChoice > -1) {
//            		int computerBlockingMove = -1;
                    for (x in availableValues.indices) {
                        if (availableValues[x]) {
                            val testBoard = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                            //copy normalizedBoard to testBoard
                            for (y in testBoard.indices) {
                                testBoard[y] = normalizedBoardPlayer1[y]
                            }
                            testBoard[x] = mPlayer1TokenChoice
                            // since there can be multiple winning moves available for the human
                            // move computer token to boardSpaceSelected
                            // reset available and re-test for winner using mPlayer1TokenChoice
                            // if winner not found then set tokenSelected to tokenChoice and set boardSpaceSelected to x
                            val winnerFound = checkWinningPosition(testBoard)
                            if (winnerFound[0] > -1 || winnerFound[1] > -1 || winnerFound[2] > -1) {
                                saveHumanWinner(x, -1)
                            }
                        }
                    }
                    //System.out.println("human winner list size: "+humanWinningHashMap.size());
                    if (humanWinningHashMap.size == 1) {
                        val onlyWinningPosition: Array<Any> = humanWinningHashMap.keys.toTypedArray()
                        val testMove = onlyWinningPosition[0] as Int
                        tokenSelected = tokenChoice
                        boardSpaceSelected = testMove
                    } else if (humanWinningHashMap.size > 1) {
                        val it: Iterator<Int> = humanWinningHashMap.keys.iterator()
                        while (it.hasNext()) {
                            val winningPosition = it.next()
                            //System.out.println("winning position: "+winningPosition);
                            val testBoard = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                            for (y in testBoard.indices) {
                                testBoard[y] = normalizedBoardPlayer1[y]
                            }
                            testBoard[winningPosition] = mPlayer2TokenChoice
                            mGameView!!.setAvailableMoves(winningPosition, testBoard, testAvailableValues)
                            val it2: Iterator<Int> = humanWinningHashMap.keys.iterator()
                            while (it2.hasNext()) {
                                val testMove = it2.next()
                                if (winningPosition == testMove) {
                                    continue  // no point in testing against same value
                                }
                                val spaceOkToUse = humanWinningHashMap[testMove] as Int
                                //System.out.println("testing "+testMove+ " against winning position: "+ winningPosition);
                                // testMove = a winning move human
                                if (testAvailableValues[testMove]) {
                                    //computerBlockingMove = winningPosition;
                                    //break;
                                    saveHumanWinner(testMove, 0) // space cannot be used
                                    //System.out.println("reset value at "+testMove+ " to unavailable(false) for "+ winningPosition);
                                } else {
                                    if (spaceOkToUse != 0) saveHumanWinner(testMove, 1) //space is ok to use
                                    //System.out.println("reset value at "+testMove+ " to ok to use for "+ winningPosition);
                                }
                            }
                        }
                        val it3: Iterator<Int> = humanWinningHashMap.keys.iterator()
                        while (it3.hasNext()) {
                            val computerBlockingMove = it3.next()
                            val spaceAvailable = humanWinningHashMap[computerBlockingMove] as Int
                            if (spaceAvailable == 1) {
                                boardSpaceSelected = computerBlockingMove
                                tokenSelected = tokenChoice
                                //System.out.println("found good move for computer at "+boardSpaceSelected);
                            }
                        }
                    }
                }
            }
            // if we reach here then Willy cannot win on this move and the human
            // cannot win on the next
            // so we'll select a position that at least doesn't give the human a win and move there
            if (tokenSelected == -1) {
                tokenChoice = mGameView!!.selectSpecificComputerToken(mPlayer1TokenChoice, false)
                if (tokenChoice > -1) {
                    for (x in availableValues.indices) {
                        if (availableValues[x]) {
                            val testBoard = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                            //copy normalizedBoard to testBoard
                            for (y in testBoard.indices) {
                                testBoard[y] = normalizedBoardPlayer1[y]
                            }
                            testBoard[x] = mPlayer1TokenChoice
                            val winnerFound = checkWinningPosition(testBoard)
                            //test to see if human can't win if he were to move here
                            // if human cannot win then this is a candidate move for computer
                            if (winnerFound[0] == -1 && winnerFound[1] == -1 && winnerFound[2] == -1) {
                                //take it one step further and see if moving to this position gives the human a win in the next move,
                                // if it does then try next available board position
                                val humanToken = mGameView!!.selectSpecificHumanToken(mPlayer1TokenChoice)
                                val testBoard2 = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                                if (humanToken > -1) {
                                    var computerCanUseMove = true
                                    for (z in availableValues.indices) {
                                        //copy the board with trial move from above to another test board
                                        for (y in testBoard.indices) {
                                            testBoard2[y] = testBoard[y]
                                        }
                                        //set available moves for new test board
                                        mGameView!!.setAvailableMoves(x, testBoard2, testAvailableValues)
                                        if (testAvailableValues[z] && z != x) {
                                            testBoard2[z] = mPlayer1TokenChoice
                                            val winnerFound2 = checkWinningPosition(testBoard2)
                                            if (winnerFound2[0] > -1 || winnerFound2[1] > -1 || winnerFound2[2] > -1) {
                                                computerCanUseMove = false
                                                break
                                            }
                                        }
                                    }
                                    //System.out.println("test case 2 selection made, computerCanUseMove = "+computerCanUseMove+" boardSpaceSelected: "+x);
                                    if (computerCanUseMove) {
                                        tokenSelected = tokenChoice
                                        boardSpaceSelected = x
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (tokenSelected == -1) {
                val humanTokenChoice = mGameView!!.selectSpecificHumanToken(mPlayer1TokenChoice)
                tokenChoice = mGameView!!.selectSpecificComputerToken(mPlayer2TokenChoice, false)
                if (availableSpaceCount == 2) { //we're down to our last 2 possible moves
                    //if we get here we're on the last move and we know we can't win with it.
                    //so let's see if the human could make the computer win
                    val testBoard = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                    //copy normalizedBoard to testBoard
                    for (y in testBoard.indices) {
                        testBoard[y] = normalizedBoardPlayer1[y]
                    }
                    if (humanTokenChoice > -1) {
                        testBoard[trialBoardSpaceSelected1] = mPlayer1TokenChoice
                        val winnerFound = checkWinningPosition(testBoard)
                        if (winnerFound[0] > -1 || winnerFound[1] > -1 || winnerFound[2] > -1) {
                            boardSpaceSelected = trialBoardSpaceSelected2
                            if (tokenChoice == -1) {
                                tokenChoice = mGameView!!.selectLastComputerToken()
                            }
                            tokenSelected = tokenChoice
                        } else {
                            tokenSelected = mGameView!!.selectLastComputerToken()
                            boardSpaceSelected = trialBoardSpaceSelected1
                        }
                    } else {
                        if (tokenChoice == -1) tokenChoice = mGameView!!.selectLastComputerToken()
                        tokenSelected = tokenChoice
                        testBoard[trialBoardSpaceSelected2] = mPlayer1TokenChoice
                        val winnerFound = checkWinningPosition(testBoard)
                        boardSpaceSelected = if (winnerFound[0] > -1 || winnerFound[1] > -1 || winnerFound[2] > -1) {
                            trialBoardSpaceSelected1
                            //System.out.println("winning move found for human, moving computer token to "+trialBoardSpaceSelected1);
                        } else {
                            trialBoardSpaceSelected2
                            //System.out.println("moving computer token to "+trialBoardSpaceSelected2);
                        }
                    }
                }
                if (availableSpaceCount >= 3) {
                    tokenChoice = mGameView!!.selectSpecificHumanToken(mPlayer1TokenChoice)
                    if (tokenChoice > -1) {
                        val testBoard = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                        for (x in availableValues.indices) {
                            for (y in testBoard.indices) {
                                testBoard[y] = normalizedBoardPlayer1[y]
                            }
                            if (availableValues[x]) {
                                testBoard[x] = mPlayer1TokenChoice
                                val winnerFound = checkWinningPosition(testBoard)
                                if (winnerFound[0] > -1 || winnerFound[1] > -1 || winnerFound[2] > -1) {
                                    tokenChoice = mGameView!!.selectSpecificComputerToken(mPlayer2TokenChoice, false)
                                    tokenSelected = if (tokenChoice > -1) {
                                        tokenChoice
                                    } else {
                                        mGameView!!.selectLastComputerToken() //no choice here
                                    }
                                    boardSpaceSelected = x
                                    break
                                }
                            }
                        }
                        //if we get here then there is no winning move available for human player
                        // for us to block so we'll just select the next available position and move there
                        if (tokenSelected == -1) {
                            tokenChoice = mGameView!!.selectSpecificComputerToken(mPlayer2TokenChoice, false)
                            //System.out.println("3 or more spaces open and still no choice made yet");
                        }
                        tokenSelected = if (tokenChoice > -1) {
                            tokenChoice
                        } else {
                            mGameView!!.selectLastComputerToken() //no choice here
                        }
                        for (x in availableValues.indices) {
                            if (availableValues[x]) {
                                boardSpaceSelected = x
                                break
                            }
                        }
                    } else {
                        tokenChoice = mGameView!!.selectSpecificComputerToken(mPlayer2TokenChoice, false)
                        if (tokenChoice == -1) {
                            tokenChoice = mGameView!!.selectLastComputerToken() //no choice here
                        }
                        val testBoard = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
                        //System.out.println("3 or more spaces open last attempt made");
                        for (x in availableValues.indices) {
                            for (y in testBoard.indices) {
                                testBoard[y] = normalizedBoardPlayer2[y]
                            }
                            if (availableValues[x]) {
                                testBoard[x] = mPlayer1TokenChoice
                                val winnerFound = checkWinningPosition(testBoard)
                                if (winnerFound[0] == -1 && winnerFound[1] == -1 && winnerFound[2] == -1) {
                                    tokenSelected = tokenChoice
                                    boardSpaceSelected = x
                                    break
                                }
                            }
                        }
                        if (tokenSelected == -1) {
                            tokenSelected = tokenChoice
                            for (x in availableValues.indices) {
                                if (availableValues[x]) {
                                    boardSpaceSelected = x
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }
        mGameView!!.disableBall(tokenSelected)
        selectionArray[0] = boardSpaceSelected
        selectionArray[1] = tokenSelected
        return selectionArray
    }

    private fun setNetworkMove(boardPosition: Int, tokenMoved: Int) {
        val resultValue = if (tokenMoved > 3) {
            tokenMoved - 4
        } else {
            tokenMoved + 4
        }
        mGameView!!.moveComputerToken(boardPosition, resultValue) //move token selected to location on board
        mLastCellSelected = boardPosition
        playSound(R.raw.finish_move)
        mGameView!!.disableBall(resultValue)
        mGameView!!.setCell(boardPosition, GameView.State.PLAYER2) //set State table
    }

    private fun setComputerMove(): Int {
        var computerToken = GameView.BoardSpaceValues.EMPTY
        val index = selectBestMove() //0 = boardSpaceSelected, 1 = tokenSelected
        if (index[0] != -1) {
            playSound(R.raw.computer_token_move_sound)
            mGameView!!.setCell(index[0], GameView.State.PLAYER2) // set State table - the computer (Willy) is always PLAYER2
            computerToken = mGameView!!.moveComputerToken(index[0], index[1]) //move computer token to location on board
        }
        return computerToken
    }

    private fun networkCallBackFinish() {
        finishTurn(false, false, true) //don't send message to make computer move don't switch the player don't use player 2 for win testing
        val testText = mButtonNext!!.text.toString()
        val playAgainString = getString(R.string.playAgain)
        if (testText.contains(playAgainString)) {
            highlightCurrentPlayer(GameView.State.EMPTY)
            return
        }
        val currentPlayer = mGameView!!.currentPlayer
        highlightCurrentPlayer(currentPlayer)
        mGameView!!.setViewDisabled(false)
    }

    private inner class MyHandlerCallback : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            writeToLog("MyHandlerCallback", "msg.what value: " + msg.what)
            if (msg.what == PLAYER_TIMED_OUT_CLIENT) {
                playSound(R.raw.timeout3)
                mForfeitGameDialog = createForfeitGameDialog("client")
                mForfeitGameDialog!!.show()
            }
            if (msg.what == PLAYER_TIMED_OUT_SERVER) {
                playSound(R.raw.timeout4)
                mForfeitGameDialog = createForfeitGameDialog("server")
                mForfeitGameDialog!!.show()
            }
            if (msg.what == DISMISS_WAIT_FOR_NEW_GAME_FROM_CLIENT) {
                if (mHostWaitDialog != null) {
                    mHostWaitDialog!!.dismiss()
                    writeToLog("MyHandlerCallback", "host wait dialog dismissed")
                    mHostWaitDialog = null
                }
                return true
            }
            if (msg.what == DISMISS_WAIT_FOR_NEW_GAME_FROM_SERVER) { //This is called from the Client thread
                val urlData = ("/gamePlayer/update/?id=$mPlayer1Id&playingNow=true&opponentId=$mPlayer2Id&userName=$mPlayer1Name")
                val messageResponse = sendMessageToAppServer(urlData,false)
                if (mClientWaitDialog == null) {
                    sendToastMessage(getString(R.string.torqued_up_really_well))
                    return true
                }
                mClientWaitDialog!!.dismiss()
                writeToLog("MyHandlerCallback", "client wait dialog dismissed, messageResponse: $messageResponse")
                mClientWaitDialog = null
                //TODO - check distanceToOpponent here client side
                return true
            }
            if (msg.what == ACCEPT_INCOMING_GAME_REQUEST_FROM_CLIENT) {
                if (mServerHasOpponent != null) {
                    if ("true" == mServerHasOpponent) {
                        setNetworkGameStatusAndResponse(true, false)
                    } else {
                        setNetworkGameStatusAndResponse(false, true)
                    }
                } else {
                    if (isServerRunning) {
                        acceptIncomingGameRequestFromClient()
                    }
                }
                return true
            }

            if (msg.what == MSG_NETWORK_SERVER_REFUSED_GAME) {
                displayServerRefusedGameAlert(mNetworkOpponentPlayerName)
                //displayOpponentLeftGameAlert("server", mPlayer2Name)
                mGameStarted = false
            }
            if (msg.what == MSG_NETWORK_SERVER_LEFT_GAME) {
                mOpponentLeftGameAlert = displayOpponentLeftGameAlert("server", mPlayer2Name)
                //mPlayer2Name = null
                mGameStarted = false
            }
            if (msg.what == MSG_OPPONENT_TIMED_OUT) {
                mWinByTimeoutAlert = displayWinByTimeoutAlert(mPlayer2Name)
                //mPlayer2Name = null
                mGameStarted = false
            }
            if (msg.what == MSG_NETWORK_CLIENT_LEFT_GAME || msg.what == MSG_NETWORK_CLIENT_REFUSED_GAME) {
                mOpponentLeftGameAlert = displayOpponentLeftGameAlert("client", mPlayer2Name)
                //mPlayer2Name = null
                mGameStarted = false
                mServerIsPlayingNow = false
            }
            if (msg.what == NEW_GAME_FROM_CLIENT) {
                mGameView!!.initalizeGameValues()
                mPlayer2NameTextValue!!.setText(mPlayer2Name)
                mButtonNext!!.text = mButtonStartText
                mButtonNext!!.isEnabled = false
                showPlayerTokenChoice()
                for (x in mTokensFromClient!!.indices) {
                    val tokenArray = mTokensFromClient!![x]
                    if (x < 8) {
                        mGameView!!.updatePlayerToken(tokenArray[0], tokenArray[1])
                    } else {
                        mGameView!!.setBoardSpaceValueCenter(tokenArray[1])
                    }
                }
                val moveFirst = mRandom.nextBoolean()
                if (moveFirst) {
                    mGameView!!.currentPlayer = GameView.State.PLAYER1
                    highlightCurrentPlayer(GameView.State.PLAYER1)
                    mGameView!!.setViewDisabled(false)
                    stopMoveWaitingTimerThread()
                    startMoveWaitingTimerThread("server")
                } else {
                    mGameView!!.currentPlayer = GameView.State.PLAYER1 //this value will be switched in onClick method
                    highlightCurrentPlayer(GameView.State.PLAYER2)
                    mGameView!!.setViewDisabled(true)
                    if (mServerThread != null)
                        mServerThread!!.setMessageToClient("moveFirst")
                }
                mGameView!!.invalidate()
                if (mClientWaitDialog != null) {
                    mClientWaitDialog!!.dismiss()
                }
                return true
            }
            if (msg.what == MSG_NETWORK_CLIENT_TURN) {
                if (mClientThread != null) {
                    val boardPosition = mClientThread!!.boardPosition
                    val tokenMoved = mClientThread!!.tokenMoved
                    setNetworkMove(boardPosition, tokenMoved)
                    networkCallBackFinish()
                    mGameView!!.invalidate()
                }
                return true
            }
            if (msg.what == MSG_NETWORK_SERVER_TURN) {
                if (mServerThread != null) {
                    val boardPosition = mServerThread!!.boardPosition
                    val tokenMoved = mServerThread!!.tokenMoved
                    setNetworkMove(boardPosition, tokenMoved)
                    networkCallBackFinish()
                    val testText = mButtonNext!!.text.toString()
                    if (testText.contains(getString(R.string.playAgain))) {
                        if (mServerIsPlayingNow) { // if win came from client side we need to send back a message to give the client the ability to respond
                            mServerThread!!.setMessageToClient("game over")
                        }
                    }
                }
                return true
            }
            if (msg.what == MSG_NETWORK_SET_TOKEN_CHOICE) {
                showPlayerTokenChoice()
            }
            if (msg.what == MSG_NETWORK_CLIENT_MAKE_FIRST_MOVE) {
                mGameView!!.currentPlayer = GameView.State.PLAYER1
                highlightCurrentPlayer(GameView.State.PLAYER1)
                mGameView!!.setViewDisabled(false)
            }
            if (msg.what == MSG_COMPUTER_TURN) {
//            	int computerToken = BoardSpaceValues.EMPTY;
// consider setting a difficulty level

/*
 * trivial cases:
 * 		if only 1 token on board then just put token anywhere  but don't select xo token
 * 		if only 1 space is available then just put last card there
 *
 * test cases using mPlayer2TokenChoice:
 * look for available computer token that matches mPlayer2TokenChoice
 * if found test for win for computer player using mPlayer2TokenChoice value
 * testing each available position
 * 		if (testForWin == true) we are done
 * test for human player win with opposing token
 * 		if found move token there
 *
 * test cases using mPlayer2TokenChoice:
 * 		loop thru available board positions
 * 		put token anywhere where result doesn't cause human player to win
 *
 * else choose random position and place random token there
 *
 * test for win possibility changing xo card on board to computer token
 * test for block possibility changing xo card on board to player 1 token
 * else just put down token randomly for now
 *
 */
                val computerToken = setComputerMove()

// for now, the computer will never select the xo token for its opening move but we may change this in
// the future. As of 07/10/2010, the computer will select the xo token only on a winning move or for the last
// move possible.
                if (mPlayer2TokenChoice == GameView.BoardSpaceValues.EMPTY) {
                    if (computerToken != GameView.BoardSpaceValues.EMPTY) mPlayer2TokenChoice = computerToken
                    mPlayer1TokenChoice = if (mPlayer2TokenChoice == GameView.BoardSpaceValues.CIRCLECROSS ||
                        mPlayer2TokenChoice == GameView.BoardSpaceValues.CROSS) { //computer will always choose X if it selects the XO card
                        GameView.BoardSpaceValues.CIRCLE // on its first move, we may want to change this behavior
                        // see comments above
                    } else {
                        GameView.BoardSpaceValues.CROSS
                    }
                    mGameView!!.setPlayer1TokenChoice(mPlayer1TokenChoice)
                    mGameView!!.setPlayer2TokenChoice(mPlayer2TokenChoice)
                    showPlayerTokenChoice()
                }
                finishTurn(false, true, false) //don't send message to make computer move but do switch the player and don't use player 2 for win testing
                return true
            }
            return false
        }
    }

    private fun getOtherPlayer(player: GameView.State): GameView.State {
        return if (player == GameView.State.PLAYER1) GameView.State.PLAYER2 else GameView.State.PLAYER1
    }

    //FIXME - consider highlighting the border of the enclosing rectangle around the player's name instead
    fun highlightCurrentPlayer(player: GameView.State) {
        val anim: Animation = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 500 //You can manage the time of the blink with this parameter
        anim.startOffset = 20
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        val anim2: Animation = AlphaAnimation(0.0f, 1.0f)
        anim2.duration = 500 //You can manage the time of the blink with this parameter
        anim2.startOffset = 20
        anim2.repeatMode = Animation.REVERSE
        anim2.repeatCount = 0
        if (player == GameView.State.PLAYER1) {
            mPlayer1NameTextValue!!.background = ResourcesCompat.getDrawable(resources, R.drawable.backwithgreenborder, null)
            mPlayer2NameTextValue!!.background = ResourcesCompat.getDrawable(resources, R.drawable.backwithwhiteborder, null)
            mPlayer1NameTextValue!!.startAnimation(anim)
            mPlayer2NameTextValue!!.startAnimation(anim2)
        } else if (player == GameView.State.PLAYER2) {
            mPlayer2NameTextValue!!.background = ResourcesCompat.getDrawable(resources, R.drawable.backwithgreenborder, null)
            mPlayer2NameTextValue!!.startAnimation(anim)
            mPlayer1NameTextValue!!.background = ResourcesCompat.getDrawable(resources, R.drawable.backwithwhiteborder, null)
            mPlayer1NameTextValue!!.startAnimation(anim2)
        } else {
            mPlayer1NameTextValue!!.background = ResourcesCompat.getDrawable(resources, R.drawable.backwithwhiteborder, null)
            mPlayer2NameTextValue!!.background = ResourcesCompat.getDrawable(resources, R.drawable.backwithwhiteborder, null)
            mPlayer1NameTextValue!!.startAnimation(anim2)
            mPlayer2NameTextValue!!.startAnimation(anim2)
        }
    }

    private fun finishTurn(makeComputerMove: Boolean, switchPlayer: Boolean, usePlayer2: Boolean) {
        writeToLog("finishTurn", "called with switchPLayer $switchPlayer usePlayer2: $usePlayer2")
        var player = mGameView!!.currentPlayer
        if (usePlayer2) { // if we're playing over a network then current player is always player 1
            player = GameView.State.PLAYER2 // so we need to add some extra logic to test winner on player 2 over the network
        }
        mGameView!!.disableBall()
        if (!checkGameFinished(player, usePlayer2)) {
            if (switchPlayer) {
                player = selectTurn(getOtherPlayer(player))
                if (player == GameView.State.PLAYER2 && makeComputerMove && !(HUMAN_VS_HUMAN or HUMAN_VS_NETWORK)) {
                    writeToLog("finishTurn", "gonna send computer_turn message")
                    mHandler.sendEmptyMessageDelayed(MSG_COMPUTER_TURN, COMPUTER_DELAY_MS)
                }
                highlightCurrentPlayer(player)
            }
        }
    }

    // Given the existence of the xo token, there is a possibility that both players could be winners.
    // in this case we will give precedence to the token type of the player that made the winning move.
    private fun checkGameFinished(player: GameView.State, usePlayer2: Boolean): Boolean {
        val boardSpaceValues = mGameView!!.boardSpaceValues
        var data = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
        var wildCardValue = mPlayer1TokenChoice
        if (player == GameView.State.PLAYER2) {
            wildCardValue = mPlayer2TokenChoice
        }
        for (x in data.indices) {
            data[x] = boardSpaceValues[x]
            if (data[x] == GameView.BoardSpaceValues.CIRCLECROSS) {
                data[x] = wildCardValue //BoardSpaceValues.CROSS;
            }
        }
        if (testForWinner(data, usePlayer2)) {
            return true
        }
        wildCardValue = if (wildCardValue == mPlayer1TokenChoice) mPlayer2TokenChoice else mPlayer1TokenChoice
        data = IntArray(GameView.BoardSpaceValues.BOARDSIZE)
        for (x in data.indices) {
            data[x] = boardSpaceValues[x]
            if (data[x] == GameView.BoardSpaceValues.CIRCLECROSS) {
                data[x] = wildCardValue
            }
        }
        if (testForWinner(data, usePlayer2)) {
            return true
        }
        if (mGameView!!.testBoardFull(9)) {
            setFinished(GameView.State.EMPTY, -1, -1, -1)
            if (HUMAN_VS_NETWORK) {
                updateWebServerScore()
            }
            return true
        }
        return false
    }

    private fun checkWinningPosition(data: IntArray): IntArray {
        var col = -1
        var row = -1
        var diag = -1
        var winningToken = -1
        var winningPosition1 = -1
        var winningPosition2 = -1
        var winningPosition3 = -1

        // check rows
        var j = 0
        var k = 0
        while (j < 5) {
            if (data[k] != GameView.BoardSpaceValues.EMPTY && data[k] == data[k + 1] && data[k] == data[k + 2]) {
                winningToken = data[k]
                row = j
                winningPosition1 = k
                winningPosition2 = k + 1
                winningPosition3 = k + 2
                break
            }
            if (data[k + 1] != GameView.BoardSpaceValues.EMPTY && data[k + 1] == data[k + 2] && data[k + 2] == data[k + 3]) {
                winningToken = data[k + 1]
                row = j
                winningPosition1 = k + 1
                winningPosition2 = k + 2
                winningPosition3 = k + 3
                break
            }
            if (data[k + 2] != GameView.BoardSpaceValues.EMPTY && data[k + 2] == data[k + 3] && data[k + 2] == data[k + 4]) {
                winningToken = data[k + 2]
                row = j
                winningPosition1 = k + 2
                winningPosition2 = k + 3
                winningPosition3 = k + 4
                break
            }
            j++
            k += 5
        }

        // check columns
        if (row == -1) {
            for (i in 0..4) {
                if (data[i] != GameView.BoardSpaceValues.EMPTY && data[i] == data[i + 5] && data[i] == data[i + 10]) {
                    winningToken = data[i]
                    col = i
                    winningPosition1 = i
                    winningPosition2 = i + 5
                    winningPosition3 = i + 10
                    break
                }
                if (data[i + 5] != GameView.BoardSpaceValues.EMPTY && data[i + 5] == data[i + 10] && data[i + 5] == data[i + 15]) {
                    winningToken = data[i + 5]
                    winningPosition1 = i + 5
                    winningPosition2 = i + 10
                    winningPosition3 = i + 15
                    col = i
                    break
                }
                if (data[i + 10] != GameView.BoardSpaceValues.EMPTY && data[i + 10] == data[i + 15] && data[i + 10] == data[i + 20]) {
                    winningToken = data[i + 10]
                    col = i
                    winningPosition1 = i + 10
                    winningPosition2 = i + 15
                    winningPosition3 = i + 20
                    break
                }
            }
        }

        // check diagonals
        //upper left to lower right diagonals:
        if (row == -1 && col == -1) {
            if (data[0] != GameView.BoardSpaceValues.EMPTY && data[0] == data[6] && data[0] == data[12]) {
                winningToken = data[0]
                diag = 0
                winningPosition1 = 0
                winningPosition2 = 6
                winningPosition3 = 12
            } else if (data[1] != GameView.BoardSpaceValues.EMPTY && data[1] == data[7] && data[1] == data[13]) {
                winningToken = data[1]
                diag = 2
                winningPosition1 = 1
                winningPosition2 = 7
                winningPosition3 = 13
            } else if (data[2] != GameView.BoardSpaceValues.EMPTY && data[2] == data[8] && data[2] == data[14]) {
                winningToken = data[2]
                diag = 3
                winningPosition1 = 2
                winningPosition2 = 8
                winningPosition3 = 14
            } else if (data[5] != GameView.BoardSpaceValues.EMPTY && data[5] == data[11] && data[5] == data[17]) {
                winningToken = data[5]
                diag = 4
                winningPosition1 = 5
                winningPosition2 = 11
                winningPosition3 = 17
            } else if (data[6] != GameView.BoardSpaceValues.EMPTY && data[6] == data[12] && data[6] == data[18]) {
                winningToken = data[6]
                diag = 0
                winningPosition1 = 6
                winningPosition2 = 12
                winningPosition3 = 18
            } else if (data[7] != GameView.BoardSpaceValues.EMPTY && data[7] == data[13] && data[7] == data[19]) {
                winningToken = data[7]
                diag = 2
                winningPosition1 = 7
                winningPosition2 = 13
                winningPosition3 = 19
            } else if (data[10] != GameView.BoardSpaceValues.EMPTY && data[10] == data[16] && data[10] == data[22]) {
                winningToken = data[10]
                diag = 5
                winningPosition1 = 10
                winningPosition2 = 16
                winningPosition3 = 22
            } else if (data[11] != GameView.BoardSpaceValues.EMPTY && data[11] == data[17] && data[11] == data[23]) {
                winningToken = data[11]
                diag = 4
                winningPosition1 = 11
                winningPosition2 = 17
                winningPosition3 = 23
            } else if (data[12] != GameView.BoardSpaceValues.EMPTY && data[12] == data[18] && data[12] == data[24]) {
                winningToken = data[12]
                diag = 0
                winningPosition1 = 12
                winningPosition2 = 18
                winningPosition3 = 24

                //check diagonals running from lower left to upper right
            } else if (data[2] != GameView.BoardSpaceValues.EMPTY && data[2] == data[6] && data[2] == data[10]) {
                winningToken = data[2]
                diag = 1
                winningPosition1 = 2
                winningPosition2 = 6
                winningPosition3 = 10
            } else if (data[3] != GameView.BoardSpaceValues.EMPTY && data[3] == data[7] && data[3] == data[11]) {
                winningToken = data[3]
                diag = 6
                winningPosition1 = 3
                winningPosition2 = 7
                winningPosition3 = 11
            } else if (data[4] != GameView.BoardSpaceValues.EMPTY && data[4] == data[8] && data[4] == data[12]) {
                winningToken = data[4]
                diag = 7
                winningPosition1 = 4
                winningPosition2 = 8
                winningPosition3 = 12
            } else if (data[9] != GameView.BoardSpaceValues.EMPTY && data[9] == data[13] && data[9] == data[17]) {
                winningToken = data[9]
                diag = 8
                winningPosition1 = 9
                winningPosition2 = 13
                winningPosition3 = 17
            } else if (data[14] != GameView.BoardSpaceValues.EMPTY && data[14] == data[18] && data[14] == data[22]) {
                winningToken = data[14]
                diag = 9
                winningPosition1 = 14
                winningPosition2 = 18
                winningPosition3 = 22
            } else if (data[7] != GameView.BoardSpaceValues.EMPTY && data[7] == data[11] && data[7] == data[15]) {
                winningToken = data[7]
                diag = 6
                winningPosition1 = 7
                winningPosition2 = 11
                winningPosition3 = 15
            } else if (data[8] != GameView.BoardSpaceValues.EMPTY && data[8] == data[12] && data[8] == data[16]) {
                winningToken = data[8]
                diag = 7
                winningPosition1 = 8
                winningPosition2 = 12
                winningPosition3 = 16
            } else if (data[12] != GameView.BoardSpaceValues.EMPTY && data[12] == data[16] && data[12] == data[20]) {
                winningToken = data[12]
                diag = 7
                winningPosition1 = 12
                winningPosition2 = 16
                winningPosition3 = 20
            } else if (data[13] != GameView.BoardSpaceValues.EMPTY && data[13] == data[17] && data[13] == data[21]) {
                winningToken = data[13]
                diag = 8
                winningPosition1 = 13
                winningPosition2 = 17
                winningPosition3 = 21
            }
        }
        val returnValue = IntArray(7)
        returnValue[0] = col
        returnValue[1] = row
        returnValue[2] = diag
        returnValue[3] = winningToken
        returnValue[4] = winningPosition1
        returnValue[5] = winningPosition2
        returnValue[6] = winningPosition3
        return returnValue
    }

    private fun testForWinner(data: IntArray, usePlayer2: Boolean): Boolean {
        val winnerFound = checkWinningPosition(data)

// For scoring purposes we will need to determine if the current player is the winner when the last card
// was placed or if the opposing player is the winner.
// if the opposing player wins then more points are awarded to the opponent
        var player: GameView.State? = null
        var currentPlayer = mGameView!!.currentPlayer
        if (usePlayer2) // if we're playing over a network then current player is always player 1
            currentPlayer = GameView.State.PLAYER2 // so we need to add some extra logic to test winner on player 2 over the network
        if (winnerFound[3] > -1) {
            if (winnerFound[3] == mPlayer1TokenChoice) {
                player = GameView.State.PLAYER1
                var player1Score = mPlayer1Score
                if (HUMAN_VS_NETWORK) {
                    player1Score = mPlayer1NetworkScore
                }
                if (currentPlayer == GameView.State.PLAYER1) {
                    player1Score += mRegularWin
                    playSound(R.raw.player_win)
                    checkForPrizeWin(winnerFound[4], winnerFound[5], winnerFound[6], PrizeValue.REGULARPRIZE)
                } else {
                    player1Score += mSuperWin
                    playSound(R.raw.player_win_shmo)
                    checkForPrizeWin(winnerFound[4], winnerFound[5], winnerFound[6], PrizeValue.SHMOPRIZE)
                }
                if (HUMAN_VS_NETWORK) {
                    mPlayer1NetworkScore = player1Score
                } else {
                    mPlayer1Score = player1Score
                }
            } else {
                player = GameView.State.PLAYER2
                var player2Score = mPlayer2Score
                if (HUMAN_VS_NETWORK) {
                    player2Score = mPlayer2NetworkScore
                }
                if (currentPlayer == GameView.State.PLAYER2) {
                    if (HUMAN_VS_HUMAN || HUMAN_VS_NETWORK) {
                        player2Score += mRegularWin
                        playSound(R.raw.player_win)
                    } else {
                        mWillyScore += mRegularWin
                        playSound(R.raw.willy_win)
                    }
                } else {
                    if (HUMAN_VS_HUMAN || HUMAN_VS_NETWORK) {
                        player2Score += mSuperWin
                        playSound(R.raw.player_win_shmo)
                    } else {
                        mWillyScore += mSuperWin
                        playSound(R.raw.willy_win_shmo)
                    }
                }
                if (HUMAN_VS_HUMAN) {
                    mPlayer2Score = player2Score
                }
                if (HUMAN_VS_NETWORK) {
                    mPlayer2NetworkScore = player2Score
                }
            }
        }
        if (winnerFound[0] != -1 || winnerFound[1] != -1 || winnerFound[2] != -1) {
            if (player != null) {
                setFinished(player, winnerFound[0], winnerFound[1], winnerFound[2])
            }
            return true
        }
        return false
    }

    private fun checkForPrizeWin(winningPosition1: Int, winningPosition2: Int, winningPosition3: Int, winType: Int) {
        if (mLastCellSelected == GameView.prizeLocation) {
            if (winType == PrizeValue.SHMOPRIZE) {
                showPrizeWon(PrizeValue.SHMOGRANDPRIZE)
            } else {
                showPrizeWon(PrizeValue.GRANDPRIZE)
            }
        } else if (GameView.prizeLocation == winningPosition1 || GameView.prizeLocation == winningPosition2 || GameView.prizeLocation == winningPosition3) {
            if (winType == PrizeValue.SHMOPRIZE) {
                showPrizeWon(PrizeValue.SHMOPRIZE)
            } else {
                showPrizeWon(PrizeValue.REGULARPRIZE)
            }
        }
    }

    private fun setFinished(player: GameView.State, col: Int, row: Int, diagonal: Int) {
        mGameView!!.currentPlayer = GameView.State.WIN
        mGameView!!.winner = player
        mGameView!!.isEnabled = false
        mGameView!!.setFinished(col, row, diagonal)
        setWinState(player)
        if (player == GameView.State.PLAYER2) {
            mGameView!!.invalidate()
        }
        displayScores()
    }

    private fun setWinState(player: GameView.State?) {
        stopMoveWaitingTimerThread()
        mButtonNext!!.isEnabled = true
        val text: String
        var player1Name: String = getString(R.string.player_1)
        var player2Name: String = getString(R.string.player_2)
        if (mPlayer1Name != null) {
            player1Name = mPlayer1Name as String
        }
        if (mPlayer2Name != null) {
            player2Name = mPlayer2Name as String
        }
        val playAgainString = getString(R.string.playAgain)
        text = if (player == GameView.State.EMPTY) {
            getString(R.string.tie)
        } else if (player == GameView.State.PLAYER1) {
            "$player1Name ${getString(R.string.player_wins)} $playAgainString"
        } else {
            "$player2Name ${getString(R.string.player_wins)} $playAgainString"
        }
        mButtonNext!!.text = text
        if (HUMAN_VS_NETWORK) {
            updateWebServerScore()
        }
        highlightCurrentPlayer(GameView.State.EMPTY)
        mGameView!!.setViewDisabled(true)
    }

    private fun playSound(soundToPlay: Int) {
        if (!soundMode) {
            return
        }

        val soundPlayed = MediaPlayer.create(applicationContext, soundToPlay)
        if (soundPlayed != null) {
            soundPlayed.setOnCompletionListener { mp -> mp.release() }
            soundPlayed.start()
        }
    }

    private fun editScore(score: Int): String {
        val formatter = DecimalFormat("0000")
        val formatScore = StringBuilder(formatter.format(score.toLong()))
        for (x in 0 until formatScore.length) {
            val testString = formatScore.substring(x, x + 1)
            if (testString == "0") {
                formatScore.replace(x, x + 1, " ")
            } else {
                break
            }
        }
        return formatScore.toString()
    }

    private fun displayScores() {
        if (HUMAN_VS_NETWORK) {
            mPlayer1ScoreTextValue!!.text = editScore(mPlayer1NetworkScore)
            mPlayer2ScoreTextValue!!.text = editScore(mPlayer2NetworkScore)
        } else if (HUMAN_VS_HUMAN) {
            mPlayer1ScoreTextValue!!.text = editScore(mPlayer1Score)
            mPlayer2ScoreTextValue!!.text = editScore(mPlayer2Score)
        } else {
            mPlayer1ScoreTextValue!!.text = editScore(mPlayer1Score)
            mPlayer2ScoreTextValue!!.text = editScore(mWillyScore)
        }
        mPlayer1NameTextValue!!.setText(mPlayer1Name)
        mPlayer2NameTextValue!!.setText(mPlayer2Name)
    }

    private fun showPlayerTokenChoice() {
        if (mPlayer1TokenChoice == GameView.BoardSpaceValues.CROSS) {
            val mBmpCrossPlayer1 = GameView.mBmpCrossPlayer1
            mGameTokenPlayer1!!.setImageBitmap(mBmpCrossPlayer1)
        } else if (mPlayer1TokenChoice == GameView.BoardSpaceValues.CIRCLE) {
            val mBmpCirclePlayer1 = GameView.mBmpCirclePlayer1
            mGameTokenPlayer1!!.setImageBitmap(mBmpCirclePlayer1)
        } else {
            mGameTokenPlayer1!!.setImageResource(R.drawable.reset_token_selection)
        }
        if (mPlayer2TokenChoice == GameView.BoardSpaceValues.CROSS) {
            val mBmpCrossPlayer2 = GameView.mBmpCrossPlayer2
            mGameTokenPlayer2!!.setImageBitmap(mBmpCrossPlayer2)
        } else if (mPlayer2TokenChoice == GameView.BoardSpaceValues.CIRCLE) {
            val mBmpCirclePlayer2 = GameView.mBmpCirclePlayer2
            mGameTokenPlayer2!!.setImageBitmap(mBmpCirclePlayer2)
        } else {
            mGameTokenPlayer2!!.setImageResource(R.drawable.reset_token_selection)
        }
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("ga_player1_token_choice", mPlayer1TokenChoice)
        savedInstanceState.putInt("ga_player2_token_choice", mPlayer2TokenChoice)
        savedInstanceState.putInt("ga_player1_score", mPlayer1Score)
        savedInstanceState.putInt("ga_player2_score", mPlayer2Score)
        savedInstanceState.putInt("ga_willy_score", mWillyScore)
        savedInstanceState.putString("ga_button", mButtonNext!!.text.toString())
        savedInstanceState.putBoolean("ga_move_mode", moveModeTouch)
        savedInstanceState.putBoolean("ga_sound_mode", soundMode)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.
        mPlayer1TokenChoice = savedInstanceState.getInt("ga_player1_token_choice")
        mPlayer2TokenChoice = savedInstanceState.getInt("ga_player2_token_choice")
        mPlayer1Score = savedInstanceState.getInt("ga_player1_score")
        mPlayer2Score = savedInstanceState.getInt("ga_player2_score")
        mWillyScore = savedInstanceState.getInt("ga_willy_score")
        val workString = savedInstanceState.getString("ga_button")
        mButtonNext!!.text = workString
        val playAgainString = getString(R.string.playAgain)
        if (!mButtonNext!!.text.toString().contains(playAgainString)) {
            mButtonNext!!.isEnabled = false
        }
        moveModeTouch = savedInstanceState.getBoolean("ga_move_mode")
        soundMode = savedInstanceState.getBoolean("ga_sound_mode")
    }

    override fun onStop() {
        super.onStop()
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putInt(PLAYER1_SCORE, mPlayer1Score)
        editor.putInt(PLAYER2_SCORE, mPlayer2Score)
        editor.putInt(WILLY_SCORE, mWillyScore)
        editor.apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        writeToLog("GameActivity", "GameActivity onDestroy() called - client thread: $mClientThread server thread: $mServerThread")
        if (mClientWaitDialog != null)
            mClientWaitDialog!!.dismiss()
        if (mLikeToPlayDialog != null)
            mLikeToPlayDialog!!.dismiss()
        if (mServerRefusedGame != null)
            mServerRefusedGame!!.dismiss()
        if (mHostWaitDialog != null)
            mHostWaitDialog!!.dismiss()
        if (mOpponentLeftGameAlert != null)
            mOpponentLeftGameAlert!!.dismiss()
        if (mWinByTimeoutAlert != null)
            mWinByTimeoutAlert!!.dismiss()
        if (mForfeitGameDialog != null)
            mForfeitGameDialog!!.dismiss()
        stopMoveWaitingTimerThread()
        writeToLog("GameActivity", "=====================GameActivity onDestroy() all done")
    }

    private fun checkDistanceToOtherPlayer() {
        writeToLog("GameActivity", "checkDistanceToOtherPlayer userId $mPlayer2Id, userName: $mPlayer2Name")
        val urlData = "/gamePlayer/getDistancBetweenPlayers/?player1=${mPlayer1Id}&player2=$mPlayer2Id"
        var messageResponse: String? = null
        runBlocking {
            val job = CoroutineScope(Dispatchers.IO).launch {
                messageResponse = converseWithAppServer(urlData, false)
            }
            job.join()
        }
        writeToLog("GameActivity", "checkDistanceToOtherPlayer response: $messageResponse")
        val jsonString = messageResponse.toString()
        val jsonObject = JSONTokener(jsonString).nextValue() as JSONObject
        val distanceToOpponent = jsonObject.getString("Distance")
        writeToLog("GameActivity", "checkDistanceToOtherPlayer distance: $distanceToOpponent")
        val distance = distanceToOpponent.toDoubleOrNull()
        val minDistanceToOpponent = if (getConfigMap("MinDistanceToOpponent")?.toDoubleOrNull() == null) 0.01
            else getConfigMap("MinDistanceToOpponent")?.toDouble()
        if (distance != null && distance < minDistanceToOpponent!!) { //15000.00) { for testing with emulator only since location is set to 0 longitude and 0 latitutude
            WillyShmoApplication.playersTooClose = true
            sendToastMessage(getString(R.string.too_close, mPlayer2Name))
        }
    }

    private inner class ServerThread: Thread() {
        val rabbitMQConnection = setUpRabbitMQConnection()
        var mMessageToClient: String? = null
        var boardPosition = 0
            private set
        var tokenMoved = 0
            private set

        fun setMessageToClient(newMessage: String?) {
            mMessageToClient = newMessage
        }

        private fun parseLine(line: String) {
            val moveValues: Array<String?> = line.split(",".toRegex()).toTypedArray()
            if (moveValues[1] != null) {
                tokenMoved = moveValues[1]!!.trim { it <= ' ' }.toInt()
            }
            if (moveValues[2] != null) {
                boardPosition = moveValues[2]!!.trim { it <= ' ' }.toInt()
            }
            if (moveValues[3] != null) {
                val player1TokenChoice = moveValues[3]!!.trim { it <= ' ' }.toInt()
                if (player1TokenChoice > -1) {
                    mPlayer2TokenChoice = player1TokenChoice
                    mPlayer1TokenChoice = if (mPlayer2TokenChoice == GameView.BoardSpaceValues.CIRCLE) GameView.BoardSpaceValues.CROSS else GameView.BoardSpaceValues.CIRCLE
                    mHandler.sendEmptyMessage(MSG_NETWORK_SET_TOKEN_CHOICE)
                }
            }
        }

        private fun setUpRabbitMQConnection(): RabbitMQConnection {
            val qName = "$mQueuePrefix-client-$mPlayer2Id"
            return runBlocking {
                CoroutineScope(Dispatchers.Default).async {
                    SetUpRabbitMQConnection().main(
                        qName,
                        this@GameActivity as ToastMessage,
                        Companion.resources
                    )
                }.await()
            }
        }

        fun closeRabbitMQConnection(rabbitMQConnection: RabbitMQConnection) {
            writeToLog("ServerThread", "about to stop RabbitMQ server side consume thread")
            val messageToSelf = "finishConsuming,${mPlayer1Name},${mPlayer1Id}"
            val myQName = getConfigMap("RabbitMQQueuePrefix") + "-" + "server" + "-" + mPlayer1Id
            runBlocking {
                CoroutineScope(Dispatchers.Default).async {
                    SendMessageToRabbitMQ().main(
                        rabbitMQConnection,
                        myQName,
                        messageToSelf,
                        this@GameActivity as ToastMessage,
                        Companion.resources
                    )
                }.await()
            }

            writeToLog("ServerThread", "about to close server side RabbitMQ connection")
            runBlocking {
                CoroutineScope(Dispatchers.Default).async {
                    CloseRabbitMQConnection().main(
                        rabbitMQConnection,
                        this@GameActivity as ToastMessage,
                        Companion.resources
                    )
                }.await()
            }
        }

        override fun run() {
            try {
                writeToLog("ServerThread", "server run method started")
                mPlayer2NetworkScore = 0
                mPlayer1NetworkScore = mPlayer2NetworkScore
                mGameStarted = false
                while (isServerRunning) {
                    if (mRabbitMQServerResponse != null) {
                        writeToLog("ServerThread", "Retrieving command: $mRabbitMQServerResponse")
                        if (mRabbitMQServerResponse!!.contains("tokenList")) {
                            getGameSetUpFromClient(mRabbitMQServerResponse!!)
                            checkDistanceToOtherPlayer()
                            mHandler.sendEmptyMessage(DISMISS_WAIT_FOR_NEW_GAME_FROM_CLIENT)
                            mHandler.sendEmptyMessage(ACCEPT_INCOMING_GAME_REQUEST_FROM_CLIENT)
                            mGameStarted = true
                            //TODO - let them play but turn off prizes
                            writeToLog("ServerThread", "got a tokenList back from opponent")
                        }
                        if (mRabbitMQServerResponse!!.startsWith("moved")) {
                            parseLine(mRabbitMQServerResponse!!)
                            mHandler.sendEmptyMessage(MSG_NETWORK_SERVER_TURN)
                            writeToLog("ServerThread", "got a moved message back from opponent")
                            stopMoveWaitingTimerThread()
                            startMoveWaitingTimerThread("server")
                        }
                        if (mRabbitMQServerResponse!!.startsWith("leftGame")) {
                            playerNotPlaying("client", mRabbitMQServerResponse!!, 1)
                        }
                        if (mRabbitMQServerResponse!!.startsWith("noPlay")) {
                            playerNotPlaying("client", mRabbitMQServerResponse!!, 1)
                        }
                        if (mRabbitMQServerResponse!!.startsWith("timedOutLoss")) {
                            writeToLog("ServerThread", "server - opponent has timed out")
                            timedOutWin()
                            isServerRunning = false
                        }
                        mRabbitMQServerResponse = null
                    }
                    if (mMessageToClient != null) {
                        stopMoveWaitingTimerThread()
                        val messageToBeSent = mMessageToClient //circumvent sending a null message to sendMessageToRabbitMQTask
                        writeToLog("ServerThread", "Server about to respond to client: $messageToBeSent")
                        val qName = "$mQueuePrefix-client-$mPlayer2Id"
                        runBlocking {
                            CoroutineScope(Dispatchers.Default).async {
                                SendMessageToRabbitMQ().main(
                                    rabbitMQConnection,
                                    qName,
                                    messageToBeSent!!,
                                    this@GameActivity as ToastMessage,
                                    Companion.resources
                                )
                            }.await()
                        }
                        writeToLog("ServerThread", "Server responded to client completed, queue: $qName, message: $messageToBeSent")
                        if (messageToBeSent!!.startsWith("leftGame") || messageToBeSent.startsWith("noPlay")) {
                            isServerRunning = false
                            mServerIsPlayingNow = false
                        }
                        mMessageToClient = null
                    }
                    sleep(THREAD_SLEEP_INTERVAL.toLong())
                } // while end
                mPlayer2NetworkScore = 0
                mPlayer1NetworkScore = mPlayer2NetworkScore
                mServerIsPlayingNow = false
                writeToLog("ServerThread", "server run method finished")
            } catch (e: Exception) {
                writeToLog("ServerThread", "error in Server Thread: " + e.message)
                sendToastMessage(e.message)
            } finally {
                stopMoveWaitingTimerThread()
                mServerThread!!.closeRabbitMQConnection(mServerThread!!.rabbitMQConnection)
                writeToLog("GameActivity", "about to call serverThread DisposeRabbitMQTask()")
                runBlocking {
                    withContext(CoroutineScope(Dispatchers.Default).coroutineContext) {
                        val disposeRabbitMQTask = DisposeRabbitMQTask()
                        disposeRabbitMQTask.main(
                            mMessageServerConsumer,
                            resources,
                            this@GameActivity as ToastMessage
                        )
                    }
                }
                isServerRunning = false
                mServerIsPlayingNow = false
                mPlayer2NetworkScore = 0
                mPlayer1NetworkScore = mPlayer2NetworkScore
                writeToLog("ServerThread", "server run method finally done")
            }
        }
    }

    fun timedOutWin() { // on client side sent from server
        writeToLog("GameActivity", "opponent has timed out and forfeited game, so I win!")
        mPlayer1NetworkScore += mRegularWin
        mHandler.sendEmptyMessage(MSG_OPPONENT_TIMED_OUT)
        //displayScores() - ensure scores are updated and persisted to database
    }

    fun playerNotPlaying(clientOrServer: String, line: String, reason: Int) {
        stopMoveWaitingTimerThread()
        val playerName: Array<String?> = line.split(",".toRegex()).toTypedArray()
        if (playerName[1] != null) {
            mNetworkOpponentPlayerName = playerName[1]
        }
        if (clientOrServer.startsWith("server")) {
            when (reason) {
                0 -> mHandler.sendEmptyMessage(MSG_NETWORK_SERVER_REFUSED_GAME)
                1 -> mHandler.sendEmptyMessage(MSG_NETWORK_SERVER_LEFT_GAME)
            }
        } else {
            when (reason) {
                0 -> mHandler.sendEmptyMessage(MSG_NETWORK_CLIENT_REFUSED_GAME)
                1 -> mHandler.sendEmptyMessage(MSG_NETWORK_CLIENT_LEFT_GAME)
            }
        }
    }

    inner class ClientThread: Thread() {
        val rabbitMQConnection = setUpRabbitMQConnection()
        private var mMessageToServer: String? = null
        var boardPosition = 0
        var tokenMoved = 0
        val player1Id: String
            get() = mPlayer1Id.toString()
        val player1Name: String?
            get() = mPlayer1Name
        fun setMessageToServer(newMessage: String?) {
            mMessageToServer = newMessage
        }

        private fun parseMove(line: String) {
            val moveValues: Array<String?> = line.split(",".toRegex()).toTypedArray()
            if (moveValues[1] != null) tokenMoved = moveValues[1]!!.trim { it <= ' ' }.toInt()
            if (moveValues[2] != null) boardPosition = moveValues[2]!!.trim { it <= ' ' }.toInt()
            if (moveValues[3] != null) {
                val player1TokenChoice = moveValues[3]!!.trim { it <= ' ' }.toInt()
                if (player1TokenChoice > -1) {
                    mPlayer2TokenChoice = player1TokenChoice
                    mPlayer1TokenChoice = if (mPlayer2TokenChoice == GameView.BoardSpaceValues.CIRCLE) GameView.BoardSpaceValues.CROSS else GameView.BoardSpaceValues.CIRCLE
                    mHandler.sendEmptyMessage(MSG_NETWORK_SET_TOKEN_CHOICE)
                }
            }
        }

        //FIXME - consolidate client side and server side methods with a single shared method
        private fun setUpRabbitMQConnection(): RabbitMQConnection {
            val qName = "$mQueuePrefix-server-$mPlayer2Id"
            return runBlocking {
                CoroutineScope(Dispatchers.Default).async {
                    SetUpRabbitMQConnection().main(
                        qName,
                        this@GameActivity as ToastMessage,
                        Companion.resources
                    )
                }.await()
            }
        }

        //FIXME - consolidate client side and server side methods with a single shared method
        fun closeRabbitMQConnection(mRabbitMQConnection: RabbitMQConnection) {
            writeToLog("ClientThread", "about to stop RabbitMQ client side consume thread")
            val messageToSelf = "finishConsuming,${mPlayer1Name},${mPlayer1Id}"
            val myQName = getConfigMap("RabbitMQQueuePrefix") + "-" + "client" + "-" + mPlayer1Id
            runBlocking {
                CoroutineScope(Dispatchers.Default).async {
                    SendMessageToRabbitMQ().main(
                        rabbitMQConnection,
                        myQName,
                        messageToSelf,
                        this@GameActivity as ToastMessage,
                        Companion.resources
                    )
                }.await()
            }
            writeToLog("ClientThread", "about to close client side RabbitMQ connection")
            runBlocking {
                CoroutineScope(Dispatchers.Default).async {
                    CloseRabbitMQConnection().main(
                        mRabbitMQConnection,
                        this@GameActivity as ToastMessage,
                        Companion.resources
                    )
                }.await()
            }
        }

        override fun run() {
            try {
                writeToLog("ClientThread", "client run method started")
                while (mClientRunning) {
                    if (mMessageToServer != null) {
                        val messageToBeSent = mMessageToServer //circumvent sending a null message to sendMessageToRabbitMQTask
                        val qName = "$mQueuePrefix-server-$mPlayer2Id"
                        runBlocking {
                            CoroutineScope(Dispatchers.Default).async {
                                SendMessageToRabbitMQ().main(
                                    rabbitMQConnection,
                                    qName,
                                    messageToBeSent!!,
                                    this@GameActivity as ToastMessage,
                                    Companion.resources
                                )
                            }.await()
                        }
                        writeToLog("ClientThread", "Sending command: $messageToBeSent queue: $qName")
                        if (messageToBeSent!!.startsWith("leftGame")) {
                            mClientRunning = false
                        }
                        mMessageToServer = null
                    }
                    if (mRabbitMQClientResponse != null) {
                        writeToLog("ClientThread", "read response: $mRabbitMQClientResponse")
                        if (mClientWaitDialog != null ) {
                            mHandler.sendEmptyMessage(DISMISS_WAIT_FOR_NEW_GAME_FROM_SERVER)
                        }
                        if (mRabbitMQClientResponse!!.startsWith("serverAccepted")) {
                            mGameStarted = true
                        }
                        if (mRabbitMQClientResponse!!.startsWith("moved")) {
                            parseMove(mRabbitMQClientResponse!!)
                            mHandler.sendEmptyMessage(MSG_NETWORK_CLIENT_TURN)
                            writeToLog("ClientThread", "got a moved response from opponent")
                            stopMoveWaitingTimerThread()
                            startMoveWaitingTimerThread("client")
                        }
                        if (mRabbitMQClientResponse!!.startsWith("moveFirst")) {
                            mGameStarted = true
                            mHandler.sendEmptyMessage(MSG_NETWORK_CLIENT_MAKE_FIRST_MOVE)
                            writeToLog("ClientThread", "got a moveFirst message from opponent")
                            stopMoveWaitingTimerThread()
                            startMoveWaitingTimerThread("client")
                        }
                        if (mRabbitMQClientResponse!!.startsWith("noPlay")) {
                            playerNotPlaying("server", mRabbitMQClientResponse!!, 0)
                            mClientRunning = false
                        }
                        if (mRabbitMQClientResponse!!.startsWith("leftGame")) {
                            playerNotPlaying("server", mRabbitMQClientResponse!!, 1)
                            mClientRunning = false
                        }
                        if (mRabbitMQClientResponse!!.startsWith("timedOutLoss")) {
                            writeToLog("ClientThread", "client - opponent has timed out")
                            timedOutWin()
                            mClientRunning = false
                        }
                        mRabbitMQClientResponse = null
                    }
                    sleep(THREAD_SLEEP_INTERVAL.toLong())
                } // while end
                writeToLog("ClientThread", "client run method finished")
            } catch (e: Exception) {
                writeToLog("ClientThread", "error in Client Thread: "+e.message)
                sendToastMessage(e.message)
            } finally {
                stopMoveWaitingTimerThread()
                mClientThread!!.closeRabbitMQConnection(mClientThread!!.rabbitMQConnection)
                writeToLog("GameActivity", " onDestroy about to call clientThread DisposeRabbitMQTask()")
                runBlocking {
                    CoroutineScope(Dispatchers.Default).async {
                        val disposeRabbitMQTask = DisposeRabbitMQTask()
                        disposeRabbitMQTask.main(
                            mMessageClientConsumer,
                            resources,
                            this@GameActivity as ToastMessage
                        )
                    }.await()
                }
                val urlData = "/gamePlayer/update/?id=$mPlayer1Id&playingNow=false&onlineNow=false&opponentId=0"
                val messageResponse = sendMessageToAppServer(urlData,false)
                mPlayer2NetworkScore = 0
                mPlayer1NetworkScore = mPlayer2NetworkScore
                mClientRunning = false
                writeToLog("ClientThread", "client run method finally done, messageResponse: $messageResponse")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        writeToLog("GameActivity", "+++++++++++++++++++++> onPause called, mClientRunning: $mClientRunning, serverIsPlayingNow: $mServerIsPlayingNow, isServerRunning: $isServerRunning")
        val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val rnds = (0..1000000).random() // generated random from 0 to 1,000,000 inclusive

        if (mClientRunning) {
            mClientThread!!.setMessageToServer("leftGame, $mPlayer1Name $dateTime $rnds")
            setNetworkGameStatusAndResponse(false, false)
        }
        if (mServerIsPlayingNow) {
            mServerThread!!.setMessageToClient("leftGame, $mPlayer1Name $dateTime $rnds")
            setNetworkGameStatusAndResponse(false, false)
        } else if (isServerRunning) {
            isServerRunning = false
            setNetworkGameStatusAndResponse(false, false)
        }
    }

    private fun sendMessageToAppServer(urlData: String, finishActivity: Boolean): String? {
        var returnMessage: String? = null
        runBlocking {
            val job = CoroutineScope(Dispatchers.IO).launch {
                returnMessage = converseWithAppServer(urlData, finishActivity)
                //writeToLog("GameActivity", "sendMessageToAppServer returnMessage: $returnMessage")
            }
            job.join()
        }
        writeToLog("GameActivity", "sendMessageToAppServer after job join() returnMessage: $returnMessage")
        return returnMessage
    }

    private fun converseWithAppServer(urlData: String, finishActivity: Boolean): String {
        return SendMessageToAppServer.main(
            urlData,
            mGameActivity as ToastMessage,
            resources,
            finishActivity
        )
    }

    private fun getGameSetUpFromClient(gameSetUp: String) {
        try {
            mPlayer1TokenChoice = GameView.BoardSpaceValues.EMPTY
            mPlayer2TokenChoice = GameView.BoardSpaceValues.EMPTY // computer or opponent
            mTokensFromClient = ArrayList()
            val jsonObject = JSONObject(gameSetUp)
            val tokenArray = jsonObject.getJSONArray("tokenList")
            for (y in 0 until tokenArray.length()) {
                val tokenValues = tokenArray.getJSONObject(y)
                val tokenId = tokenValues.getString("tokenId")
                val tokenType = tokenValues.getString("tokenType")
                val tokenIntValue = tokenId.toInt()
                val tokenIntType = tokenType.toInt()
                if (tokenIntValue < 8) {
                    val resultValue = if (tokenIntValue > 3) {
                        tokenIntValue - 4
                    } else {
                        tokenIntValue + 4
                    }
                    (mTokensFromClient as ArrayList<IntArray>).add(intArrayOf(resultValue, tokenIntType))
                } else {
                    (mTokensFromClient as ArrayList<IntArray>).add(intArrayOf(GameView.BoardSpaceValues.BOARDCENTER, tokenIntType))
                }
            }
            mPlayer2Name = jsonObject.getString("player1Name")
            mPlayer2Id = jsonObject.getString("player1Id")
        } catch (e: JSONException) {
            sendToastMessage(e.message)
        }
    }

    private fun setNetworkGameStatusAndResponse(start: Boolean, sendNoPlay: Boolean) {
        mServerHasOpponent = null
        var urlData = "/gamePlayer/update/?playingNow=true&id=$mPlayer1Id&opponentId=$mPlayer2Id"
        if (start) { // This is set from the Server side
            mHandler.sendEmptyMessage(NEW_GAME_FROM_CLIENT)
            mServerIsPlayingNow = true
            mServerThread!!.setMessageToClient("serverAccepted")
            val messageResponse = sendMessageToAppServer(urlData, !start)
            writeToLog("GameActivity", "setNetworkGameStatusAndResponse start guy messageResponse: $messageResponse")
            //TODO - Server side - checkDistanceToOpponent
        } else {
            writeToLog("GameActivity", "setNetworkGameStatusAndResponse sendNoPlay: $sendNoPlay")
            urlData = "/gamePlayer/update/?id=$mPlayer1Id&playingNow=false&onlineNow=false&opponentId=0"
            mPlayer2NetworkScore = 0
            mPlayer1NetworkScore = mPlayer2NetworkScore
            //mPlayer2Name = null
            mServerIsPlayingNow = false
            if (mServerThread != null) {
                writeToLog("GameActivity", "setNetworkGameStatusAndResponse server thread is running")
                if (sendNoPlay) {
                    mServerThread!!.setMessageToClient("noPlay, $mPlayer1Name")
                }
            }
            val messageResponse = sendMessageToAppServer(urlData, !start)
            writeToLog("GameActivity", "setNetworkGameStatusAndResponse finish guy messageResponse: $messageResponse")
            finish()
        }
    }

    private fun startMoveWaitingTimerThread(clientOrServer: String) {
        mMoveWaitingProgress = 100
        mMoveWaitingTimerHandlerThread = HandlerThread("moveWaitingTimerHandlerThread")
        mMoveWaitingTimerHandlerThread!!.start()
        looperForMoveWaiting = mMoveWaitingTimerHandlerThread!!.looper
        if (mWaitingForMove) {
            mWaitingForMove = false
            mMoveWaitProgressThread = null
        }
        mWaitingForMove = true
        mMoveWaitProgressThread = MoveWaitProgressThread(clientOrServer)
        mMoveWaitProgressThread!!.start()
        looperForMoveWaitingHandler = object : Handler(looperForMoveWaiting!!) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MOVE_WAITING -> { setWaitingForMoveProgressBar(mMoveWaitingProgress) }
                    else -> { }
                }
            }
        }
    }

    private fun stopMoveWaitingTimerThread() {
        setWaitingForMoveProgressBar(0)
        mWaitingForMove = false
    }

    private fun setWaitingForMoveProgressBar(progress: Int) {
        mMoveWaitingTimerProgress?.setProgress(progress)
        writeToLog("GameActivity", "Move waiting progress bar set to: $progress")
    }

    inner class MoveWaitProgressThread(val clientOrServer: String): Thread() { // thread is used to change the progress value
        var timedOut = false
        private val moveWaitTimeSeconds = if (getConfigMap("MoveWaitTimeSeconds")?.toIntOrNull() == null) 10
            else getConfigMap("MoveWaitTimeSeconds")?.toInt()

        override fun run() {
            try {
                while (mWaitingForMove) {
                    sleep(1000)
                    val msg = looperForMoveWaitingHandler!!.obtainMessage(MOVE_WAITING)
                    looperForMoveWaitingHandler!!.sendMessage(msg)
                    if (mMoveWaitingProgress <= 0) {
                        mWaitingForMove = false
                        timedOut = true
                    }
                    mMoveWaitingProgress -= 100 / moveWaitTimeSeconds!!.toInt()
                }
                mMoveWaitingProgress = 0
                setWaitingForMoveProgressBar(mMoveWaitingProgress)
            } catch (e: Exception) {
                writeToLog("GameActivity", "setMoveWaitProgressThread exception: $e")
            } finally {
                if (timedOut) {
                    if ("server".equals(clientOrServer)) {
                        mHandler.sendEmptyMessage(PLAYER_TIMED_OUT_SERVER)
                    } else {
                        mHandler.sendEmptyMessage(PLAYER_TIMED_OUT_CLIENT)
                    }
                }
                mMoveWaitingProgress = 0
                writeToLog("GameActivity", "setMoveWaitProgressThread all done")
            }
        }
    }

    private fun createForfeitGameDialog(clientOrServer: String): AlertDialog {
        writeToLog("GameActivity", "forfeitGame")
        timedOutLoss(clientOrServer)
        if (mForfeitGameDialog != null) {
            mForfeitGameDialog!!.dismiss()
            mForfeitGameDialog = null
        }
        return AlertDialog.Builder(this@GameActivity)
            .setTitle(getString(R.string.moved_too_slow))
            .setPositiveButton(getString(R.string.play_again_string)) { _, _ -> finish() } //startTwoPlayerActivity() }
            .setMessage(getString(R.string.timed_out_loss) + " $mPlayer2Name!")
            .setCancelable(false)
            .setIcon(R.drawable.willy_shmo_small_icon)
            //.setNegativeButton(getString(R.string.quit_button)) { _, _ -> finish() }
            .create()
    }

    private fun playItAgainSam() {
        if (mServerIsPlayingNow) {
            mHostWaitDialog = createHostWaitDialog()
            mHostWaitDialog!!.show()
        } else if (mClientRunning) { //reset values on client side
            sendNewGameToServer()
        } else {
            finish()
        }
    }

    private fun timedOutLoss(clientOrServer: String) {
        writeToLog("GameActivity", "timedOutLoss() method entered")
        //TODO - need to figure out how to award 10 points to the opponent use mRegularWin
        if ("server".equals(clientOrServer)) {
            mServerThread!!.setMessageToClient("timedOutLoss")
        } else {
            mClientThread!!.setMessageToServer("timedOutLoss")
        }
    }

    private val newNetworkGameHandler = object: Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            writeToLog("newNetworkGameHandler", "msg.what: $msg.what " + msg.what)
            when (msg.what) {
                ACCEPT_GAME -> setNetworkGameStatusAndResponse(true, false)
                REJECT_GAME -> setNetworkGameStatusAndResponse(false, true)
            }
        }
    }

    /*
    private val newNetworkGameHandler2 = Handler(Looper.getMainLooper()) { msg -> // Your code logic goes here.
        when (msg.what) {
            ACCEPT_GAME -> setNetworkGameStatusAndResponse(true, false)
            REJECT_GAME -> setNetworkGameStatusAndResponse(false, true)
        }
        true
    }
    */

    constructor(parcel: Parcel) : this() {
        mServer = parcel.readByte() != 0.toByte()
        mClient = parcel.readByte() != 0.toByte()
        mPlayer1TokenChoice = parcel.readInt()
        mPlayer2TokenChoice = parcel.readInt()
        mRabbitMQClientResponse = parcel.readString()
        mRabbitMQServerResponse = parcel.readString()
        mRabbitMQStartGameResponse = parcel.readString()
        mServerHasOpponent = parcel.readString()
    }

    private fun acceptIncomingGameRequestFromClient() {
        writeToLog("GameActivity", "at start of acceptIncomingGameRequestFromClient()")

        //doesn't work here:
        //val item = intent.getParcelableExtra<ParcelItems>(PARCELABLE_VALUES)
        //writeToLog("GameActivity", "our parcelable extra item: $item")

        val acceptMsg = Message.obtain()
        acceptMsg.target = newNetworkGameHandler
        acceptMsg.what = ACCEPT_GAME
        val rejectMsg = Message.obtain()
        rejectMsg.target = newNetworkGameHandler
        rejectMsg.what = REJECT_GAME

        if (!mServerIsPlayingNow) { //see if this gets rid of error when getting a tokenList after we've gotten a leftGame message
            writeToLog("GameActivity", "serverIsPlayingNow is false in acceptIncomingGameRequestFromClient(), not gonna return this time")
            // return
        }
        if (mServerThread == null) { //see if this gets rid of error when getting a tokenList after we've gotten a leftGame message
            writeToLog("GameActivity", "mServerThread is null in acceptIncomingGameRequestFromClient(), gonna return...")
            return
        }

        try {
            if (!isServerRunning) { //see if this gets rid of error when getting a tokenList after we've gotten a leftGame message
                writeToLog("GameActivity", "isServerRunning is false in acceptIncomingGameRequestFromClient(), gonna return...")
                return
            }
            mLikeToPlayDialog = AlertDialog.Builder(this@GameActivity)
                .setTitle("$mPlayer2Name would like to play")
                .setPositiveButton("Accept") { _, _ -> acceptMsg.sendToTarget() }
                .setCancelable(false)
                .setIcon(R.drawable.willy_shmo_small_icon)
                .setNegativeButton("Reject") { _, _ -> rejectPlayRequest() }
                .show()
        } catch (e: Exception) {
            writeToLog("GameActivity", "acceptIncomingGameRequestFromClient() catch exception isServerRunning: $isServerRunning")
            writeToLog("GameActivity", "acceptIncomingGameRequestFromClient() catch exception serverIsPlayingNow: $mServerIsPlayingNow")
            writeToLog("GameActivity", "acceptIncomingGameRequestFromClient() catch exception mServerThread: $mServerThread")
            sendToastMessage(e.message)
        }
    }

    private fun rejectPlayRequest() {
        stopMoveWaitingTimerThread()
    }

    private fun displayServerRefusedGameAlert(playerName: String?) {
        if (!mGameStarted) {
            //val i = Intent(this, PlayOverNetwork::class.java)
            //i.putExtra(PLAYER1_NAME, mPlayer1Name)
            //startActivity(i)
            //return
            writeToLog("GameActivity", "displayServerRefusedGameAlert mGameStarted: $mGameStarted")
        }
        try {
            mServerRefusedGame = AlertDialog.Builder(this@GameActivity)
                .setIcon(R.drawable.willy_shmo_small_icon)
                //.setTitle("Sorry, $playerName server side has left the game")
                .setTitle(getString(R.string.player_sorry) + " $playerName " + getString(R.string.server_side) + " " + getString(R.string.has_left_game))
                .setPositiveButton(R.string.ok) { _, _ -> startTwoPlayerActivity() }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            sendToastMessage(e.message)
        }
    }

    private fun startTwoPlayerActivity() {
        writeToLog("GameActivity", "startTwoPlayerActivity() called, mGameStarted: $mGameStarted")
        val i = Intent(this, TwoPlayerActivity::class.java)
        i.putExtra(PLAYER1_NAME, mPlayer1Name)
        i.putExtra(PLAYER2_NAME, mPlayer2Name)
        startActivity(i)
        finish() //causes onPause to be invoked
    }

    private fun displayOpponentLeftGameAlert(clientOrServer: String, playerName: String?): AlertDialog {
        if (mOpponentLeftGameAlert != null) {
            mOpponentLeftGameAlert!!.dismiss()
        }
        return AlertDialog.Builder(this@GameActivity)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(getString(R.string.player_sorry) + " " + playerName + " " + clientOrServer + " " + getString(R.string.has_left_game))
            .setPositiveButton(R.string.ok) { _, _ -> startTwoPlayerActivity() }
            .setCancelable(false)
            .show()
    }

    private fun displayWinByTimeoutAlert(playerName: String?): AlertDialog {
        if (mWinByTimeoutAlert!= null) {
            mWinByTimeoutAlert!!.dismiss()
        }
        return AlertDialog.Builder(this@GameActivity)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(playerName + " " + getString(R.string.timed_out_win))
            .setMessage(getString(R.string.timed_out_win_2))
            .setPositiveButton(R.string.ok) { _, _ -> startTwoPlayerActivity() }
            .setCancelable(false)
            .show()
    }

    private fun updateWebServerScore() {
        val urlData = "/gamePlayer/updateGamesPlayed/?id=$mPlayer1Id&score=$mPlayer1NetworkScore"
        val messageResponse = sendMessageToAppServer(urlData,false)
        writeToLog("GameActivity", "updateWebServerScore() messageResponse: $messageResponse")
    }

    class ErrorHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(mApplicationContext, msg.obj as String, Toast.LENGTH_LONG).show()
        }
    }

    override fun sendToastMessage(message: String?) {
        writeToLog("GameActivity", "sendToastMessage message: $message")
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    fun sendMessageToServerHost(message: String) {
        if (mClientRunning) {
            mClientThread!!.setMessageToServer(message)
        }
        //writeToLog("GameActivity", "sendMessageToServerHost: $message")
    }

    // to update the game count:
    // http://ww2.guzzardo.com:8081/WillyShmoGrails/gamePlayer/updateGamesPlayed/?id=1
    private fun showPrizeWon(prizeType: Int) {
        try {
            AlertDialog.Builder(this@GameActivity)
                .setTitle(getString(R.string.won_a_prize))
                .setPositiveButton(R.string.alert_dialog_accept) { _, _ ->
                    val i = Intent(this@GameActivity, PrizesAvailableActivity::class.java)
                    startActivity(i)
                }
                .setCancelable(true)
                .setIcon(R.drawable.willy_shmo_small_icon)
                .setNegativeButton(R.string.alert_dialog_reject) { _, _ -> }
                .show()
        } catch (e: Exception) {
            sendToastMessage(getString(R.string.prize_won_error) + " " + e.message)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeInt(0)
    }

    @Parcelize
    data class ParcelItems(val imageId: Int, val title: String): Parcelable

    companion object {
        private var mApplicationContext: Context? = null
        private var mGameActivity: GameActivity? = null
        var errorHandler: ErrorHandler? = null
        //TODO - I think all of these constants can be put into an interface?
        private const val packageName = "com.guzzardotictacdoh2"
        /* Start player. Must be 1 or 2. Default is 1.  */
        const val EXTRA_START_PLAYER = "$packageName.GameActivity.EXTRA_START_PLAYER"
        const val START_PLAYER_HUMAN = "$packageName.GameActivity.START_PLAYER_HUMAN"
        const val PLAYER1_NAME = "$packageName.GameActivity.PLAYER1_NAME"
        const val PLAYER2_NAME = "$packageName.GameActivity.PLAYER2_NAME"
        const val PLAY_AGAINST_WILLY = "$packageName.GameActivity.PLAY_AGAINST_WILLY"
        const val PLAYER1_SCORE = "$packageName.GameActivity.PLAYER1_SCORE"
        const val PLAYER2_SCORE = "$packageName.GameActivity.PLAYER2_SCORE"
        const val WILLY_SCORE = "$packageName.GameActivity.WILLY_SCORE"
        const val START_SERVER = "$packageName.GameActivity.START_SERVER"
        const val START_CLIENT = "$packageName.GameActivity.START_CLIENT"
        const val START_CLIENT_OPPONENT_ID = "$packageName.GameActivity.START_CLIENT_OPPONENT_ID"
        const val PLAYER1_ID = "$packageName.GameActivity.PLAYER1_ID"
        const val MOVE_MODE = "$packageName.GameActivity.MOVE_MODE"
        const val SOUND_MODE = "$packageName.GameActivity.SOUND_MODE"
        const val TOKEN_SIZE = "$packageName.GameActivity.TOKEN_SIZE"
        const val TOKEN_COLOR_1 = "$packageName.GameActivity.TOKEN_COLOR_1"
        const val TOKEN_COLOR_2 = "$packageName.GameActivity.TOKEN_COLOR_2"
        const val HAVE_OPPONENT = "$packageName.GameActivity.HAVE_OPPONENT"
        const val START_FROM_PLAYER_LIST = "$packageName.GameActivity.START_FROM_PLAYER_LIST"
        const val PARCELABLE_VALUES = "$packageName.GameActivity.PARCELABLE_VALUES"
        const val DISTANCE_UNIT_OF_MEASURE = "packageName.GameActivity.DISTANCE_UNIT_OF_MEASURE"
        private const val MSG_COMPUTER_TURN = 1
        private const val NEW_GAME_FROM_CLIENT = 2
        private const val MSG_NETWORK_CLIENT_TURN = 3
        private const val MSG_NETWORK_SERVER_TURN = 4
        private const val MSG_NETWORK_SET_TOKEN_CHOICE = 5
        private const val DISMISS_WAIT_FOR_NEW_GAME_FROM_CLIENT = 6
        private const val DISMISS_WAIT_FOR_NEW_GAME_FROM_SERVER = 7
        private const val ACCEPT_INCOMING_GAME_REQUEST_FROM_CLIENT = 8
        private const val MSG_NETWORK_CLIENT_MAKE_FIRST_MOVE = 9
        private const val MSG_NETWORK_SERVER_REFUSED_GAME = 10
        private const val MSG_NETWORK_SERVER_LEFT_GAME = 11
        private const val MSG_NETWORK_CLIENT_REFUSED_GAME = 12
        private const val MSG_NETWORK_CLIENT_LEFT_GAME = 13
        private const val PLAYER_TIMED_OUT_CLIENT = 14
        private const val PLAYER_TIMED_OUT_SERVER = 15
        private const val MSG_OPPONENT_TIMED_OUT = 16
        private const val COMPUTER_DELAY_MS: Long = 500
        private const val THREAD_SLEEP_INTERVAL = 300 //milliseconds
        private const val mRegularWin = 10
        private const val mSuperWin = 30
        private var mPlayer1Id = 0
        var mPlayer2Id: String? = null
        private var mPlayer1Name: String? = null
        var mPlayer2Name: String? = null
        private var mGameView: GameView? = null
        private var mPlayer1Score = 0
        private var mPlayer2Score = 0
        private var mWillyScore = 0
        private var mPlayer1NetworkScore = 0
        private var mPlayer2NetworkScore = 0
        var moveModeTouch  = false //false = drag move mode; true = touch move mode
        var soundMode = false //false = no sound; true = sound
        private var HUMAN_VS_HUMAN = false
        private var HUMAN_VS_NETWORK = false
        private var mSavedCell = 0 //hack for saving cell selected when XO token is chosen as first move
        private var mButtonStartText: CharSequence? = null
        private var mServerRunning = false
        private var mClientRunning = false
        private var mServerIsPlayingNow = false
        private var mBallMoved = 0 //hack for correcting problem with resetting mBallId to -1 in mGameView.disableBall()
        private lateinit var resources: Resources
        private var mHostWaitDialog: AlertDialog? = null
        private var mOpponentLeftGameAlert: AlertDialog? = null
        private var mWinByTimeoutAlert: AlertDialog? = null
        private var mClientWaitDialog: AlertDialog? = null
        private var mLikeToPlayDialog: AlertDialog? = null
        private var mServerRefusedGame: AlertDialog? = null
        private var mChooseTokenDialog: AlertDialog? = null
        private var mForfeitGameDialog: AlertDialog? = null
        private var mNetworkOpponentPlayerName: String? = null
        private var mLastCellSelected = 0
        private var mHostName: String? = null
        private var mQueuePrefix: String? = null
        private const val ACCEPT_GAME = 1
        private const val REJECT_GAME = 2
        private var mGameStarted = false
        private var mStartSource: String? = null
        private var HUMAN_VS_WILLY: Boolean = false

        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(resources.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }

        var isClientRunning: Boolean
            get() = mClientRunning
            set(clientRunning) {
                mClientRunning = clientRunning
            }

        var isGameStarted: Boolean
            get() = mGameStarted
            set(gameStarted) {
                mGameStarted = gameStarted
            }

        private var isServerRunning: Boolean
            get() = mServerRunning
            private set(serverRunning) {
                mServerRunning = serverRunning
            }
    }
}