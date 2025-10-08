/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.guzzardo.tictacdoh2.WillyShmoApplication.UserPreferences
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mPlayer1Name
import com.guzzardo.tictacdoh2.WillyShmoApplication.Companion.mPlayer2Name

class SettingsDialogs : Activity(), ToastMessage {
    private var mButtonPlayer1: Button? = null
    private var mButtonPlayer2: Button? = null
    private var mSeekBar: SeekBar? = null
    private var mTokenSize = 0
    private var mTokenColor = 0
    private var mTokenColor1 = 0
    private var mTokenColor2 = 0
    private var mAdView: AdView? = null

    /**
     * Initialization of the Activity after it is first created.  Must at least
     * call [android.app.Activity.setContentView] to
     * describe what is to be displayed in the screen.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Companion.resources = resources
        setContentView(R.layout.settings_dialog)
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        mPlayer1Name = settings.getString(GameActivity.PLAYER1_NAME, "").toString()
        if (mPlayer1Name.trim().length.equals(0))
            mPlayer1Name = getString(R.string.player_1)
        mPlayer2Name = settings.getString(GameActivity.PLAYER2_NAME, "").toString()
        if (mPlayer2Name.trim().length.equals(0))
            mPlayer2Name = getString(R.string.player_2)
        mMoveModeTouch = settings.getBoolean(GameActivity.MOVE_MODE, false)
        mSoundMode = settings.getBoolean(GameActivity.SOUND_MODE, true)
        mTokenSize = settings.getInt(GameActivity.TOKEN_SIZE, 50)
        mTokenColor1 = settings.getInt(GameActivity.TOKEN_COLOR_1, Color.RED)
        mTokenColor2 = settings.getInt(GameActivity.TOKEN_COLOR_2, Color.BLUE)
        mDistanceUnitOfMeasure = settings.getString(GameActivity.DISTANCE_UNIT_OF_MEASURE, "M").toString()
        mMoveModeChecked = if (!mMoveModeTouch) 0 else 1
        mSoundModeChecked = if (mSoundMode) 0 else 1
        mButtonPlayer1 = findViewById<View>(R.id.text_entry_button_player1_name) as Button
        val nameValuePlayer1 = getString(R.string.alert_dialog_text_entry_player1_name) + " " + mPlayer1Name
        mButtonPlayer1!!.text = nameValuePlayer1
        mButtonPlayer2 = findViewById<View>(R.id.text_entry_button_player2_name) as Button
        val nameValuePlayer2 = getString(R.string.alert_dialog_text_entry_player2_name) + " " + mPlayer2Name
        mButtonPlayer2!!.text = nameValuePlayer2

        /* Display a text message with yes/no buttons and handle each message as well as the cancel action */
        val twoButtonsTitle = findViewById<View>(R.id.reset_scores) as Button
        twoButtonsTitle.setOnClickListener {
            val resetScoresDialog = showResetScoresDialog()
            resetScoresDialog.show()
        }

        /* Display a text entry dialog for entry of player 1 name */
        mButtonPlayer1!!.setOnClickListener {
            showPlayerNameDialog(1)
        }

        /* Display a text entry dialog for entry of player 2 name */
        mButtonPlayer2!!.setOnClickListener {
            showPlayerNameDialog(2)
        }

        /* Display a radio button group */
        var radioButton = findViewById<View>(R.id.move_mode) as Button
        radioButton.setOnClickListener {
            val moveModeDialog = showMoveModeDialog()
            moveModeDialog.show()
        }

        /* Display a radio button group */
        radioButton = findViewById<View>(R.id.sound_mode) as Button
        radioButton.setOnClickListener {
            val soundModeDialog = showSoundModeDialog()
            soundModeDialog.show()
        }

        /* Display a radio button group */
        radioButton = findViewById<View>(R.id.distance_unit_of_measure) as Button
        radioButton.setOnClickListener {
            val distanceUnitOfMeasureDialog = showDistanceUnitOfMeasureDialog()
            distanceUnitOfMeasureDialog.show()
        }

