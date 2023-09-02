package com.zwy.nas.viewModel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwy.nas.Common
import com.zwy.nas.WebSocketClient
import com.zwy.nas.database.AppDatabase
import com.zwy.nas.database.UploadFileBean
import com.zwy.nas.request.Api
import com.zwy.nas.request.SelectFileResponse
import com.zwy.nas.request.reqPath
import com.zwy.nas.task.UploadTask
import com.zwy.nas.util.FileUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.InputStream
import kotlin.math.max

class UploadViewModel(private val database: AppDatabase) : ViewModel() {
    companion object {
        private var instance: UploadViewModel? = null

        fun getInstance(database: AppDatabase?): UploadViewModel {
            if (instance == null && database != null) {
                instance = UploadViewModel(database)
            }
            return instance!!
        }
    }

    private var contentResolver: ContentResolver? = null

    private val uploadFiles = MutableStateFlow<List<UploadFileBean>>(emptyList())

    private var isTask = false

    private var job: Job? = null

    private val splitSize: Long = 1024 * 1024 * 2L

    private val uploadId = mutableStateOf(-1L)

    private val progress = mutableStateOf(0)

    private var globalViewModel: GlobalViewModel? = null
    fun addUploadTask(uri: Uri, superId: String, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uriStr = uri.toString()
                val pair = findPathSize(uri)
                if (pair != null) {
                    val same = database.UploadFileDao().findByName(pair.first, superId)
                    if (same == null) {
                        val suffix = ".${pair.first.substringAfterLast(".")}"
                        val type = FileUtil.findFileType(suffix)
                        database.UploadFileDao()
                            .insertUploadFile(
                                UploadFileBean(
                                    name = pair.first,
                                    uri = uriStr,
                                    size = pair.second,
                                    superId = superId,
                                    type = type,
                                    progress = 0,
                                    status = 0,
                                )
                            )
                        uploadFiles.value = database.UploadFileDao().findUploadFile()
                        openUploadTask(userId, superId)
                    } else {
                        Log.d(Common.MY_TAG, "文件已添加至上传列表")
                    }
                } else {
                    Log.w(Common.MY_TAG, "获取文件信息为空 $uri")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "添加上传文件异常", e)
            }
        }
    }

    private fun findPathSize(uri: Uri): Pair<String, Long>? {
        val cursor = contentResolver?.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                val name = it.getString(nameIndex)
                val size = it.getLong(sizeIndex)
                return Pair(name, size)
            }
        }
        return null
    }

    private fun openUploadTask(userId: String, superId: String) {
        if (!isTask) {
            isTask = true
            UploadTask.startTask(0, 1000) {
                val upFiles = database.UploadFileDao().findByStatusSync(0)
                if (upFiles.isEmpty()) {
                    Log.d(Common.MY_TAG, "关闭文件上传定时任务")
                    UploadTask.cancelTask()
                    isTask = false
                } else {
                    runBlocking {
                        job = launch(Dispatchers.IO) {
                            uploadFile(upFiles[0], userId, superId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun uploadFile(
        uploadFileBean: UploadFileBean,
        userId: String,
        superId: String
    ) {
        val name = uploadFileBean.name
        val supId = uploadFileBean.superId
        val size = uploadFileBean.size
        try {
            val res = Api.get(findToken(database)).checkUpload(name, supId, size)
            if (res.code == "200") {
                val checkExistsResponse = res.data
                when (checkExistsResponse?.status) {
                    0 -> {
                        splitUploadFile(
                            uploadFileBean,
                            checkExistsResponse.chunks,
                            checkExistsResponse.id,
                            userId,
                            superId
                        )
                    }

                    2 -> {
                        Log.d(Common.MY_TAG, "检查文件上传 文件合并中 $res")
                    }

                    else -> {
                        Log.d(Common.MY_TAG, "检查文件上传 文件已上传 $res")
                    }
                }
            } else {
                Log.w(Common.MY_TAG, "检查文件上传失败 $res")
            }
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "文件上传异常", e)
        }
    }

    private suspend fun findToken(database: AppDatabase): String {
        return database.tokenDao().findToken() ?: ""
    }

    private suspend fun splitUploadFile(
        uploadFileBean: UploadFileBean,
        chunks: Set<Int>,
        id: String,
        userId: String,
        superId: String
    ) {
        val uri = Uri.parse(uploadFileBean.uri)
        if (uploadFileBean.size <= splitSize) {
            contentResolver?.openInputStream(uri)?.use {
                singleUpload(uploadFileBean, it, id, superId)
            }
        } else {
            createWebSocket(userId)
            contentResolver?.openInputStream(uri)?.use {
                upload(uploadFileBean, it, chunks, id, userId, superId)
            }
        }
    }

    private fun createWebSocket(userId: String) {
        Log.d(Common.MY_TAG, "创建webSocket连接 $userId")
        val webSocketUrl = "ws://$reqPath/webSocket/${userId}"
        val webSocketListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                findLocalUploadFile()
            }
        }
        WebSocketClient.connect(webSocketUrl, webSocketListener)
    }

    fun findLocalUploadFile() {
        viewModelScope.launch(Dispatchers.IO) {
            uploadFiles.value = database.UploadFileDao().findUploadFile()
        }
    }

    private suspend fun singleUpload(
        uploadFileBean: UploadFileBean,
        inputStream: InputStream,
        id: String,
        superId: String
    ) {
        try {
            val filenameRequestBody =
                RequestBody.create(MediaType.parse("text/plain"), uploadFileBean.name)
            val superIdRequestBody =
                RequestBody.create(MediaType.parse("text/plain"), superId)
            val idRequestBody =
                RequestBody.create(MediaType.parse("text/plain"), id)
            val requestBody = RequestBody.create(
                MediaType.parse("application/octet-stream"),
                inputStream.readBytes()
            )
            val filePart = MultipartBody.Part.createFormData(
                "file",
                uploadFileBean.name,
                requestBody
            )
            val res = Api.get(findToken(database))
                .upload(filenameRequestBody, superIdRequestBody, idRequestBody, filePart)
            if (res.code == "200") {
                findServerFiles(superId)
                delLocalUploadFile(uploadFileBean.id)
            } else {
                Log.w(Common.MY_TAG, "单文件上传失败 $res")
            }
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "单片文件上传异常", e)
        }
    }

    private fun findServerFiles(superId: String) {
        if (globalViewModel == null) {
            globalViewModel = GlobalViewModel.getInstance(null)
        }
        globalViewModel!!.findServerFiles()
    }

    private fun delLocalUploadFile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.UploadFileDao().delUploadFile(id)
            uploadFiles.value = database.UploadFileDao().findUploadFile()
        }
    }


    private suspend fun upload(
        uploadFileBean: UploadFileBean,
        inputStream: InputStream,
        chunks: Set<Int>,
        id: String,
        userId: String,
        superId: String
    ) {
        try {
            uploadId.value = uploadFileBean.id
            progress.value = 0
            val buffer = ByteArray(splitSize.toInt())
            var bytesRead: Int
            var chunkNumber = 1
            val totalSplit = kotlin.math.ceil(uploadFileBean.size.toDouble() / splitSize).toInt()
            while (true) {
                val p = findProgress(chunkNumber, totalSplit)
                if (chunkNumber in chunks) {
                    withContext(Dispatchers.IO) {
                        inputStream.skip(splitSize)
                    }
                } else {
                    bytesRead = withContext(Dispatchers.IO) {
                        inputStream.read(buffer)
                    }
                    if (bytesRead < 0) {
                        break
                    }
                    val requestBody =
                        findUploadBody(chunkNumber, uploadFileBean, totalSplit, id, buffer, superId)
                    val res = Api.get(findToken(database)).uploadSplit(
                        requestBody.chunkNumberBody,
                        requestBody.totalSizeBody,
                        requestBody.totalChunksBody,
                        requestBody.filenameBody,
                        requestBody.superIdBody,
                        requestBody.webIdBody,
                        requestBody.idBody,
                        requestBody.filePart
                    )
                    if (res.code == "200") {
                        progress.value = p
                        Log.d(Common.MY_TAG, "${uploadFileBean.name} ${progress.value}%")
                    } else {
                        Log.w(
                            Common.MY_TAG,
                            "分片上传文件失败 $res chunkNumber $chunkNumber thread ${Thread.currentThread().name}"
                        )
                    }
                }
                chunkNumber++
            }
            if (database.UploadFileDao().findById(uploadFileBean.id).status == 0) {
                createWebSocket(userId)
                delLocalUploadFile(uploadFileBean.id)
            }
        } catch (jobE: CancellationException) {
            Log.d(Common.MY_TAG, "任务取消")
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "分片文件上传异常", e)
        }
    }

    private fun findProgress(numerator: Int, denominator: Int): Int {
        // 将 Int 转换为 Double，然后计算百分比
        return ((numerator.toDouble() / denominator.toDouble()) * 100).toInt()
    }

    private fun findUploadBody(
        chunkNumber: Int,
        uploadFileBean: UploadFileBean,
        totalSplit: Int,
        id: String,
        buffer: ByteArray,
        superId: String
    ): UploadBody {
        val chunkNumberBody =
            RequestBody.create(
                MediaType.parse("text/plain"),
                chunkNumber.toString()
            )
        val totalSizeBody =
            RequestBody.create(
                MediaType.parse("text/plain"),
                uploadFileBean.size.toString()
            )
        val totalChunksBody =
            RequestBody.create(MediaType.parse("text/plain"), totalSplit.toString())
        val filenameBody =
            RequestBody.create(MediaType.parse("text/plain"), uploadFileBean.name)
        val superIdBody =
            RequestBody.create(MediaType.parse("text/plain"), superId)
        val idBody =
            RequestBody.create(MediaType.parse("text/plain"), id)
        val requestBody = RequestBody.create(
            MediaType.parse("application/octet-stream"),
            buffer
        )
        val filePart = MultipartBody.Part.createFormData(
            "file",
            uploadFileBean.name,
            requestBody
        )
        val webIdBody = RequestBody.create(
            MediaType.parse(
                "text/plain"
            ), uploadFileBean.id.toString()
        )
        return UploadBody(
            chunkNumberBody,
            totalSizeBody,
            totalChunksBody,
            filenameBody,
            superIdBody,
            idBody,
            requestBody,
            webIdBody,
            filePart
        )
    }

    fun stopFile(id: Long, status: Int, userId: String, superId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (status == 0) {
                database.UploadFileDao().updateStatus(id, 0)
                uploadFiles.value = database.UploadFileDao().findUploadFile()
                openUploadTask(userId, superId)
            } else if (status == -1) {
                database.UploadFileDao().updateStatusAndProgress(id, -1, progress.value)
                uploadFiles.value = database.UploadFileDao().findUploadFile()
                job?.cancel()
            }
        }
    }

    fun cancelJob(id: Long) {
        job?.cancel()
        delLocalUploadFile(id)
    }

    fun viewFindProgress(uploadFileBean: UploadFileBean): Int {
        if (uploadFileBean.id == uploadId.value) {
            return max(progress.value, uploadFileBean.progress)
        }
        return uploadFileBean.progress
    }

    data class UploadBody(
        val chunkNumberBody: RequestBody,
        val totalSizeBody: RequestBody,
        val totalChunksBody: RequestBody,
        val filenameBody: RequestBody,
        val superIdBody: RequestBody,
        val idBody: RequestBody,
        val requestBody: RequestBody,
        val webIdBody: RequestBody,
        val filePart: MultipartBody.Part,
    )
}