package com.zwy.nas.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_table")
data class UserBean(
    @PrimaryKey
    val id: String,
    val name: String
)

@Entity(tableName = "token_table")
data class TokenBean(
    @PrimaryKey
    val token: String
)

@Entity(tableName = "upload_file")
data class UploadFileBean(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val uri: String,
    val size: Long,
    val superId: String,
    val file: Int,
    var progress: Int,
    val stop: Int,
)

