package com.guzzardo.tictacdoh2

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class User(val firstName: String, val lastName: String, val age: Int): Parcelable