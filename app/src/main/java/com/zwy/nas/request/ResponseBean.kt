package com.zwy.nas.request

import com.google.gson.annotations.SerializedName

data class UserModel(@SerializedName("id") val id: String, @SerializedName("name") val name: String)
data class SelectFileResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("createTime") val createTime: String,
    @SerializedName("file") val file: Int,
    @SerializedName("favorite") val favorite: Int,
    @SerializedName("superId") val superId: String,
    @SerializedName("status") val status: Int,
)

data class CheckExistsResponse(
    @SerializedName("status")
    val status: Int,
    @SerializedName("chunks")
    val chunks: Set<Int>,
    @SerializedName("id")
    val id: String
)

data class FindHistoryFileResponse(
    @SerializedName("name")
    val name: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("createTime")
    val createTime: String,
    @SerializedName("fileType")
    val fileType:Int
)