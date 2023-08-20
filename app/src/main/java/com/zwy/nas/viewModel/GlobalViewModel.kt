package com.zwy.nas.viewModel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwy.nas.Common
import com.zwy.nas.WebSocketClient
import com.zwy.nas.database.AppDatabase
import com.zwy.nas.database.TokenBean
import com.zwy.nas.database.UploadFileBean
import com.zwy.nas.request.Api
import com.zwy.nas.request.CreateDirectoryRequest
import com.zwy.nas.request.FindHistoryFileResponse
import com.zwy.nas.request.LoginRequest
import com.zwy.nas.request.SelectFileResponse
import com.zwy.nas.request.reqPath
import com.zwy.nas.task.UploadTask
import com.zwy.nas.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

class GlobalViewModel(private val database: AppDatabase) : ViewModel() {
    private val _navigateToLogin = mutableStateOf(false)
    val navigateToLogin: State<Boolean> get() = _navigateToLogin

    private val _userId = mutableStateOf("")
    val userId: State<String> get() = _userId

    private val _userName = mutableStateOf("")
    val userName: State<String> get() = _userName

    private var _loginSuccess = mutableStateOf(false)
    var loginSuccess: State<Boolean> = _loginSuccess

    private val _files = MutableStateFlow<List<SelectFileResponse>>(emptyList())
    val files: StateFlow<List<SelectFileResponse>> get() = _files

    private val _uploadFiles = MutableStateFlow<List<UploadFileBean>>(emptyList())
    val uploadFiles: StateFlow<List<UploadFileBean>> get() = _uploadFiles

    private val _uploadHistoryFiles = MutableStateFlow<List<UploadFileBean>>(emptyList())
    val uploadHistoryFiles: StateFlow<List<UploadFileBean>> get() = _uploadHistoryFiles

    private val _progress = mutableStateOf(0)
    val progress: State<Int> get() = _progress

    private val _uploadId = mutableStateOf(-1L)
    val uploadId: State<Long> get() = _uploadId

    private val _uploadHistory = MutableStateFlow<List<FindHistoryFileResponse>>(emptyList())
    val uploadHistory: StateFlow<List<FindHistoryFileResponse>> get() = _uploadHistory


    var optFileIndex: Int = -1

    var superId: String = "-1"

    private val splitSize: Long = 1024 * 1024 * 2L

    var contentResolver: ContentResolver? = null
    private val threadNum = 4
    var threadPool: CoroutineContext? = null

    private var isTask = false


    companion object {
        private var instance: GlobalViewModel? = null

        fun getInstance(database: AppDatabase?): GlobalViewModel {
            if (instance == null && database != null) {
                instance = GlobalViewModel(database)
            }
            return instance!!
        }
    }

    private suspend fun findToken(database: AppDatabase): String {
        return database.tokenDao().findToken() ?: ""
    }

