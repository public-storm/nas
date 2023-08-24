package com.zwy.nas.viewModel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
import java.nio.channels.Pipe
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

    var i = 0L

    var stopId = mutableListOf<Long>()

    val jobs = mutableListOf<Job>()


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
    1.根据文件名称和上级文件id到本地数据库中查询文件上传列表，如果已存在上传列表中，输出日志（文件已添加至上传列表）
    2.不在上传文件列表中，添加到上传列表中，查询上传列表数据同步ui界面
    3.如果当前上传任务没有启动，开启上传任务
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


    /*
    1.开启上传任务，上传任务标识修改为开启
    2.每间隔1s，查询本地数据库中上传列表中未暂停的上传文件
    3.如果没有未暂停的上传文件，关闭定时任务，上传任务标识修改为关闭
    4.如果有未暂停的上传文件，获取第一个上传文件，开启一个协程执行该文件的上传
     */
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
                        createWebSocket(_userId.value)
                        delLocalUploadFile(uploadFileBean.id)
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
                upload2(uploadFileBean, it, chunks, id)
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

    /*
    1.根据分片大小、文件大小、协程数来拆分分片任务
    2.执行拆分任务
    3.对应协程全部执行完毕，进度条复位为0
    4.删除本地上传文件，同步ui界面
    5.todo 开启websocket监听服务端信息，收到信息后查询主文件列表同步ui界面
     */
    private fun upload2(
        uploadFileBean: UploadFileBean,
        inputStream: InputStream,
        chunks: Set<Int>,
        id: String
    ) {
        try {
            _uploadId.value = uploadFileBean.id
            val num = 5
            val res = findFileTriple(splitSize, uploadFileBean.size, num)
            runBlocking {
                a(res, uploadFileBean.id)
                jobs.forEach {
                    it.join()
                }
                Log.d(Common.MY_TAG, "${uploadFileBean.name} 协程执行完成")
                _progress.value = 0
                i = 0
                if (uploadFileBean.id !in stopId){
                    delLocalUploadFile(uploadFileBean.id)
                }
            }
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "分片文件上传异常", e)
        }
    }

    /*
    1.根据拆分的任务开启对应的协程数
    2.协程执行对应的任务
     */
    suspend fun a(
        reqData: MutableList<MutableList<Triple<Pair<Int, Int>, Long, Long>>>,
        id: Long
    ) {
        reqData.forEach {
            val scope = CoroutineScope(Dispatchers.IO)
            val job = scope.launch {
                println("队列执行")
                b(it, id)
            }
            jobs.add(job)
        }
    }

    /*
    1.判断当前执行文件id是否已经被暂停，未暂停就执行上传任务
     */
    suspend fun b(list: MutableList<Triple<Pair<Int, Int>, Long, Long>>, id: Long) {
        list.forEach {
            if (id !in stopId) {
                sendHttp(it)
            }
        }
    }

    suspend fun sendHttp(triple: Triple<Pair<Int, Int>, Long, Long>) {
        delay(500)
        i++
        val p = findProgress(i.toInt(), triple.first.second)
        _progress.value = p
        println("${p}%")
    }

    private fun findFileTriple(
        splitSize: Long,
        totalSize: Long,
        num: Int
    ): MutableList<MutableList<Triple<Pair<Int, Int>, Long, Long>>> {
        val totalSplit = kotlin.math.ceil(totalSize.toDouble() / splitSize).toInt()
        var n = 1
        val res: MutableList<MutableList<Triple<Pair<Int, Int>, Long, Long>>> = mutableListOf()
        for (i in 0 until num) {
            val resChild: MutableList<Triple<Pair<Int, Int>, Long, Long>> = mutableListOf()
            res.add(resChild)
        }
        var index = 0
        var startTmp = 0L
        var buffer = splitSize
        val size = totalSplit / num
        while (n <= totalSplit) {
            if (n == totalSplit) {
                buffer = totalSize - startTmp
            }
            val triple = Triple(Pair(n, totalSplit), startTmp, startTmp + buffer)
            val resChild = res[index]
            if (resChild.size < size) {
                resChild.add(triple)
            } else {
                if (index + 1 == res.size) {
                    resChild.add(triple)
                } else {
                    index++
                    val resNextChild = res[index]
                    resNextChild.add(triple)
                }
            }
            startTmp += buffer
            n++
        }
        return res
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
    fun stopFile(id: Long, stop: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            database.UploadFileDao().stopFile(id, stop)
            _uploadFiles.value = database.UploadFileDao().findUploadFile()
            if (stop == 0) {
                stopId.remove(id)
                if (!isTask) {
                    openUploadTask()
                }
            } else {
                stopId.add(id)
                cancelJob()
            }
        }
    }

    fun cancelJob() {
        jobs.forEach {
            it.cancel()
        }
    }

    fun viewFindProgress(uploadFileBean: UploadFileBean): Int {
        return if (uploadFileBean.id == _uploadId.value) {
            _progress.value
        } else {
            uploadFileBean.progress
        }
    }


    private fun findProgress(numerator: Int, denominator: Int): Int {
        // 将 Int 转换为 Double，然后计算百分比
        return ((numerator.toDouble() / denominator.toDouble()) * 100).toInt()
    }
}