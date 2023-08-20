package com.zwy.nas.request

data class LoginRequest(val userName: String, val password: String)
data class SelectFileRequest(val superId: String, val name: String, val favorite: Int)
data class CreateDirectoryRequest(val name: String,val superId: String)