    fun findUser() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Api.get(findToken(database)).findUser()
                if (res.code == "200") {
                    _userId.value = res.data!!.id
                    _userName.value = res.data!!.name
                } else {
                    _navigateToLogin.value = true
                    Log.w(Common.MY_TAG, "查询用户失败 $res")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "查询用户异常", e)
            }

        }
    }


    fun login(name: String, pwd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Api.get(findToken(database)).login(LoginRequest(name, pwd))
                if (res.code == "200") {
                    _loginSuccess.value = true
                    val tokenDao = database.tokenDao()
                    tokenDao.delAll()
                    tokenDao.insertToken(TokenBean(res.data.toString()))
                } else {
                    Log.w(Common.MY_TAG, "登录失败 $res")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "登录异常", e)
            }

        }
    }

    fun findServerFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Api.get(findToken(database)).findFiles(superId)
                if (res.code == "200") {
                    Log.d(Common.MY_TAG, "findFiles: ${res.data}")
                    _files.value = res.data ?: emptyList()
                } else {
                    Log.w(Common.MY_TAG, "查询文件失败 $res")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "查询文件异常", e)
            }

        }
    }

    fun createServerDirectory(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Api.get(findToken(database))
                    .createDirectory(CreateDirectoryRequest(name, superId))
                if (res.code == "200") {
                    findServerFiles()
                } else {
                    Log.w(Common.MY_TAG, "创建文件夹失败 $res")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "创建文件夹异常", e)
            }
        }
    }

    fun delServerDirectory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = _files.value[optFileIndex].id
                val res = Api.get(findToken(database)).delDirectory(id)
                if (res.code == "200") {
                    findServerFiles()
                } else {
                    Log.w(Common.MY_TAG, "删除文件失败 $res")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "删除文件异常", e)
            }
        }
    }

    fun renameServerFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = _files.value[optFileIndex].id
                val res = Api.get(findToken(database)).renameFile(id, name)
                if (res.code == "200") {
                    findServerFiles()
                } else {
                    Log.w(Common.MY_TAG, "重命名文件失败 $res")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "重命名文件异常", e)
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
            _uploadFiles.value = database.UploadFileDao().findUploadFile()
        }
    }


    fun findServerUploadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Api.get(findToken(database)).findHistoryFile()
                if (res.code == "200") {
                    _uploadHistory.value = res.data ?: emptyList()
                    Log.d(Common.MY_TAG, "findUploadHistory: ${_uploadHistory.value}")
                } else {
                    Log.d(Common.MY_TAG, "查询上传历史失败 $res")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "查询上传历史异常", e)
            }
        }
    }

    fun delServerAllHistoryFile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Api.get(findToken(database)).delAllHistoryFile()
                if (res.code == "200") {
                    findServerUploadHistory()
                } else {
                    Log.d(Common.MY_TAG, "删除上传历史失败")
                }
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "删除上传历史异常", e)
            }
        }
    }

    fun addLocalUploadFile(uri: Uri) {
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
                                    file = type,
                                    progress = 0,
                                    stop = 0,
                                )
                            )
                        _uploadFiles.value = database.UploadFileDao().findUploadFile()
                        if (!isTask) {
                            //开启上传扫描
                            openUploadTask()
                        }
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


    private fun openUploadTask() {
        isTask = true
        UploadTask.startTask(0, 1000) {
            val uploadFiles = database.UploadFileDao().findNoStopSync()
            if (uploadFiles.isNotEmpty()) {
                val uploadFileBean = uploadFiles[0]
                runBlocking {
                    launch(Dispatchers.IO) {
                        uploadFile(uploadFileBean)
                    }
                }
            } else {
                Log.d(Common.MY_TAG, "关闭文件上传定时任务")
                UploadTask.cancelTask()
                isTask = false
            }
        }
    }

    fun delLocalUploadFile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.UploadFileDao().delUploadFile(id)
            _uploadFiles.value = database.UploadFileDao().findUploadFile()
        }
    }


    private suspend fun uploadFile(uploadFileBean: UploadFileBean) {
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
                            checkExistsResponse.id
                        )
                    }

                    2 -> {
                        Log.d(Common.MY_TAG, "检查文件上传 文件合并中 $res")
                        createWebSocket(_userId.value)
                        database.UploadFileDao().delUploadFile(uploadFileBean.id)
                        _uploadFiles.value = database.UploadFileDao().findUploadFile()
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


    private suspend fun splitUploadFile(
        uploadFileBean: UploadFileBean,
        chunks: Set<Int>,
        id: String
    ) {
        val uri = Uri.parse(uploadFileBean.uri)
        if (uploadFileBean.size <= splitSize) {
            contentResolver?.openInputStream(uri)?.use {
                singleUpload(uploadFileBean, it, id)
            }
        } else {
            createWebSocket(_userId.value)
            contentResolver?.openInputStream(uri)?.use {
                upload2(uploadFileBean, it, chunks, id)
            }
        }
    }

    private suspend fun singleUpload(
        uploadFileBean: UploadFileBean,
        inputStream: InputStream,
        id: String
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
                findServerFiles()
                delLocalUploadFile(uploadFileBean.id)
            } else {
                Log.w(Common.MY_TAG, "单文件上传失败 $res")
            }
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "单片文件上传异常", e)
        }
    }

    private fun upload(
        uploadFileBean: UploadFileBean,
        inputStream: InputStream,
        chunks: Set<Int>,
        id: String
    ) {
        try {
            _uploadId.value = uploadFileBean.id
            _progress.value = 0
            val buffer = ByteArray(splitSize.toInt())
            var bytesRead: Int = -1
            var chunkNumber = 1
            val totalSplit = kotlin.math.ceil(uploadFileBean.size.toDouble() / splitSize).toInt()
            var f: UploadFileBean? = null
            while (true) {
                val p = findProgress(chunkNumber, totalSplit)
                runBlocking {
                    launch(Dispatchers.IO) {
                        f = database.UploadFileDao().findById(uploadFileBean.id)
                    }
                }
                if (f?.stop == 1) {
                    Log.d(Common.MY_TAG, "upload: 文件已暂停")
                    runBlocking {
                        launch(Dispatchers.IO) {
                            database.UploadFileDao().keepProgress(uploadFileBean.id, p)
                            _uploadFiles.value = database.UploadFileDao().findUploadFile()
                        }
                    }
                    break
                }
                if (chunkNumber in chunks) {
                    runBlocking {
                        launch(Dispatchers.IO) {
                            inputStream.skip(splitSize)
                        }
                    }
                } else {
                    runBlocking {
                        launch(Dispatchers.IO) {
                            bytesRead = inputStream.read(buffer)
                        }
                    }
                    if (bytesRead < 0) {
                        break
                    }
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
                    runBlocking {
                        launch(Dispatchers.IO) {
                            val res = Api.get(findToken(database)).uploadSplit(
                                chunkNumberBody,
                                totalSizeBody,
                                totalChunksBody,
                                filenameBody,
                                superIdBody,
                                webIdBody,
                                idBody,
                                filePart
                            )
                            if (res.code == "200") {
                                Log.d(
                                    Common.MY_TAG,
                                    "分片上传文件成功 $p thread ${Thread.currentThread().name}"
                                )
                                _progress.value = p
                            } else {
                                Log.w(
                                    Common.MY_TAG,
                                    "分片上传文件失败 $res chunkNumber $chunkNumber thread ${Thread.currentThread().name}"
                                )
                            }
                        }
                    }
                }
                chunkNumber++
            }
            delLocalUploadFile(uploadFileBean.id)
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "分片文件上传异常", e)
        }
    }

    private fun upload2(
        uploadFileBean: UploadFileBean,
        inputStream: InputStream,
        chunks: Set<Int>,
        id: String
    ) {
        try {
            _uploadId.value = uploadFileBean.id
            _progress.value = 0
            val buffer = ByteArray(splitSize.toInt())
            var bytesRead: Int = -1
            var chunkNumber = 1
            var stopNum = 0
            val mutex = Mutex()
            val totalSplit = kotlin.math.ceil(uploadFileBean.size.toDouble() / splitSize).toInt()
            runBlocking(Dispatchers.IO) {
                while (true) {
                    if (chunkNumber in chunks) {
                        inputStream.skip(splitSize)
                    } else {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead < 0) {
                            break
                        }
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
                        launch(Dispatchers.IO) {
                            val f = database.UploadFileDao().findById(uploadFileBean.id)
                            if (f.stop == 1) {
                                mutex.withLock {
                                    Log.d(
                                        Common.MY_TAG,
                                        "upload: 文件已暂停 thread ${Thread.currentThread().name}"
                                    )
                                    val p = findProgress(chunkNumber, totalSplit)
                                    if (stopNum < p) {
                                        database.UploadFileDao().keepProgress(
                                            uploadFileBean.id,
                                            findProgress(chunkNumber, totalSplit)
                                        )
                                        stopNum = p
                                    }
                                }
                            } else {
                                val res = Api.get(findToken(database)).uploadSplit(
                                    chunkNumberBody,
                                    totalSizeBody,
                                    totalChunksBody,
                                    filenameBody,
                                    superIdBody,
                                    webIdBody,
                                    idBody,
                                    filePart
                                )
                                if (res.code == "200") {
                                    val p2 = findProgress(chunkNumber, totalSplit)
                                    Log.d(
                                        Common.MY_TAG,
                                        "分片上传文件成功 $p2 thread ${Thread.currentThread().name}"
                                    )
                                    _progress.value = p2
                                    chunkNumber++
                                } else {
                                    Log.w(
                                        Common.MY_TAG,
                                        "分片上传文件失败 $res chunkNumber $chunkNumber thread ${Thread.currentThread().name}"
                                    )
                                }
                            }
                        }
                        _uploadFiles.value = database.UploadFileDao().findUploadFile()
                    }
                }
            }
            delLocalUploadFile(uploadFileBean.id)
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "分片文件上传异常", e)
        }
    }

    fun stopFile(id: Long, stop: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            database.UploadFileDao().stopFile(id, stop)
            _uploadFiles.value = database.UploadFileDao().findUploadFile()
            if (stop == 0 && !isTask) {
                openUploadTask()
            } else {
//                if (_uploadId.value == id) {
//                    database.UploadFileDao().keepProgress(id, _progress.value)
//                    _uploadFiles.value = database.UploadFileDao().findUploadFile()
//                }
            }
        }
    }

    fun viewFindProgress(uploadFileBean: UploadFileBean): Int {
        return if (uploadFileBean.id == _uploadId.value) {
            maxOf(_progress.value, uploadFileBean.progress)
        } else {
            uploadFileBean.progress
        }
    }


    private fun findProgress(numerator: Int, denominator: Int): Int {
        // 将 Int 转换为 Double，然后计算百分比
        return ((numerator.toDouble() / denominator.toDouble()) * 100).toInt()
    }
}