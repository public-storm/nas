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

    private val _uploadId = mutableStateOf(-1L)
    val uploadId: State<Long> get() = _uploadId

    private val _uploadHistory = MutableStateFlow<List<FindHistoryFileResponse>>(emptyList())
    val uploadHistory: StateFlow<List<FindHistoryFileResponse>> get() = _uploadHistory

    var optFileIndex: Int = -1

    var superId: String = "-1"

    private val splitSize: Long = 1024 * 1024 * 2L

    var contentResolver: ContentResolver? = null

    private var isTask = false

    private val progress = mutableStateOf(0)

    var job: Job? = null

    fun filePlay(): ByteArray? {
        var data: ByteArray? = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = Api.get(findToken(database)).filePlay(1)
                data = res.bytes()
            } catch (e: Exception) {
                Log.e(Common.MY_TAG, "filePlay: 获取播放文件异常", e)
            }
        }
        return data
    }

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

    /*
    1.本地数据库中文件上传表中添加记录（判断是否已经有记录，判断依据：文件名、上级文件id）
    2.查询本地上传表记录，同步上传ui界面
    3.开启上传任务定时扫描
     */
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
                                    type = type,
                                    progress = 0,
                                    status = 0,
                                )
                            )
                        _uploadFiles.value = database.UploadFileDao().findUploadFile()
                        openUploadTask()
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


    /*
    1.判断但是是否正在任务中，避免重复开启
    2.不在任务中，修改是否在任务中标识，开启定时扫描任务（间隔1s）
    3.查询本地数据库中待上传文件，如果为空则关闭定时扫描任务，修改是否在任务中标识
    4.如果上传文件列表不为空，开启一个协程处理上传任务
     */
    private fun openUploadTask() {
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
                            uploadFile(upFiles[0])
                        }
                    }
                }
            }
        }
    }

    fun delLocalUploadFile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.UploadFileDao().delUploadFile(id)
            _uploadFiles.value = database.UploadFileDao().findUploadFile()
        }
    }


    /*
    1.请求服务器接口，判断文件是否已经保存至服务器
    2.文件已经保存至服务器，输出日志（检查文件上传 文件已上传）
    3.文件已经保存至服务器，但正在合并中，建立与服务器的websocket连接，接收到消息后，查询主文件列表，同步ui界面。删除本地数据库中上传列表，查询上传列表，同步ui界面
    4.文件未上传至服务器，执行分片上传
     */
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


    /*
    1.根据分片大小和文件大小，区分单文件上传和分片上传 （单文件上传，服务器不用合并文件）
    2.单文件上传
    3.分片上传
     */
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
                upload(uploadFileBean, it, chunks, id)
            }
        }
    }

    /*
    1.调用服务器单文件上传接口上传文件，
    2.查询主文件列表，同步ui界面，
    3.删除本地数据库上传文件，同步ui界面
     */
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

    private suspend fun upload(
        uploadFileBean: UploadFileBean,
        inputStream: InputStream,
        chunks: Set<Int>,
        id: String
    ) {
        try {
            _uploadId.value = uploadFileBean.id
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
                        findUploadBody(chunkNumber, uploadFileBean, totalSplit, id, buffer)
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
                createWebSocket(_userId.value)
                delLocalUploadFile(uploadFileBean.id)
            }
        } catch (jobE: CancellationException) {
            Log.d(Common.MY_TAG, "任务取消")
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "分片文件上传异常", e)
        }
    }


    private fun findUploadBody(
        chunkNumber: Int,
        uploadFileBean: UploadFileBean,
        totalSplit: Int,
        id: String,
        buffer: ByteArray
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

    /*
    1.将本地数据库中上传文件修改未暂停
    2.同步ui界面
    3.如果是开启上传，在暂停上传列表中移除当前文件id，如果当前定时上传任务未开启，开启定时上传扫描任务
    4.如果是暂停上传，在暂停上传列表中添加当前文件id
     */
    fun stopFile(id: Long, status: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (status == 0) {
                database.UploadFileDao().updateStatus(id, 0)
                _uploadFiles.value = database.UploadFileDao().findUploadFile()
                openUploadTask()
            } else if (status == -1) {
                database.UploadFileDao().updateStatusAndProgress(id, -1, progress.value)
                _uploadFiles.value = database.UploadFileDao().findUploadFile()
                job?.cancel()
            }
        }
    }

    fun cancelJob(id: Long) {
        job?.cancel()
        delLocalUploadFile(id)
    }


    fun viewFindProgress(uploadFileBean: UploadFileBean): Int {
        if (uploadFileBean.id == _uploadId.value) {
            return max(progress.value, uploadFileBean.progress)
        }
        return uploadFileBean.progress
    }


    private fun findProgress(numerator: Int, denominator: Int): Int {
        // 将 Int 转换为 Double，然后计算百分比
        return ((numerator.toDouble() / denominator.toDouble()) * 100).toInt()
    }
}