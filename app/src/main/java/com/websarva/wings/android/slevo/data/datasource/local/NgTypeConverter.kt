package com.websarva.wings.android.slevo.data.datasource.local

import androidx.room.TypeConverter
import com.websarva.wings.android.slevo.data.model.NgType

class NgTypeConverter {
    @TypeConverter
    fun fromNgType(type: NgType): String = type.name

    @TypeConverter
    fun toNgType(value: String): NgType = NgType.valueOf(value)
}
