package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
class User {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var fullName: String? = null
    var email: String? = null
    var password: String? = null
}