        val tokenSizeTitle = findViewById<View>(R.id.token_size) as Button
        tokenSizeTitle.setOnClickListener {
            val tokenSizeDialog = showTokenSizeDialog()
            tokenSizeDialog.show()
        }
        val tokenColorTitle = findViewById<View>(R.id.token_color_1) as Button
        tokenColorTitle.setOnClickListener {
            val tokenColorDialog = showTokenColorDialog(1)
            tokenColorDialog.show()
        }
        val tokenColorTitle2 = findViewById<View>(R.id.token_color_2) as Button
        tokenColorTitle2.setOnClickListener {
            val tokenColorDialog2 = showTokenColorDialog(2)
            tokenColorDialog2.show()
        }
        mAdView = findViewById<View>(R.id.ad_settings) as AdView
        val adRequest = AdRequest.Builder().build()
        mAdView!!.loadAd(adRequest)
    }

    private fun showMoveModeDialog(): AlertDialog {
        return AlertDialog.Builder(this@SettingsDialogs)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(R.string.move_mode)
            .setSingleChoiceItems(R.array.select_move_mode, mMoveModeChecked) { _, whichButton -> setMoveModeSelection(whichButton) }
            .setPositiveButton(R.string.ok) { _, _ -> setMoveMode() }
            .setNegativeButton(R.string.alert_dialog_cancel) { dialog, _ -> dialog.cancel() }
            .create()
    }

    private fun showSoundModeDialog(): AlertDialog {
        return AlertDialog.Builder(this@SettingsDialogs)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(R.string.sound_mode)
            .setSingleChoiceItems(R.array.select_sound_mode, mSoundModeChecked) { _, whichButton -> setSoundModeSelection(whichButton) }
            .setPositiveButton(R.string.ok) { _, _ -> setSoundMode() }
            .setNegativeButton(R.string.alert_dialog_cancel) { dialog, _ -> dialog.cancel() }
            .create()
    }

    private fun showDistanceUnitOfMeasureDialog(): AlertDialog {
        mDistanceMeasureChecked = if (mDistanceUnitOfMeasure == "M") 0 else 1
        return AlertDialog.Builder(this@SettingsDialogs)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(R.string.distance_unit_of_measure)
            .setSingleChoiceItems(R.array.select_distance_unit_of_measure, mDistanceMeasureChecked) { _, whichButton -> setDistanceUnitOfMeasureSelection(whichButton) }
            .setPositiveButton(R.string.ok) { _, _ -> saveDistanceUnitOfMeasure() }
            .setNegativeButton(R.string.alert_dialog_cancel) { dialog, _ -> dialog.cancel() }
            .create()
    }

    private fun showResetScoresDialog(): AlertDialog {
        return AlertDialog.Builder(this@SettingsDialogs)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(R.string.alert_dialog_reset_scores)
            .setPositiveButton(R.string.ok) { _, _ -> /* User clicked OK so do some stuff */
                resetScores()
            }
            .setNegativeButton(R.string.alert_dialog_cancel) { _, _ -> /* User clicked Cancel so do some stuff */ }
            .create()
    }

    private fun showPlayerNameDialog(playerId: Int) {
        // This example shows how to add a custom layout to an AlertDialog
        val playerCheckString = getString(R.string.player_check_string)
        var titleId = R.string.alert_dialog_text_entry_player1_name
        if (playerId == 2) {
            titleId = R.string.alert_dialog_text_entry_player2_name
        }
        val factory = LayoutInflater.from(this)
        val textEntryViewPlayer = factory.inflate(R.layout.name_dialog_text_entry, null)
        AlertDialog.Builder(this@SettingsDialogs)
            .setIcon(R.drawable.willy_shmo_small_icon)
            .setTitle(titleId)
            .setView(textEntryViewPlayer)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> /* User clicked OK so do some stuff */
                val userName = textEntryViewPlayer.findViewById<View>(R.id.username_edit) as EditText
                val userNameText = userName.text
                val userNameLength = if (userNameText.length > 15) 15 else userNameText.length
                val userNameChars = CharArray(userNameLength)
                userNameText.getChars(0, userNameLength, userNameChars, 0)
                val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
                val editor = settings.edit()
                val intent = Intent(applicationContext, SettingsDialogs::class.java)
                if (playerId == 1) {
                    val player1Name = String(userNameChars)
                    if (player1Name.trim().length.equals(0) or player1Name.trim().lowercase().contains(playerCheckString)) {
                        sendToastMessage(getString(R.string.enter_player1_name))
                    } else {
                        intent.putExtra(GameActivity.PLAYER1_ID, 0)
                        intent.putExtra(GameActivity.PLAYER1_NAME, player1Name)
                        editor.putString(GameActivity.PLAYER1_NAME, player1Name)
                        mPlayer1Name = player1Name
                        val player1TitleAndName = getString(R.string.alert_dialog_text_entry_player1_name) + " $mPlayer1Name"
                        mButtonPlayer1!!.text = player1TitleAndName
                        editor.apply()
                    }
                } else {
                    val player2Name = String(userNameChars)
                    if (player2Name.trim().length.equals(0) or player2Name.trim().lowercase().contains(playerCheckString)) {
                        sendToastMessage(getString(R.string.enter_player2_name))
                    } else {
                        intent.putExtra(GameActivity.PLAYER1_NAME, player2Name)
                        editor.putString(GameActivity.PLAYER2_NAME, player2Name)
                        mPlayer2Name = player2Name
                        val player2TitleAndName = getString(R.string.alert_dialog_text_entry_player2_name) + ": $mPlayer2Name"
                        mButtonPlayer2!!.text = player2TitleAndName //"Player 2 Name: $mPlayer2Name"
                        editor.commit()
                    }
                }
            }
            .setNegativeButton(R.string.alert_dialog_cancel) { _, _ -> /* User clicked cancel so do some stuff */ }
            .show()
    }

    private fun showTokenSizeDialog(): AlertDialog {
        val tokenSizeDialog = AlertDialog.Builder(this)
        val titleId = R.string.alert_dialog_entry_token_size
        val factory = LayoutInflater.from(this)
        val tokenSizeEntryView = factory.inflate(R.layout.token_size_dialog_entry, null)
        tokenSizeDialog.setView(tokenSizeEntryView)
        mSeekBar = tokenSizeEntryView.findViewById<View>(R.id.seekBar) as SeekBar
        mSeekBar!!.progress = mTokenSize
        mSeekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mTokenSize = progress
            }
        })
        tokenSizeDialog.setIcon(R.drawable.willy_shmo_small_icon)
        tokenSizeDialog.setTitle(titleId)
        tokenSizeDialog.setCancelable(false)
        tokenSizeDialog.setPositiveButton(R.string.ok) { _, _ -> /* User clicked OK so do some stuff */
            setTokenSize()
            val intent = Intent(applicationContext, SettingsDialogs::class.java)
            startActivityForResult(intent, 1)
            finish()
        }
        tokenSizeDialog.setNegativeButton(R.string.alert_dialog_cancel) { _, _ -> /* User clicked cancel so do some stuff */ }
        return tokenSizeDialog.create()
    }

    fun setTokenColorFromDialog(newTokenColor: Int) {
        mTokenColor = newTokenColor
    }

    private fun showTokenColorDialog(playerNumber: Int): AlertDialog {
        val tokenColorDialog = AlertDialog.Builder(this)
        val titleId =
            if (playerNumber == 1) R.string.alert_dialog_token_color_1 else R.string.alert_dialog_token_color_2
        val newColor = if (playerNumber == 1) mTokenColor1 else mTokenColor2
        val tokenColorPickerView = TokenColorPickerView(this, this@SettingsDialogs, newColor)
        tokenColorDialog.setView(tokenColorPickerView)
        tokenColorDialog.setIcon(R.drawable.willy_shmo_small_icon)
        tokenColorDialog.setTitle(titleId)
        tokenColorDialog.setCancelable(false)
        tokenColorDialog.setPositiveButton(R.string.ok) { _, _ -> /* User clicked OK so do some stuff */
            setTokenColor(playerNumber)
            val intent = Intent(applicationContext, SettingsDialogs::class.java)
            startActivityForResult(intent, 1)
            finish()
        }
        tokenColorDialog.setNegativeButton(R.string.alert_dialog_cancel) { _, _ -> /* User clicked cancel so do some stuff */ }
        return tokenColorDialog.create()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // We need an Editor object to make preference changes.
        // All objects are from android.context.Context
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        settings.edit {
            putString(GameActivity.PLAYER1_NAME, mPlayer1Name)
            putString(GameActivity.PLAYER2_NAME, mPlayer2Name)
            putBoolean(GameActivity.MOVE_MODE, mMoveModeTouch)
            putBoolean(GameActivity.SOUND_MODE, mSoundMode)
            putInt(GameActivity.TOKEN_SIZE, mTokenSize)
            putInt(GameActivity.TOKEN_COLOR_1, mTokenColor1)
            putInt(GameActivity.TOKEN_COLOR_2, mTokenColor2)
            putString(GameActivity.DISTANCE_UNIT_OF_MEASURE, mDistanceUnitOfMeasure)
            apply()
        }
        writeToLog("SettingsDialog", "onStop() called mPlayer1Name: $mPlayer1Name")
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        super.onSaveInstanceState(savedInstanceState)
        writeToLog("SettingsDialog", "onSaveInstanceState() called mPlayer1Name: $mPlayer1Name")
        savedInstanceState.putString("gDialog_player1_name", mPlayer1Name)
        savedInstanceState.putString("gDialog_player2_name", mPlayer2Name)
        savedInstanceState.putBoolean("gDialog_move_mode", mMoveModeTouch)
        savedInstanceState.putBoolean("gDialog_sound_mode", mSoundMode)
        savedInstanceState.putInt("gDialog_token_size", mTokenSize)
        savedInstanceState.putInt("gDialog_player1_token_color", mTokenColor1)
        savedInstanceState.putInt("gDialog_player2_token_color", mTokenColor2)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore UI state from the savedInstanceState.
        // This bundle has also been passed to onCreate.
        mPlayer1Name = savedInstanceState.getString("gDialog_player1_name").toString()
        mPlayer2Name = savedInstanceState.getString("gDialog_player2_name").toString()
        mMoveModeTouch = savedInstanceState.getBoolean("gDialog_move_mode")
        mSoundMode = savedInstanceState.getBoolean("gDialog_sound_mode")
        mTokenSize = savedInstanceState.getInt("gDialog_token_size")
        mTokenColor1 = savedInstanceState.getInt("gDialog_player1_token_color")
        mTokenColor2 = savedInstanceState.getInt("gDialog_player2_token_color")
        writeToLog("SettingsDialog", "onRestoreInstanceState() called mPlayer1Name: $mPlayer1Name")
    }

    private fun resetScores() {
        // We need an Editor object to make preference changes.
        // All objects are from android.context.Context
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        settings.edit {
            putInt(GameActivity.PLAYER1_SCORE, 0)
            putInt(GameActivity.PLAYER2_SCORE, 0)
            putInt(GameActivity.WILLY_SCORE, 0)
            apply()
        }
    }

    private fun setMoveModeSelection(moveMode: Int) {
        mMoveModeTouch = moveMode != 0
    }

    private fun setMoveMode() {
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        settings.edit {
            putBoolean(GameActivity.MOVE_MODE, mMoveModeTouch)
            apply()
        }
    }

    private fun setTokenSize() {
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        settings.edit {
            putInt(GameActivity.TOKEN_SIZE, mTokenSize)
            apply()
        }
    }

    private fun setTokenColor(playerNumber: Int) {
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        settings.edit {
            if (playerNumber == 1) {
                putInt(GameActivity.TOKEN_COLOR_1, mTokenColor)
            } else {
                putInt(GameActivity.TOKEN_COLOR_2, mTokenColor)
            }
            apply()
        }
    }

    private fun setSoundModeSelection(soundMode: Int) {
        mSoundMode = soundMode == 0
    }

    private fun setDistanceUnitOfMeasureSelection(distanceUnitOfMeasure: Int) {
        if (distanceUnitOfMeasure == 0)
            mDistanceUnitOfMeasure = "M"
        else
            mDistanceUnitOfMeasure = "K"
    }

    private fun setSoundMode() {
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        settings.edit {
            putBoolean(GameActivity.SOUND_MODE, mSoundMode)
            apply()
        }
    }

    private fun saveDistanceUnitOfMeasure() {
        val settings = getSharedPreferences(UserPreferences.PREFS_NAME, MODE_PRIVATE)
        settings.edit {
            putString(GameActivity.DISTANCE_UNIT_OF_MEASURE, mDistanceUnitOfMeasure)
            apply()
        }
    }

    override fun sendToastMessage(message: String?) {
        writeToLog("SettingsDialogs", "sendToastMessage message: $message")
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun writeToLog(filter: String, msg: String) {
        if ("true".equals(resources.getString(R.string.debug), ignoreCase = true)) {
            Log.d(filter, msg)
        }
    }

    companion object {
        private lateinit var resources: Resources
        private var mMoveModeTouch = false // false = drag move mode; true = touch move mode
        private var mMoveModeChecked = 0 // 0 = drag move mode; 1 = touch move mode
        private var mSoundModeChecked = 0 // 0 = sound on; 1 = sound off
        private var mSoundMode = true // false = no sound; true = sound
        private var mDistanceMeasureChecked = 0 // 0 = miles; 1 = kilometers
        private var mDistanceUnitOfMeasure = "M"
    }
}