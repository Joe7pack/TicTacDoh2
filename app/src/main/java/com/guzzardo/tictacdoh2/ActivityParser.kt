package com.guzzardo.tictacdoh2

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.parcelableCreator

@Parcelize
data class ActivityParser(var data: Int): Parcelable {

    override fun describeContents() = Parcelable.CONTENTS_FILE_DESCRIPTOR

    private companion object : Parceler<ActivityParser> {
        override fun ActivityParser.write(parcel: Parcel, flags: Int) {
            // Custom write implementation
        }

        override fun create(parcel: Parcel): ActivityParser {
            // Custom read implementation
            return TODO("Provide the return value")
        }

        /*
        override fun newArray(size: Int): Array<ActivityParser> {
            return arrayOfNulls<ActivityParser>(size) //as Array<ActivityParser>
        }
        */

    }


}