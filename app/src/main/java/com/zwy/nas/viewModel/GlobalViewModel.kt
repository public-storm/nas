package com.zwy.nas.viewModel

import android.content.ContentResolver
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwy.nas.Common
import com.zwy.nas.database.AppDatabase
import com.zwy.nas.database.DownloadFileBean
import com.zwy.nas.database.TokenBean
import com.zwy.nas.database.UploadFileBean
import com.zwy.nas.request.Api
import com.zwy.nas.request.CreateDirectoryRequest
import com.zwy.nas.request.FindHistoryFileResponse
import com.zwy.nas.request.LoginRequest
import com.zwy.nas.request.SelectFileResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class GlobalViewModel(private val database: AppDatabase) : ViewModel() {
    private val _navigateToLogin = mutableStateOf(false)
    val navigateToLogin: State<Boolean> get() = _navigateToLogin

    private val _userId = mutableStateOf("")
    val userId: State<String> get() = _userId

    private val _userName = mutableStateOf("")
    val userName: State<String> get() = _userName

    private var _loginSuccess = mutableStateOf(false)

    var loginSuccess: State<Boolean> = _loginSuccess

    private val _uploadHistoryFiles = MutableStateFlow<List<UploadFileBean>>(emptyList())
    val uploadHistoryFiles: StateFlow<List<UploadFileBean>> get() = _uploadHistoryFiles

    private val _uploadHistory = MutableStateFlow<List<FindHistoryFileResponse>>(emptyList())
    val uploadHistory: StateFlow<List<FindHistoryFileResponse>> get() = _uploadHistory

    var optFileIndex: Int = -1

    var superId: String = "-1"

    var contentResolver: ContentResolver? = null

    var bol = false

    val pathFiles = mutableStateListOf<Pair<String, String>>()

    val files = MutableStateFlow<List<SelectFileResponse>>(emptyList())

    fun test(dir: File) {
        if (!bol) {
            bol = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    var index = 1L
                    val fileName = "my_file.mp4"
                    val dirName = File(dir, "my_file")
                    if (!dirName.exists()) {
                        Log.d(Common.MY_TAG, "CreateDir: 创建目录")
                        dirName.mkdirs()
                    } else {
                        Log.d(Common.MY_TAG, "CreateDir: 目录已经创建")
                    }
                    val file = File(dirName, fileName)
                    val res = Api.get(findToken(database)).findChunkSize()
                    if (res.code == "200") {
                        FileOutputStream(file).use {
                            while (true) {
                                val r = Api.get(findToken(database)).filePlay(index)
                                val data = r.bytes()
                                val chunkSize = res.data
                                it.write(data)
                                val p = findProgress(index.toInt(), chunkSize?.toInt()!!)
                                Log.d(Common.MY_TAG, "文件写入 $index  $p")
                                if (index == chunkSize) {
                                    break
                                }
                                index++
                            }
                        }
                        Log.d(Common.MY_TAG, "写入完成")
                    }
                } catch (e: Exception) {
                    Log.e(Common.MY_TAG, "下载分片异常", e)
                }
            }
        }
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
                Log.d(Common.MY_TAG, "findServerFiles: 上级文件id $superId")
                val res = Api.get(findToken(database)).findFiles(superId)
                if (res.code == "200") {
                    Log.d(Common.MY_TAG, "findFiles: ${res.data}")
                    files.value = res.data ?: emptyList()
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
                val id = files.value[optFileIndex].id
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
                val id = files.value[optFileIndex].id
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

    fun onClickFile(index: Int) {
        val f = files.value[index]
        if (f.file == 0) {
            superId = f.id
            val pair = Pair(f.id, f.name)
            pathFiles.add(pair)
            findServerFiles()
        }
    }

    fun findDownloadFileBean(index: Int): DownloadFileBean {
        val selectFileResponse = files.value[index];
        return DownloadFileBean(
            selectFileResponse.id,
            selectFileResponse.name,
            selectFileResponse.size,
            0,
            selectFileResponse.file
        )
    }

    private fun findProgress(numerator: Int, denominator: Int): Int {
        // 将 Int 转换为 Double，然后计算百分比
        return ((numerator.toDouble() / denominator.toDouble()) * 100).toInt()
    }
}