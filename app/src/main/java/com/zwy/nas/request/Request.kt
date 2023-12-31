package com.zwy.nas.request

import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Streaming

//const val reqPath = "192.168.0.3:8777"
//const val reqPath = "192.168.0.2:8777"
const val reqPath = "10.23.100.186:8777"
//const val reqPath= "192.168.43.6:8777"
//const val reqPath= "192.168.137.1:8777"
//const val reqPath = "192.168.110.103:8777"
//const val reqPath= "192.168.110.103:8777"

data class ResponseResult<T>(
    @SerializedName("code") var code: String = "",
    @SerializedName("mse") var msg: String? = "",
    @SerializedName("data") var data: T? = null
)


interface IApi {
    @GET("/user")
    suspend fun findUser(): ResponseResult<UserModel>

    @POST("/login")
    suspend fun login(@Body loginRequest: LoginRequest): ResponseResult<String>

    @POST("/file/directory")
    suspend fun createDirectory(@Body req: CreateDirectoryRequest): ResponseResult<Unit>

    @GET("/file/directory")
    suspend fun findFiles(
        @Query("superId") superId: String
    ): ResponseResult<List<SelectFileResponse>>

    @DELETE("/file/directory")
    suspend fun delDirectory(@Query("id") id: String): ResponseResult<Unit>

    @PUT("/file/rename")
    suspend fun renameFile(
        @Query("id") id: String,
        @Query("name") name: String
    ): ResponseResult<Unit>

    @GET("/file/upload/check")
    suspend fun checkUpload(
        @Query("filename") filename: String,
        @Query("superId") superId: String,
        @Query("fileSize") fileSize: Long
    ): ResponseResult<CheckExistsResponse>

    @Multipart
    @POST("/file/upload")
    suspend fun upload(
        @Part("filename") filename: RequestBody,
        @Part("superId") superId: RequestBody,
        @Part("id") id: RequestBody,
        @Part file: MultipartBody.Part
    ): ResponseResult<Unit>

    @Multipart
    @POST("/file/upload/split")
    suspend fun uploadSplit(
        @Part("chunkNumber") chunkNumber: RequestBody,
        @Part("totalSize") totalSize: RequestBody,
        @Part("totalChunks") totalChunks: RequestBody,
        @Part("filename") filename: RequestBody,
        @Part("superId") superId: RequestBody,
        @Part("webId") webId: RequestBody,
        @Part("id") id: RequestBody,
        @Part file: MultipartBody.Part
    ): ResponseResult<Unit>

    @GET("/file/history")
    suspend fun findHistoryFile(): ResponseResult<List<FindHistoryFileResponse>>

    @DELETE("/file/history")
    suspend fun delAllHistoryFile(): ResponseResult<Unit>

    @GET("/file/download/chunk")
    suspend fun findDownloadChunk(@Query("id") id: String): ResponseResult<FindDownloadChunkResponse>

    @GET("/file/download")
    suspend fun download(@Query("id") id: String, @Query("index") index: Long): ResponseBody

    @Streaming
    @GET("/preview")
    fun preview(@Query("index") index: Int): Call<ResponseBody>
}


class HeaderInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val modifiedRequest = originalRequest.newBuilder()
            .header("token", token)
            // 添加其他需要的 Header 头部信息
            .build()
        return chain.proceed(modifiedRequest)
    }
}

object Api {
    private val httpClient = OkHttpClient.Builder()

    fun get(token: String): IApi {
        httpClient.interceptors().clear() // 清除之前的拦截器
        httpClient.addInterceptor(HeaderInterceptor(token)) // 添加新的拦截器
        val retrofit = Retrofit.Builder()
            .baseUrl("http://$reqPath")
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient.build())
            .build()
        return retrofit.create(IApi::class.java)
    }
}


