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

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.guzzardo.tictacdoh2.ColorBall.Companion.setTokenColor
import com.guzzardo.tictacdoh2.GameActivity.ClientThread
import com.guzzardo.tictacdoh2.GameActivity.Companion.moveModeTouch
import com.guzzardo.tictacdoh2.WillyShmoApplication.UserPreferences
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.isNetworkAvailable
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.prizesAreAvailable
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.playersTooClose
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import kotlin.math.sqrt

class GameView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    enum class State(val value: Int) {
        UNKNOWN(-3), WIN(-2), EMPTY(0), PLAYER1(1), PLAYER2(2), PLAYERBOTH(3);

        companion object {
            fun fromInt(i: Int): State {
                for (s in values()) {
                    if (s.value == i) {
                        return s
                    }
                }
                return EMPTY
            }
        }
    }

    private var mViewDisabled = false
    private val mContext: Context
    private val mHandler = Handler(Looper.getMainLooper(), MyHandler())
    private val mSrcRect = Rect()
    private val mDstRect = Rect()
    private val mTakenRect = Rect()
    private var mOffsetX = 0
    private var mOffsetY = 0
    private val mWinPaint: Paint
    private val mLinePaint: Paint
    private val mBmpPaint: Paint
    private val mBmpCrossCenter: Bitmap?
    private val mBmpCircleCenter: Bitmap?
    private val mBmpCircleCrossCenter: Bitmap?
    private val mBmpCircleCrossPlayer1: Bitmap?
    private val mBmpCircleCrossPlayer2: Bitmap?
    private val mBmpAvailableMove: Bitmap?
    private val mTextPaint: Paint
    private val mBmpTakenMove: Bitmap?
    private val mBmpPrize: Bitmap?
    private val mRandom = Random()
    private var mCellListener: ICellListener? = null

    /** Contains one of [State.EMPTY], [State.PLAYER1] or [State.PLAYER2] or [State.PLAYERBOTH] where PlayerBoth = XO card.  */
    private val data = arrayOfNulls<State>(BoardSpaceValues.BOARDSIZE)
    val boardSpaceAvailableValues = BooleanArray(BoardSpaceValues.BOARDSIZE) // false = not available
    val boardSpaceValues = IntArray(BoardSpaceValues.BOARDSIZE) // -1 = empty, 0 = circle, 1 = cross 2 = circleCross
    private var mSelectedCell = -1
    private var mSelectedValue = State.EMPTY
    private var mCurrentPlayer = State.UNKNOWN
    var winner = State.EMPTY
    private var mWinCol = -1
    private var mWinRow = -1
    private var mWinDiag = -1
    private var mBlinkDisplayOff = false
    private val mBlinkRect = Rect()

    private val mColorBall = arrayOfNulls<ColorBall>(ColorBall.MAXBALLS) // array that holds the balls
    var ballMoved = -1 // variable to know what ball is being dragged
        //private set

    interface ICellListener {
        fun onCellSelected()
    }

    interface BoardSpaceValues {
        companion object {
            const val EMPTY = -1
            const val CIRCLE = 0
            const val CROSS = 1
            const val CIRCLECROSS = 2
            const val BOARDSIZE = 25
            const val BOARDCENTER = 12
        }
    }

    interface ScreenOrientation {
        companion object {
            const val PORTRAIT = 0
            const val LANDSCAPE = 1
        }
    }

    fun setViewDisabled(disabled: Boolean) {
        mViewDisabled = disabled
    }

    fun setHumanState(humanState: Boolean) {
        HUMAN_VS_HUMAN = humanState
    }

    private fun setBoardSpaceValue(offset: Int, token: Int) {
        boardSpaceValues[offset] = token
        boardSpaceAvailableValues[offset] = false
    }

    fun setBoardSpaceValueCenter(tokenType: Int) {
        boardSpaceValues[BoardSpaceValues.BOARDCENTER] = tokenType
        boardSpaceAvailableValues[BoardSpaceValues.BOARDCENTER] = false
    }

    fun setBoardSpaceValue(offset: Int) {
        if (ballMoved > -1) {
            boardSpaceValues[offset] = mColorBall[ballMoved]!!.type
            boardSpaceAvailableValues[offset] = false
        }
    }

    fun getBoardSpaceValue(offset: Int): Int {
        return boardSpaceValues[offset]
    }

    fun testBoardFull(howFull: Int): Boolean {
        var tokenCount = 0
        for (x in boardSpaceValues.indices) {
            if (boardSpaceValues[x] != BoardSpaceValues.EMPTY) tokenCount++
        }
        return tokenCount == howFull
    }

    fun setPlayer1TokenChoice(player1TokenChoice: Int) {
        mPlayer1TokenChoice = player1TokenChoice
    }

    fun setPlayer2TokenChoice(player2TokenChoice: Int) {
        mPlayer2TokenChoice = player2TokenChoice
    }

    fun setGamePrize(prize: Boolean) {
        if (prize) {
            if (isNetworkAvailable && prizesAreAvailable && !playersTooClose) {
                prizeLocation = mRandom.nextInt(BoardSpaceValues.BOARDSIZE)
                //mPrizeLocation = 11; //set prize to a fixed location
                mPrizeXBoardLocation = mPrizeXBoardLocationArray[prizeLocation]
                mPrizeYBoardLocation = mPrizeYBoardLocationArray[prizeLocation]
            }
        } else {
            prizeLocation = -1
        }
    }

    fun initalizeGameValues() {
        for (x in data.indices) {
            data[x] = State.EMPTY
            boardSpaceValues[x] = BoardSpaceValues.EMPTY
            boardSpaceAvailableValues[x] = SPACENOTAVAILABLE
        }
        boardSpaceAvailableValues[7] = true
        boardSpaceAvailableValues[11] = true
        boardSpaceAvailableValues[13] = true
        boardSpaceAvailableValues[17] = true
        data[BoardSpaceValues.BOARDCENTER] = State.PLAYERBOTH
        mWinCol = -1
        mWinRow = -1
        mWinDiag = -1
        ballMoved = -1
    }

    fun setTokenCards() {
        for (x in mGameTokenCard.indices) {
            mGameTokenCard[x] = BoardSpaceValues.EMPTY
        }
        for (x in startingGameTokenCard.indices) {
            var positionFilled = false
            while (!positionFilled) {
                val randomCard = mRandom.nextInt(NUMBEROFTOKENS)
                if (mGameTokenCard[randomCard] == BoardSpaceValues.EMPTY) {
                    mGameTokenCard[randomCard] = startingGameTokenCard[x]
                    positionFilled = true
                }
            }
        }
    }

    //TODO- this method should be combined with initializePlayerTokens
    private fun initializeBallPositions() {
        var incrementX = portraitIncrementXPlayer1
        var startX = portraitStartingXPlayer1
        var incrementY = portraitIncrementYPlayer1
        var startY = portraitStartingYPlayer1
        if (mDisplayMode == ScreenOrientation.LANDSCAPE) {
            incrementX = landscapeIncrementXPlayer1
            startX = landscapeStartingXPlayer1
            incrementY = landscapeIncrementYPlayer
            startY = landscapeStartingXPlayer1
        }
        for (x in 0..3) {
            mStartingPlayerTokenPositions[x][0] = startX
            mStartingPlayerTokenPositions[x][1] = startY
            startX += incrementX
            startY += incrementY
        }
    }

    private fun initializePlayerTokens(context: Context) {
        var portraitLocationXPlayer1 = portraitStartingXPlayer1
        var portraitLocationYPlayer1 = portraitStartingYPlayer1
        var portraitLocationXPlayer2 = portraitStartingXPlayer2
        var portraitLocationYPlayer2 = portraitStartingYPlayer2
        var landscapeLocationXPlayer1 = landscapeStartingXPlayer1
        var landscapeLocationYPlayer1 = landscapeStartingYPlayer1
        var landscapeLocationXPlayer2 = landscapeStartingXPlayer2
        var landscapeLocationYPlayer2 = landscapeStartingYPlayer2
        setTokenCards()
        setBoardSpaceValue(BoardSpaceValues.BOARDCENTER, mGameTokenCard[NUMBEROFTOKENS - 1])
        val tokenPointLandscape = Point()
        val tokenPointPortrait = Point()
        for (x in 0..3) {
            tokenPointLandscape[landscapeLocationXPlayer1] = landscapeLocationYPlayer1
            tokenPointPortrait[portraitLocationXPlayer1] = portraitLocationYPlayer1
            landscapeLocationXPlayer1 += landscapeIncrementXPlayer1
            portraitLocationXPlayer1 += portraitIncrementXPlayer1
            landscapeLocationYPlayer1 += landscapeIncrementYPlayer
            portraitLocationYPlayer1 += portraitIncrementYPlayer1
            var resource = R.drawable.lib_circlered
            if (mGameTokenCard[x] == ColorBall.CROSS) resource =
                R.drawable.lib_crossred else if (mGameTokenCard[x] == ColorBall.CIRCLECROSS) resource =
                R.drawable.lib_circlecrossred
            mColorBall[x] = ColorBall(
                context,
                resource,
                tokenPointLandscape,
                tokenPointPortrait,
                mDisplayMode,
                mGameTokenCard[x],
                mTokenColor1
            )
        }
        for (x in 4 until NUMBEROFTOKENS - 1) {
            tokenPointLandscape[landscapeLocationXPlayer2] = landscapeLocationYPlayer2
            tokenPointPortrait[portraitLocationXPlayer2] = portraitLocationYPlayer2
            landscapeLocationXPlayer2 += landscapeIncrementXPlayer2
            portraitLocationXPlayer2 += portraitIncrementXPlayer2
            landscapeLocationYPlayer2 += landscapeIncrementYPlayer
            portraitLocationYPlayer2 += portraitIncrementYPlayer2
            var resource = R.drawable.lib_circleblue
            if (mGameTokenCard[x] == ColorBall.CROSS) resource =
                R.drawable.lib_crossblue else if (mGameTokenCard[x] == ColorBall.CIRCLECROSS) resource =
                R.drawable.lib_circlecrossblue
            mColorBall[x] = ColorBall(
                context,
                resource,
                tokenPointLandscape,
                tokenPointPortrait,
                mDisplayMode,
                mGameTokenCard[x],
                mTokenColor2
            )
        }
        if (isClientRunning) {
            sendTokensToServer()
        }
        writeToLog("GameView", "Game started: ${GameActivity.isGameStarted} Opposing player ID:  ${GameActivity.mPlayer2Id}, " +
            "Opposing player Name:  ${GameActivity.mPlayer2Name}")
    }

    fun updatePlayerToken(id: Int, tokenType: Int) {
        var bitmap: Bitmap? //= null
        if (id < 4) {
            bitmap = mBmpCirclePlayer1
            //resource = R.drawable.lib_circlered;
            if (tokenType == ColorBall.CROSS) {
                bitmap = mBmpCrossPlayer1
            } else {
                if (tokenType == ColorBall.CIRCLECROSS) {
                    bitmap = mBmpCircleCrossPlayer1
                }
            }
        } else {
            bitmap = mBmpCirclePlayer2
            if (tokenType == ColorBall.CROSS) {
                bitmap = mBmpCrossPlayer2
            } else {
                if (tokenType == ColorBall.CIRCLECROSS) {
                    bitmap = mBmpCircleCrossPlayer2
                }
            }
        }
        val ball = mColorBall[id]
        ball!!.updateBall(tokenType, bitmap!!)
    }

    fun sendTokensToServer() {
        try {
            val tokenList = JSONObject()
            val tokenArray = JSONArray()
            for (x in 0 until NUMBEROFTOKENS) {
                val tokenValues = JSONObject()
                tokenValues.put("tokenId", x)
                tokenValues.put("tokenType", mGameTokenCard[x])
                tokenArray.put(tokenValues)
            }
            tokenList.put("tokenList", tokenArray)
            val player1Name = mClientThread!!.player1Name
            val player1Id = mClientThread!!.player1Id
            tokenList.put("player1Name", player1Name)
            tokenList.put("player1Id", player1Id)
            val tokenListString = tokenList.toString()
            mGameActivity!!.sendMessageToServerHost(tokenListString)
            setViewDisabled(true)
            mGameActivity!!.highlightCurrentPlayer(State.PLAYER2)
        } catch (e: JSONException) {
            mGameActivity!!.sendToastMessage(e.message)
        }
    }

    private fun resetUnusedTokens() {
        for (ball in mColorBall) {
            if (ball!!.isDisabled) continue
            if (ball.iD == ballMoved) continue
            ball.resetPosition(mDisplayMode)
        }
    }

    fun setCell(cellIndex: Int, value: State?) {
        data[cellIndex] = value
        invalidate()
    }

    fun setCellListener(cellListener: ICellListener?) {
        mCellListener = cellListener
    }

    val selection: Int
        get() = if (mSelectedValue == mCurrentPlayer) {
            mSelectedCell
        } else -1
    var currentPlayer: State
        get() = mCurrentPlayer
        set(player) {
            mCurrentPlayer = player
            mSelectedCell = -1
        }

    // Sets winning mark on specified column or row (0..2) or diagonal (0..1).
    fun setFinished(col: Int, row: Int, diagonal: Int) {
        mWinCol = col
        mWinRow = row
        mWinDiag = diagonal
    }

    private fun getTokenToDraw(location: Int): Bitmap? {
        var cross = mBmpCrossPlayer1
        var circle = mBmpCirclePlayer1
        var circleCross = mBmpCircleCrossPlayer1
        if (data[location] == State.PLAYER2) {
            cross = mBmpCrossPlayer2
            circle = mBmpCirclePlayer2
            circleCross = mBmpCircleCrossPlayer2
        }
        var tokenToDraw = circle
        if (boardSpaceValues[location] == BoardSpaceValues.CROSS) tokenToDraw =
            cross else if (boardSpaceValues[location] == BoardSpaceValues.CIRCLECROSS) tokenToDraw =
            circleCross
        return tokenToDraw
    }

    private val tokenToDrawCenter: Bitmap?
        get() {
            var tokenToDraw = mBmpCircleCenter
            if (boardSpaceValues[BoardSpaceValues.BOARDCENTER] == BoardSpaceValues.CROSS) tokenToDraw =
                mBmpCrossCenter else if (boardSpaceValues[BoardSpaceValues.BOARDCENTER] == BoardSpaceValues.CIRCLECROSS) tokenToDraw =
                mBmpCircleCrossCenter
            return tokenToDraw
        }

    private fun drawPlayerToken(canvas: Canvas, location: Int) {
        if (boardSpaceValues[location] != BoardSpaceValues.EMPTY) {
            canvas.drawBitmap(
                mBmpTakenMove!!,
                mTakenRect,
                mDstRect,
                mBmpPaint
            ) //revert background to black
            val tokenToDraw = getTokenToDraw(location)
            canvas.drawBitmap(tokenToDraw!!, mSrcRect, mDstRect, mBmpPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s3 = mSxy * 5
        val x7 = mOffsetX
        val y7 = mOffsetY
        run {
            var i = 0
            var k = mSxy
            while (i < 4) {
                canvas.drawLine(
                    x7.toFloat(),
                    (y7 + k).toFloat(),
                    (x7 + s3 - 1).toFloat(),
                    (y7 + k).toFloat(),
                    mLinePaint
                ) // draw horizontal rows 
                canvas.drawLine(
                    (x7 + k).toFloat(),
                    y7.toFloat(),
                    (x7 + k).toFloat(),
                    (y7 + s3 - 1).toFloat(),
                    mLinePaint
                ) // draw vertical columns
                i++
                k += mSxy
            }
        }
        setAvailableMoves(canvas, mSelectedCell, boardSpaceValues, boardSpaceAvailableValues)
        val prizeDrawn = false
        if (moveModeTouch) {
            if (mSelectedCell > -1) {
                val xValue = mSelectedCell % 5
                val yValue = calculateYValue(mSelectedCell)
                mDstRect.offsetTo(
                    MARGIN + mOffsetX + mSxy * xValue,
                    MARGIN + mOffsetY + mSxy * yValue
                )
                if (mBlinkDisplayOff) {
                    canvas.drawBitmap(mBmpAvailableMove!!, mTakenRect, mDstRect, null)
                } else {
                    canvas.drawBitmap(mBmpTakenMove!!, mTakenRect, mDstRect, null)
                }
            }
        }
        mDstRect.offsetTo(MARGIN + mOffsetX + mSxy * 2, MARGIN + mOffsetY + mSxy * 2)
        val tokenToDraw: Bitmap? = tokenToDrawCenter
        canvas.drawBitmap(tokenToDraw!!, mSrcRect, mDstRect, mBmpPaint)
        if (prizeLocation > -1 && !prizeDrawn) {
            mDstRect.offsetTo(
                MARGIN + mOffsetX + mSxy * mPrizeXBoardLocation,
                MARGIN + mOffsetY + mSxy * mPrizeYBoardLocation
            )
            canvas.drawBitmap(mBmpPrize!!, mSrcRect, mDstRect, mBmpPaint)
        }
        var j = 0
        var k = 0
        var y = y7
        while (j < 5) {
            var i = 0
            var x = x7
            while (i < 5) {
                mDstRect.offsetTo(MARGIN + x, MARGIN + y)
                var v: State?
                if (mSelectedCell == k) {
                    if (mBlinkDisplayOff) {
                        i++
                        k++
                        x += mSxy
                        continue
                    }
                    v = mSelectedValue
                } else {
                    v = data[k]
                }
                if (HUMAN_VS_HUMAN && (v == State.PLAYER1 || v == State.PLAYER2)) {
                    if (mSelectedCell == k && boardSpaceAvailableValues[k]) {
                        //this is required to allow blinking
                        canvas.drawBitmap(mBmpAvailableMove!!, mTakenRect, mDstRect, null)
                    } else {
                        if (k != BoardSpaceValues.BOARDCENTER) {
                            drawPlayerToken(canvas, k)
                        }
                    }
                } else {
                    when (v) {
                        State.PLAYER1 -> if (mSelectedCell == k && boardSpaceAvailableValues[k]) {
                            //this is required to allow blinking
                            canvas.drawBitmap(mBmpAvailableMove!!, mTakenRect, mDstRect, null)
                        } else {
                            drawPlayerToken(canvas, k)
                        }
                        State.PLAYER2 -> drawPlayerToken(canvas, k)
                        else -> { /* not applicable to player2 */ }
                    }
                }
                i++
                k++
                x += mSxy
            }
            j++
            y += mSxy
        }
        if (mWinRow >= 0) {
            val a = y7 + mWinRow * mSxy + mSxy / 2
            canvas.drawLine(
                (x7 + MARGIN).toFloat(),
                a.toFloat(),
                (x7 + s3 - 1 - MARGIN).toFloat(),
                a.toFloat(),
                mWinPaint
            )
        } else if (mWinCol >= 0) {
            val x = x7 + mWinCol * mSxy + mSxy / 2
            canvas.drawLine(
                x.toFloat(),
                (y7 + MARGIN).toFloat(),
                x.toFloat(),
                (y7 + s3 - 1 - MARGIN).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 0) {
            // diagonal 0 is from (0,0) to (2,2)
            canvas.drawLine(
                (x7 + MARGIN).toFloat(),
                (y7 + MARGIN).toFloat(),
                (x7 + s3 - 1 - MARGIN).toFloat(),
                (y7 + s3 - 1 - MARGIN).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 2) {
            // diagonal 0 is from (0,0) to (2,2)
            canvas.drawLine(
                (x7 + MARGIN + mSxy).toFloat(),
                (y7 + MARGIN).toFloat(),
                (x7 + s3 - 1 - MARGIN).toFloat(),
                (y7 + s3 - 1 - MARGIN - mSxy).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 3) {
            canvas.drawLine(
                (x7 + MARGIN + 2 * mSxy).toFloat(),
                (y7 + MARGIN).toFloat(),
                (x7 + s3 - 1 - MARGIN).toFloat(),
                (y7 + s3 - 1 - MARGIN - 2 * mSxy).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 5) {
            canvas.drawLine(
                (x7 + MARGIN).toFloat(),
                (y7 + MARGIN + 2 * mSxy).toFloat(),
                (x7 + s3 - 1 - MARGIN - 2 * mSxy).toFloat(),
                (y7 + s3 - 1 - MARGIN).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 4) {
            canvas.drawLine(
                (x7 + MARGIN).toFloat(),
                (y7 + MARGIN + mSxy).toFloat(),
                (x7 + s3 - 1 - MARGIN - mSxy).toFloat(),
                (y7 + s3 - 1 - MARGIN).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 6) {
            // diagonal 6 is from (0,3) to (15,0)
            canvas.drawLine(
                (x7 + MARGIN - 0 * mSxy).toFloat(),
                (y7 + s3 - 1 - MARGIN - 1 * mSxy).toFloat(),
                (x7 + s3 + 2 - MARGIN - 1 * mSxy).toFloat(),
                (y7 + MARGIN).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 7) {
            // diagonal 7 is from (0,4) to (20,0)
            canvas.drawLine(
                (x7 + MARGIN).toFloat(),
                (y7 + s3 - 1 - MARGIN).toFloat(),
                (x7 + s3 - 1 - MARGIN).toFloat(),
                (y7 + MARGIN).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 1) {
            // diagonal 1 is from (0,2) to (10,0)
            canvas.drawLine(
                (x7 + MARGIN - 0 * mSxy).toFloat(),
                (y7 + s3 - 1 - MARGIN - 2 * mSxy).toFloat(),
                (x7 + s3 + 2 - MARGIN - 2 * mSxy).toFloat(),
                (y7 + MARGIN).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 8) {
            // diagonal 8 is from (1,4) to (4,1)
            canvas.drawLine(
                (x7 + MARGIN + 1 * mSxy).toFloat(),
                (y7 + s3 - 1 - MARGIN - 0 * mSxy).toFloat(),
                (x7 + s3 + 2 - MARGIN + 0 * mSxy).toFloat(),
                (y7 + MARGIN + 1 * mSxy).toFloat(),
                mWinPaint
            )
        } else if (mWinDiag == 9) {
            // diagonal 9 is from (2,4) to (4,2)
            canvas.drawLine(
                (x7 + MARGIN + 2 * mSxy).toFloat(),
                (y7 + s3 - 1 - MARGIN - 0 * mSxy).toFloat(),
                (x7 + s3 + 2 - MARGIN + 0 * mSxy).toFloat(),
                (y7 + MARGIN + 2 * mSxy).toFloat(),
                mWinPaint
            )
        }

        //draw the balls on the canvas
        if (moveModeTouch) {
            for (x in mColorBall.indices) {
                if (!mColorBall[x]!!.isDisabled) {
                    if (ballMoved == x && mBlinkDisplayOff) continue
                    mDstRect.offsetTo(mColorBall[x]!!.getCoordX(), mColorBall[x]!!.getCoordY())
                    canvas.drawBitmap(mColorBall[x]!!.bitmap, mSrcRect, mDstRect, mBmpPaint)
                }
            }
        } else {
            for (ball in mColorBall) {
                if (!ball!!.isDisabled) {
                    mDstRect.offsetTo(ball.getCoordX(), ball.getCoordY())
                    canvas.drawBitmap(ball.bitmap, mSrcRect, mDstRect, mBmpPaint)
                }
            }
        }
    }

    private fun calculateLeftLimit(boardSpaceValue: IntArray): Int {
        run {
            var x = 4
            while (x < 25) {
                if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
                    return 2
                }
                x += 5
            }
        }
        var x = 3
        while (x < 24) {
            if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
                return 1
            }
            x += 5
        }
        return 0
    }

    private fun calculateRightLimit(boardSpaceValue: IntArray): Int {
        run {
            var x = 0
            while (x < 21) {
                if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
                    return 2
                }
                x += 5
            }
        }
        var x = 1
        while (x < 22) {
            if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
                return 3
            }
            x += 5
        }
        return 4
    }

    private fun calculateTopLimit(boardSpaceValue: IntArray): Int {
        for (x in 20..24) if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
            return 2
        }
        for (x in 15..19) if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
            return 1
        }
        return 0
    }

    private fun calculateBottomLimit(boardSpaceValue: IntArray): Int {
        for (x in 0..4) if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
            return 2
        }
        for (x in 5..9) if (boardSpaceValue[x] != BoardSpaceValues.EMPTY) {
            return 3
        }
        return 4
    }

    private fun resetAvailableMoves(canvas: Canvas?, boardSpaceAvailable: BooleanArray, selectedCell: Int) {
        for (x in boardSpaceAvailable.indices) {
            val xValue = x % 5
            val yValue = calculateYValue(x)
            if (selectedCell == x) continue
            boardSpaceAvailable[x] = SPACENOTAVAILABLE
            if (canvas != null) {
                mDstRect.offsetTo(
                    MARGIN + mOffsetX + mSxy * xValue,
                    MARGIN + mOffsetY + mSxy * yValue
                )
                canvas.drawBitmap(mBmpTakenMove!!, mTakenRect, mDstRect, null)
            }
        }
    }

    fun setAvailableMoves(selectedCell: Int, boardSpaceValue: IntArray, boardSpaceAvailable: BooleanArray) {
        setAvailableMoves(null, selectedCell, boardSpaceValue, boardSpaceAvailable)
    }

    //	if the position checked is occupied determine if left, right, up and down exist
    //	for each direction that exists check if its already occupied
    //	if its not, then its available
    private fun setAvailableMoves(canvas: Canvas?, selectedCell: Int, boardSpaceValue: IntArray, boardSpaceAvailable: BooleanArray) {
        resetAvailableMoves(canvas, boardSpaceAvailable, selectedCell)
        val leftLimit = calculateLeftLimit(boardSpaceValue)
        val rightLimit = calculateRightLimit(boardSpaceValue)
        val topLimit = calculateTopLimit(boardSpaceValue)
        val bottomLimit = calculateBottomLimit(boardSpaceValue)
        //calculate leftLimit, rightLimit, topLimit and bottomLimit
        //if xValue < leftLimit then position is not available, similarly for the remaining directions
        for (x in boardSpaceValue.indices) {
            var leftExists = false
            var rightExists = false
            var upExists = false
            var downExists = false
            if (boardSpaceValue[x] == -1) continue
            if (x % 5 > 0) leftExists = true
            if (x != 4 && x != 9 && x != 14 && x != 19 && x != 24) rightExists = true
            if (x > 4) upExists = true
            if (x < 20) downExists = true
            val xValue = x % 5
            val yValue = calculateYValue(x)
            if (leftExists) {
                if (xValue > leftLimit) if (boardSpaceValue[x - 1] == -1 && selectedCell != x - 1) {
                    if (canvas != null) {
                        drawAvailableSquare(canvas, xValue - 1, yValue)
                    }
                    boardSpaceAvailable[x - 1] = true
                }
            }
            if (rightExists) {
                if (xValue < rightLimit) if (boardSpaceValue[x + 1] == -1 && selectedCell != x + 1) {
                    if (canvas != null) {
                        drawAvailableSquare(canvas, xValue + 1, yValue)
                    }
                    boardSpaceAvailable[x + 1] = true
                }
            }
            if (upExists) {
                if (yValue > topLimit) {
                    if (boardSpaceValue[x - 5] == -1 && selectedCell != x - 5) {
                        if (canvas != null) {
                            drawAvailableSquare(canvas, xValue, yValue - 1)
                        }
                        boardSpaceAvailable[x - 5] = true
                    }
                }
            }
            if (downExists) {
                if (yValue < bottomLimit) {
                    if (boardSpaceValue[x + 5] == -1 && selectedCell != x + 5) {
                        if (canvas != null) {
                            drawAvailableSquare(canvas, xValue, yValue + 1)
                        }
                        boardSpaceAvailable[x + 5] = true
                    }
                }
            }
        }
    }

    private fun drawAvailableSquare(canvas: Canvas, xValue: Int, yValue: Int) {
        mDstRect.offsetTo(MARGIN + mOffsetX + mSxy * xValue, MARGIN + mOffsetY + mSxy * yValue)
        canvas.drawBitmap(mBmpAvailableMove!!, mTakenRect, mDstRect, null)
    }

    //calculate the Y offset given the cell position on the game board
    private fun calculateYValue(cellNumber: Int): Int {
        return if (cellNumber < 5) 0 else if (cellNumber < 10) 1 else if (cellNumber < 15) 2 else if (cellNumber < 20) 3 else 4
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the view squared
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        var d = if (w == 0) h else if (h == 0) w else if (w < h) w else h
        val modeW = MeasureSpec.getMode(widthMeasureSpec)
        val modeH = MeasureSpec.getMode(heightMeasureSpec)
        if (modeH == MeasureSpec.EXACTLY && modeW == MeasureSpec.EXACTLY) {
            mDisplayMode = ScreenOrientation.LANDSCAPE
            val screenBoardSize = if (w < h) w else h
            //TODO - try to consolidate these 4 if conditions into a single use case
            if (screenBoardSize < 400) { //LG G1 screenBoardSize = 320
                val result = mTokenSize * 3 / 150.0 * 40.0
                TOKENSIZE = result.toInt()
                mOffsetY = 20 // offset of board grid from top of screen
                landscapeIncrementYPlayer = 50
                mTokenRadius = 40
            } else if (screenBoardSize < 500) { //WVGA800 screenBoardSize = 442
                val result = mTokenSize * 3 / 150.0 * 50.0
                TOKENSIZE = result.toInt()
                mOffsetY = 40
                landscapeIncrementYPlayer = 80
                mTokenRadius = 45
            } else if (screenBoardSize < 800) {// on my Samsung J7 screenBoardSize = 672, on my Galaxy Note screenBoardSize = 720, on my Nexus 6 screenBoardSize = 661
                val result = mTokenSize * 3 / 150.0 * 90.0
                TOKENSIZE = result.toInt()
                mOffsetY = 60 //was 120
                landscapeIncrementYPlayer = 100
                mTokenRadius = 55
            } else { // my LG V10, 10.1 WXGA 2 Tablet Emulator
                val result = mTokenSize * 3 / 150.0 * 130.0
                TOKENSIZE = result.toInt()
                mOffsetY = 80
                landscapeIncrementYPlayer = TOKENSIZE
                val workRadius = TOKENSIZE * .75
                mTokenRadius = workRadius.toInt() // was hard coded at 65 then changed to TOKENSIZE / 2
            }
            mSxy = TOKENSIZE + 2 // calculated card size
            BoardLowerLimit = mOffsetY + mSxy * 5
            val playingBoardWidth = (mSxy + GRIDLINEWIDTH) * 5
            landscapeRightMoveXLimitPlayer1 = w / 2 + playingBoardWidth / 2
            landscapeLeftMoveXLimitPlayer2 = w / 2 - playingBoardWidth / 2
            landscapeRightMoveXLimitPlayer2 = w - mSxy
            landscapeLeftMoveXLimitPlayer1 = mSxy * 2
            landscapeStartingXPlayer2 = w - landscapeHumanTokenSelectedOffsetX - mSxy
            mOffsetX = (w - playingBoardWidth) / 2
            mDstRect[MARGIN, MARGIN, mSxy - MARGIN - 1] = mSxy - MARGIN - 1
            setMeasuredDimension(w, h)
        }
        if (!INITIALIZATIONCOMPLETED) {
            initializeBallPositions()
            initializePlayerTokens(mContext)
            INITIALIZATIONCOMPLETED = true
        }
        resetUnusedTokens()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mViewDisabled) return false
        if (moveModeTouch) return moveModeTouch(event)
        val action = event.action
        var X = event.x.toInt()
        var Y = event.y.toInt()
        var ballStart = 0
        var ballLimit = 4
        var rightLimit = landscapeRightMoveXLimitPlayer1 // default to 1 player landscape
        var leftLimit = landscapeLeftMoveXLimitPlayer1 // default to 1 player landscape
        if (HUMAN_VS_HUMAN) {
            if (mCurrentPlayer == State.PLAYER2) {
                ballStart = 4
                ballLimit = 8
                if (mDisplayMode == ScreenOrientation.LANDSCAPE) {
                    rightLimit = landscapeRightMoveXLimitPlayer2
                    leftLimit = landscapeLeftMoveXLimitPlayer2
                } else {
                    rightLimit = portraitComputerLiteralOffset
                    leftLimit = portraitHumanTokenSelectedOffsetX
                }
            }
        } else if (mDisplayMode == ScreenOrientation.PORTRAIT) {
            rightLimit = portraitComputerLiteralOffset
            leftLimit = portraitHumanTokenSelectedOffsetX
        }
        if (action == MotionEvent.ACTION_DOWN) {
            // touch down so check if the finger is on a ball
            ballMoved = -1
            for (x in ballStart until ballLimit) {
                val ball = mColorBall[x]
                if (ball!!.isDisabled) continue
                // check if inside the bounds of the ball (circle)
                // get the center for the ball
                val centerX = ball.getCoordX() + 25
                val centerY = ball.getCoordY() + 25
                // calculate the radius from the touch to the center of the ball
                val radCircle =
                    sqrt(((centerX - X) * (centerX - X) + (centerY - Y) * (centerY - Y)).toDouble())
                // if the radius is smaller then 23 (radius of a ball is 22), then it must be on the ball
                if (radCircle < mTokenRadius) {
                    ballMoved = ball.iD
                    break
                }
            }
            invalidate()
            return true
        } else if (action == MotionEvent.ACTION_MOVE) {
            X = event.x.toInt()
            Y = event.y.toInt()
            // move the balls the same as the finger
            if (ballMoved > -1) {
                if (X > leftLimit && X < rightLimit) mColorBall[ballMoved]!!.setCoordX(X - 25)
                if (Y > 25 && Y < BoardLowerLimit) mColorBall[ballMoved]!!
                    .setCoordY(Y - 25)
            }
            invalidate()
            return true
        } else if (action == MotionEvent.ACTION_UP) {
            X = event.x.toInt()
            Y = event.y.toInt()
            val xPos = (X - mOffsetX) / mSxy
            val yPos = (Y - mOffsetY) / mSxy
            if (xPos in 0..4 && yPos in 0..4) {
                val cell = xPos + 5 * yPos
                var state = if (cell == mSelectedCell) mSelectedValue else data[cell]!!
                writeToLog("ClientService", "xPos: $xPos yPos: $yPos calculated cell: $cell")
                writeToLog(
                    "ClientService",
                    "state: " + state + " mSelectedCell: " + mSelectedCell + " mSelectedValue: " + mSelectedValue + " mData[cell]: " + data[cell]
                )

                if (state == State.EMPTY) state = mCurrentPlayer
                writeToLog("ClientService", "cell calculated: $cell")
                if (ballMoved > -1) {
                    if (boardSpaceAvailableValues[cell]) {
                        mDstRect.offsetTo(MARGIN + mOffsetX + mSxy * xPos, MARGIN + mOffsetY + mSxy * yPos)
                        mColorBall[ballMoved]!!.setCoordX(MARGIN + mOffsetX + mSxy * xPos)
                        mColorBall[ballMoved]!!.setCoordY(MARGIN + mOffsetY + mSxy * yPos)
                        mBlinkRect[MARGIN + mOffsetX + xPos * mSxy, MARGIN + mOffsetY + yPos * mSxy, MARGIN + mOffsetX + (xPos + 1) * mSxy] =
                            MARGIN + mOffsetY + (yPos + 1) * mSxy
                        mSelectedCell = cell
                        mSelectedValue = state
                        writeToLog("ClientService", "ball id: $ballMoved cell calculated: $cell")
                    } else {
                        ballMoved = -1
                        stopBlink()
                        writeToLog("ClientService","ball id: $ballMoved cell calculated: $cell space not available")
                    }
                }
                if (ballMoved > -1 && !mColorBall[ballMoved]!!.isDisabled && state != State.EMPTY) {
                    // Start the blinker
                    mHandler.sendEmptyMessageDelayed(MSG_BLINK, FPS_MS)
                    writeToLog("ClientService", "blinker started")
                }
                if (mCellListener != null) {
                    writeToLog("ClientService", "onCellSelected called for ballId: " + ballMoved)
                    if (ballMoved > -1) mCellListener!!.onCellSelected() //activates / de-activates next button 
                    else {
                        mSelectedCell = -1
                        mCellListener!!.onCellSelected()
                    }
                }
            } else {
                ballMoved = -1
                mBlinkRect.setEmpty()
                mSelectedCell = -1
                mCellListener!!.onCellSelected()
                writeToLog("ClientService", "outside of board selected")
            }
            for (x in ballStart until ballLimit) { // only reset 1 side token positions
                val ball = mColorBall[x]
                if (ball!!.isDisabled) {
                    continue
                }
                if (ballMoved == ball.iD) {
                    continue
                }
                //writeToLog("ClientService", "ball reset: $x")
                ball.resetPosition(mDisplayMode)
            }
            invalidate()
            return true
        }
        this.performClick() //added to eliminate warning message: onTouch should call View#performClick when a click is detected
        return false
    }

    override fun performClick(): Boolean { //added to eliminate warning message: onTouch should call View#performClick when a click is detected
        super.performClick()
        return false
    }

    private fun moveModeTouch(event: MotionEvent): Boolean {
        val action = event.action
        val X = event.x.toInt()
        val Y = event.y.toInt()
        var ballStart = 0
        var ballLimit = 4
        if (HUMAN_VS_HUMAN && mCurrentPlayer == State.PLAYER2) {
            ballStart = 4
            ballLimit = 8
        }
        // if token is blinking and we've selected another valid token then turn blinking off original token and blink new token
        // if nothing is blinking then make either a valid selected token or a valid selected move to position blink
        // if position is blinking and we've selected a valid token then move selected token to blinking position
        // if token is blinking and we've selected a valid position then move blinking selected token to valid position

        // if position is blinking and we've selected another valid position then turn blinking off original position and blink new position
        // if blink position has been filled with a selected token and user selects another token then swap tokens in blinking position
        // if blink position has been filled with a selected token and user selects another valid position then swap positions with same token
        // if anything else is touched then don't change anything that is currently blinking (selected)
        if (action == MotionEvent.ACTION_DOWN) {
            return true
        } else if (action == MotionEvent.ACTION_UP) {
            // touch up so check if the finger is on a ball
            // if we've touched a valid ball or a valid square on the board then make it blink
            //mBallId = -1;
            for (x in ballStart until ballLimit) {
                val ball = mColorBall[x]
                if (ball!!.isDisabled) {
                    continue
                }
                // check if inside the bounds of the ball (circle)
                // get the center for the ball
                val centerX = ball.getCoordX() + 25
                val centerY = ball.getCoordY() + 25
                // calculate the radius from the touch to the center of the ball
                val radCircle =
                    sqrt(((centerX - X) * (centerX - X) + (centerY - Y) * (centerY - Y)).toDouble())
                // if the radius is smaller then 23 (radius of a ball is 22), then it must be on the ball
                if (radCircle < mTokenRadius) {
                    ballMoved = ball.iD
                    break
                }
            }
            for (x in ballStart until ballLimit) { // only reset 1 side token positions
                val ball = mColorBall[x]
                if (ball!!.isDisabled) {
                    continue
                }
                if (ballMoved == ball.iD) {
                    continue
                }
                //writeToLog("ClientService", "ball reset: $x")
                ball.resetPosition(mDisplayMode)
            }
            val xPos = (X - mOffsetX) / mSxy
            val yPos = (Y - mOffsetY) / mSxy
            if (xPos in 0..4 && yPos in 0..4) {
                val cell = xPos + 5 * yPos
                var state = if (cell == mSelectedCell) mSelectedValue else data[cell]!!
                writeToLog("ClientService", "xPos: $xPos yPos: $yPos calculated cell: $cell")
                writeToLog(
                    "ClientService",
                    "state: " + state + " mSelectedCell: " + mSelectedCell + " mSelectedValue: " + mSelectedValue + " mData[cell]: " + data[cell]
                )
                if (state == State.EMPTY) {
                    state = mCurrentPlayer
                }
                writeToLog("ClientService", "cell calculated: $cell")
                if (ballMoved > -1) {
                    if (boardSpaceAvailableValues[cell]) {
                        mDstRect.offsetTo(MARGIN + mOffsetX + mSxy * xPos,MARGIN + mOffsetY + mSxy * yPos)
                        mColorBall[ballMoved]!!.setCoordX(MARGIN + mOffsetX + mSxy * xPos)
                        mColorBall[ballMoved]!!.setCoordY(MARGIN + mOffsetY + mSxy * yPos)
                        stopBlinkTouchMode()
                        mBlinkRect[MARGIN + mOffsetX + xPos * mSxy, MARGIN + mOffsetY + yPos * mSxy, MARGIN + mOffsetX + (xPos + 1) * mSxy] =
                            MARGIN + mOffsetY + (yPos + 1) * mSxy
                        mSelectedCell = cell
                        mSelectedValue = state
                        writeToLog("ClientService", "ball id: $ballMoved cell calculated: $cell")
                        mHandler.sendEmptyMessageDelayed(MSG_BLINK, FPS_MS)
                        if (!(mPrevSelectedBall == ballMoved && mPrevSelectedCell == mSelectedCell)) {
                            mCellListener!!.onCellSelected()
                            mPrevSelectedBall = ballMoved
                            mPrevSelectedCell = mSelectedCell
                            invalidate()
                        }
                        return true
                    }
                }
                //if we've touched a valid square on the board then make it blink
                if (boardSpaceAvailableValues[cell]) {
                    stopBlinkTouchMode()
                    mBlinkRect[MARGIN + mOffsetX + xPos * mSxy, MARGIN + mOffsetY + yPos * mSxy, MARGIN + mOffsetX + (xPos + 1) * mSxy] =
                        MARGIN + mOffsetY + (yPos + 1) * mSxy
                    mHandler.sendEmptyMessageDelayed(MSG_BLINK_SQUARE, FPS_MS)
                    mSelectedCell = cell
                    invalidate()
                    return true
                }
            }
            if (ballMoved > -1 && mSelectedCell > -1) {
                val xValue = mSelectedCell % 5
                val yValue = calculateYValue(mSelectedCell)
                mColorBall[ballMoved]!!.setCoordX(MARGIN + mOffsetX + mSxy * xValue)
                mColorBall[ballMoved]!!.setCoordY(MARGIN + mOffsetY + mSxy * yValue)
                mSelectedValue = mCurrentPlayer
                stopBlinkTouchMode()
                mBlinkRect[MARGIN + mOffsetX + xValue * mSxy, MARGIN + mOffsetY + yValue * mSxy, MARGIN + mOffsetX + (xValue + 1) * mSxy] =
                    MARGIN + mOffsetY + (yValue + 1) * mSxy
                mHandler.sendEmptyMessageDelayed(MSG_BLINK, FPS_MS)
                if (!(mPrevSelectedBall == ballMoved && mPrevSelectedCell == mSelectedCell)) {
                    mCellListener!!.onCellSelected()
                    mPrevSelectedBall = ballMoved
                    mPrevSelectedCell = mSelectedCell
                    invalidate()
                }
                return true
            }
            if (ballMoved > -1) {
                val rect = mColorBall[ballMoved]!!.rect
                stopBlinkTouchMode()
                mBlinkRect[rect.left, rect.top, rect.left + TOKENSIZE] =
                    rect.top + TOKENSIZE
                mHandler.sendEmptyMessageDelayed(MSG_BLINK_TOKEN, FPS_MS)
                invalidate()
            }
        }
        return false
    }

    fun selectSpecificComputerToken(type: Int, offense: Boolean): Int {
        for (x in 0..3) {
            if (offense) {
                if ((mColorBall[computerMove + x]!!.type == type || mColorBall[computerMove + x]!!.type == BoardSpaceValues.CIRCLECROSS)
                    && !mColorBall[computerMove + x]!!.isDisabled
                ) return computerMove + x
            } else {
                if (mColorBall[computerMove + x]!!.type == type
                    && !mColorBall[computerMove + x]!!.isDisabled
                ) return computerMove + x
            }
        }
        return -1
    }

    fun selectSpecificHumanToken(type: Int): Int {
        for (x in 0..3) {
            if ((mColorBall[x]!!.type == type || mColorBall[x]!!.type == BoardSpaceValues.CIRCLECROSS) && !mColorBall[x]!!.isDisabled) return x
        }
        return -1
    }

    fun selectLastComputerToken(): Int {
        for (x in 0..3) {
            if (mColorBall[computerMove + x]!!.isDisabled) {
                continue
            }
            return computerMove + x
        }
        return -1
    }

    fun selectRandomComputerToken(): Int {
        var randomNumber = mRandom.nextInt(4)
        var tokenAvailable = 0
        var totalAvailable = 0
        for (x in 4..7) {
            if (mColorBall[x]!!.isDisabled) {
                continue
            }
            tokenAvailable = x
            totalAvailable++
        }
        if (totalAvailable == 1) {
            return tokenAvailable
        }
        for (x in 0..3) {
            if (mColorBall[computerMove + randomNumber]!!.isDisabled || mColorBall[computerMove + randomNumber]!!.type == BoardSpaceValues.CIRCLECROSS) {
                randomNumber++
            }
            if (randomNumber > 3) {
                randomNumber = 0
                continue
            }
            break
        }
        return computerMove + randomNumber
    }

    fun selectRandomAvailableBoardSpace(): Int {
        //build array of avail cells
        var numberAvailable = 0
        for (x in boardSpaceAvailableValues.indices) {
            if (boardSpaceAvailableValues[x]) numberAvailable++
        }
        val boardCellsAvailable = IntArray(numberAvailable)
        var boardCellNumber = 0
        for (x in boardSpaceAvailableValues.indices) {
            if (boardSpaceAvailableValues[x]) boardCellsAvailable[boardCellNumber++] = x
        }
        val randomNumber = mRandom.nextInt(boardCellsAvailable.size)
        return boardCellsAvailable[randomNumber]
    }

    fun moveComputerToken(boardLocation: Int, ballSelected: Int): Int {
        setBoardSpaceValue(boardLocation, mColorBall[ballSelected]!!.type)
        return mColorBall[ballSelected]!!.type
    }

    fun stopBlink() {
        val hadSelection = mSelectedCell != -1 && mSelectedValue != State.EMPTY
        mSelectedCell = -1
        mSelectedValue = State.EMPTY
        if (!mBlinkRect.isEmpty) {
            invalidate()
        }
        mBlinkDisplayOff = false
        mBlinkRect.setEmpty()
        mHandler.removeMessages(MSG_BLINK)
        mHandler.removeMessages(MSG_BLINK_TOKEN)
        mHandler.removeMessages(MSG_BLINK_SQUARE)
        if (hadSelection && mCellListener != null) {
            mCellListener!!.onCellSelected() //enables I'm done button
        }
    }

    private fun stopBlinkTouchMode() {
        mBlinkDisplayOff = false
        mBlinkRect.setEmpty()
        mHandler.removeMessages(MSG_BLINK)
        mHandler.removeMessages(MSG_BLINK_TOKEN)
        mHandler.removeMessages(MSG_BLINK_SQUARE)
    }

    private inner class MyHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            if (msg.what == MSG_BLINK) {
                if (mSelectedCell >= 0 && mSelectedValue != State.EMPTY) {
                    mBlinkDisplayOff = !mBlinkDisplayOff
                    invalidate()
                    if (!mHandler.hasMessages(MSG_BLINK)) {
                        mHandler.sendEmptyMessageDelayed(MSG_BLINK, FPS_MS)
                    }
                }
                return true
            }
            if (msg.what == MSG_BLINK_TOKEN) {
                mBlinkDisplayOff = !mBlinkDisplayOff
                invalidate()
                if (!mHandler.hasMessages(MSG_BLINK_TOKEN)) {
                    mHandler.sendEmptyMessageDelayed(MSG_BLINK_TOKEN, FPS_MS)
                }
                return true
            }
            if (msg.what == MSG_BLINK_SQUARE) {
                mBlinkDisplayOff = !mBlinkDisplayOff
                invalidate()
                if (!mHandler.hasMessages(MSG_BLINK_SQUARE)) {
                    mHandler.sendEmptyMessageDelayed(MSG_BLINK_SQUARE, FPS_MS)
                }
                return true
            }
            return false
        }
    }

    private fun getResBitmap(bmpResId: Int): Bitmap? {
        val opts = BitmapFactory.Options()
        opts.inMutable = true
        var bmp = BitmapFactory.decodeResource(resources, bmpResId, opts)
        if (bmp == null && isInEditMode) {
            val d = ResourcesCompat.getDrawable(resources, bmpResId, null)
            val w = d!!.intrinsicWidth
            val h = d.intrinsicHeight
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, w - 1, h - 1)
            d.draw(c)
        }
        return bmp
    }

    fun disableBall() {
        if (ballMoved > -1) {
            mColorBall[ballMoved]!!.isDisabled = true
        }
        ballMoved = -1
    }

    fun disableBall(ballId: Int) {
        mColorBall[ballId]!!.isDisabled = true
    }

    fun setClient(clientThread: ClientThread?) {
        mClientThread = clientThread
    }

    private val isClientRunning: Boolean
        get() = GameActivity.isClientRunning

    fun setGameActivity(gameActivity: GameActivity?) {
        mGameActivity = gameActivity
    }

    private val sharedPreferences: Unit
        get() {
            val settings = mContext.getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            mTokenSize = settings.getInt(GameActivity.TOKEN_SIZE, 50)
            mTokenColor1 = settings.getInt(GameActivity.TOKEN_COLOR_1, Color.RED)
            mTokenColor2 = settings.getInt(GameActivity.TOKEN_COLOR_2, Color.BLUE)
        }

    companion object {
        const val FPS_MS = (1000 / 2).toLong()

        //constants related to gameBoard drawing:
        private const val MARGIN = 5
        private const val GRIDLINEWIDTH = 4

        //TODO - calculate these 3 values in onMeasure
        private var TOKENSIZE = 0 // bitmap pixel size of X or O card on board
        private var mTokenRadius = 40 // used to specify the touch points when moved by the player's finger
        private const val PORTRAITOFFSETX = 5 // X offset to board grid in portrait mode
        private const val PORTRAITOFFSETY = 5
        private const val PORTRAITWIDTHHEIGHT = 300 // portrait width and height of view square
        private var BoardLowerLimit = 0
        private var mDisplayMode = 0 // portrait or landscape
        private const val portraitComputerLiteralOffset = 245
        private const val portraitHumanTokenSelectedOffsetX = 20
        private var landscapeRightMoveXLimitPlayer1 = 0
        private var landscapeLeftMoveXLimitPlayer2 = 0
        private var landscapeRightMoveXLimitPlayer2 = 0
        private var landscapeLeftMoveXLimitPlayer1 = 0
        private const val landscapeHumanTokenSelectedOffsetX = 25
        private const val portraitIncrementXPlayer1 = 50
        private const val portraitIncrementYPlayer1 = 0
        private const val portraitStartingXPlayer1 = 50
        private const val portraitStartingYPlayer1 = 260
        private const val portraitIncrementXPlayer2 = 0
        private const val portraitIncrementYPlayer2 = 50
        private const val portraitStartingXPlayer2 = 260
        private const val portraitStartingYPlayer2 = 60
        private const val landscapeIncrementXPlayer1 = 0
        private var landscapeIncrementYPlayer = 0
        private const val landscapeStartingXPlayer1 = 50
        private const val landscapeStartingYPlayer1 = 25
        private const val landscapeIncrementXPlayer2 = 0
        private var landscapeStartingXPlayer2 = 0
        private const val landscapeStartingYPlayer2 = 25
        private const val MSG_BLINK = 1
        private const val MSG_BLINK_TOKEN = 2
        private const val MSG_BLINK_SQUARE = 3
        private const val SPACENOTAVAILABLE = false
        private var INITIALIZATIONCOMPLETED = false
        private const val NUMBEROFTOKENS = 9
        private var mTokenSize = 0
        private var mTokenColor1 = 0
        private var mTokenColor2 = 0
        private var computerMove: Int = 0 //temporary value to assign token to computer move
        private var mPlayer1TokenChoice: Int = 0
        private var mPlayer2TokenChoice: Int = 0
        private val mStartingPlayerTokenPositions =
            arrayOf(intArrayOf(0, 0), intArrayOf(0, 0), intArrayOf(0, 0), intArrayOf(0, 0))
        private val startingGameTokenCard = intArrayOf(
            BoardSpaceValues.CIRCLE,
            BoardSpaceValues.CROSS,
            BoardSpaceValues.CIRCLE,
            BoardSpaceValues.CROSS,
            BoardSpaceValues.CIRCLE,
            BoardSpaceValues.CROSS,
            BoardSpaceValues.CIRCLE,
            BoardSpaceValues.CROSS,
            BoardSpaceValues.CIRCLECROSS
        )
        var mGameTokenCard = intArrayOf(
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY,
            BoardSpaceValues.EMPTY
        )
        private var mSxy = 0
        var prizeLocation = -1
        private val mPrizeXBoardLocationArray = intArrayOf(0, 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1, 2, 3, 4)
        private val mPrizeYBoardLocationArray = intArrayOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4)
        private var mPrizeXBoardLocation = 0
        private var mPrizeYBoardLocation = 0
        private var HUMAN_VS_HUMAN = false
        private var mClientThread: ClientThread? = null
        private var mGameActivity: GameActivity? = null
        private var mPrevSelectedBall = 0 //save value for touch selection
        private var mPrevSelectedCell = 0 //save value for touch selection

        private lateinit var resources: Resources
        private fun writeToLog(filter: String, msg: String) {
            if ("true".equals(resources.getString(R.string.debug), ignoreCase = true)) {
                Log.d(filter, msg)
            }
        }

        var mBmpCrossPlayer1: Bitmap? = null
        var mBmpCrossPlayer2: Bitmap? = null
        var mBmpCirclePlayer1: Bitmap? = null
        var mBmpCirclePlayer2: Bitmap? = null

    }

    init {
        isFocusable = true //necessary for getting the touch events
        requestFocus()
        Companion.resources = resources
        mContext = context
        sharedPreferences
        mBmpPrize = getResBitmap(R.drawable.prize_token)
        mBmpCrossPlayer1 = getResBitmap(R.drawable.lib_crossred)
        setTokenColor(mBmpCrossPlayer1!!, mTokenColor1)
        mBmpCrossPlayer2 = getResBitmap(R.drawable.lib_crossblue)
        setTokenColor(mBmpCrossPlayer2!!, mTokenColor2)
        mBmpCrossCenter = getResBitmap(R.drawable.lib_crossgreen)
        mBmpCirclePlayer1 = getResBitmap(R.drawable.lib_circlered)
        setTokenColor(mBmpCirclePlayer1!!, mTokenColor1)
        mBmpCirclePlayer2 = getResBitmap(R.drawable.lib_circleblue)
        setTokenColor(mBmpCirclePlayer2!!, mTokenColor2)
        mBmpCircleCenter = getResBitmap(R.drawable.lib_circlegreen)
        mBmpCircleCrossPlayer1 = getResBitmap(R.drawable.lib_circlecrossred)
        setTokenColor(mBmpCircleCrossPlayer1!!, mTokenColor1)
        mBmpCircleCrossPlayer2 = getResBitmap(R.drawable.lib_circlecrossblue)
        setTokenColor(mBmpCircleCrossPlayer2!!, mTokenColor2)
        mBmpCircleCrossCenter = getResBitmap(R.drawable.lib_circlecrossgreen)
        mBmpAvailableMove = getResBitmap(R.drawable.allowed_move)
        mBmpTakenMove = getResBitmap(R.drawable.taken_move)
        mSrcRect[0, 0, mBmpCrossPlayer1!!.width - 1] = mBmpCrossPlayer1!!.height - 1
        if (mBmpAvailableMove != null) {
            mTakenRect[0, 0, mBmpAvailableMove.width - 1] = mBmpAvailableMove.height - 1
        }
        mBmpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mLinePaint = Paint()
        mLinePaint.color = -0x100
        mLinePaint.strokeWidth = GRIDLINEWIDTH.toFloat()
        mLinePaint.style = Paint.Style.STROKE
        mWinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mWinPaint.color = -0x10000
        mWinPaint.strokeWidth = 5f // was 10
        mWinPaint.style = Paint.Style.STROKE
        mTextPaint = Paint()
        mTextPaint.color = -0x10000
        mTextPaint.strokeWidth = 1f
        mTextPaint.style = Paint.Style.STROKE
        initalizeGameValues()
        if (isInEditMode) {
            // In edit mode (e.g. in the Eclipse ADT graphical layout editor)
            // we'll use some random data to display the state.
            for (i in data.indices) {
                data[i] = State.fromInt(mRandom.nextInt(3))
            }
        }
        computerMove = 4 //temporary value for determining next token for computer to move 
        mPlayer1TokenChoice = -1
        mPlayer2TokenChoice = -1
        INITIALIZATIONCOMPLETED = false
    }
}