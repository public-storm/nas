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
    val type: Int,
    var progress: Int,
    val status: Int,
)

@Entity(tableName = "download_file")
data class DownloadFileBean(
    @PrimaryKey
    val id: String,
    val name: String,
    val size: Long,
    val status: Int,
    val type: Int,
    val progress: Int
)

@Entity(tableName = "download_file_history")
data class DownloadHistoryFileBean(
    @PrimaryKey
    val id: String,
    val name: String,
    val size: Long,
    val type: Int,
)

