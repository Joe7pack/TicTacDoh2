package com.guzzardo.tictacdoh2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.guzzardo.tictacdoh2.GameView.ScreenOrientation

class ColorBall(context: android.content.Context, drawable: Int, pointLandscape: android.graphics.Point, pointPortrait: android.graphics.Point, displayMode: Int, type: Int, color: Int) {
    var bitmap : android.graphics.Bitmap // the image of the ball
    private var coordX : Int = 0 // the current x coordinate at the canvas = 0
    private var coordY : Int = 0 // the current y coordinate at the canvas = 0
    private val startingLandscapeCoordX : Int // the starting x coordinate at the canvas
    private val startingLandscapeCoordY : Int // the starting y coordinate at the canvas
    private val startingPortraitCoordX : Int // the starting x coordinate at the canvas
    private val startingPortraitCoordY : Int // the starting y coordinate at the canvas
    private val mDisplayMode : Int //portrait or landscape
    val iD : Int// gives every ball its own id
    var type : Int // circle, cross or circleCross token
    var isDisabled = false //indicates ball can no longer be moved (finalized placement on board) = false

    fun updateBall(type: Int, bitmap: android.graphics.Bitmap) {
        this.type = type
        this.bitmap = bitmap
        resetPosition(mDisplayMode)
        isDisabled = false
    }

    fun getCoordX(): Int {
        return coordX
    }

    fun getCoordY(): Int {
        return coordY
    }

    fun setCoordX(newValue: Int) {
        coordX = newValue
    }

    fun setCoordY(newValue: Int) {
        coordY = newValue
    }

    fun getMaxBalls(): Int {
        return MAXBALLS
    }

    fun resetPosition(displayMode: Int) {
        if (displayMode == ScreenOrientation.LANDSCAPE) {
            coordX = startingLandscapeCoordX
            coordY = startingLandscapeCoordY
        } else {
            coordX = startingPortraitCoordX
            coordY = startingPortraitCoordY
        }
    }

    val rect: Rect
        get() = Rect(coordX, coordY, 0, 0)

    companion object {
        @kotlin.jvm.JvmField
        var MAXBALLS: Int = 8
        var count = 0
        //const val MAXBALLS = 8
        const val CIRCLE = 0
        const val CROSS = 1
        const val CIRCLECROSS = 2
        @JvmStatic
		fun setTokenColor(img: Bitmap, newColor: Int) {
            val width = img.width
            val height = img.height
            val pixels = IntArray(width * height)
            img.getPixels(pixels, 0, width, 0, 0, width, height)
            for (x in pixels.indices) {
                if (pixels[x] != 0) {
                    pixels[x] = newColor
                }
            }
            img.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    init {
        val bitmapOptions = BitmapFactory.Options()
        bitmapOptions.inMutable = true
        bitmap = BitmapFactory.decodeResource(context.resources, drawable, bitmapOptions)
        setTokenColor(bitmap, color)
        iD = count
        count++
        if (count > MAXBALLS - 1)
            count = 0
        if (displayMode == ScreenOrientation.LANDSCAPE) {
            coordX = pointLandscape.x
            coordY = pointLandscape.y
        } else {
            coordX = pointPortrait.x
            coordY= pointPortrait.y
        }
        startingLandscapeCoordX = pointLandscape.x
        startingLandscapeCoordY = pointLandscape.y
        startingPortraitCoordX = pointPortrait.x
        startingPortraitCoordY = pointPortrait.y
        this.type = type
        mDisplayMode = displayMode
    }
}