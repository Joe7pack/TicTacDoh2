package com.guzzardo.tictacdoh2

import android.os.Parcel
import kotlinx.parcelize.parcelableCreator

interface ActivityParcelableCreator {
    fun activityFromParcel(parcel: Parcel): ActivityParser {
        return parcelableCreator<ActivityParser>().createFromParcel(parcel)
    }

}