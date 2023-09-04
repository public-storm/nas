package com.zwy.nas.viewModel

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwy.nas.Common
import com.zwy.nas.database.AppDatabase
import com.zwy.nas.database.DownloadFileBean
import com.zwy.nas.request.Api
import com.zwy.nas.task.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.max

class DownloadViewModel(private val database: AppDatabase) : ViewModel() {
    companion object {
        private var instance: DownloadViewModel? = null

        fun getInstance(database: AppDatabase?): DownloadViewModel {
            if (instance == null && database != null) {
                instance = DownloadViewModel(database)
            }
            return instance!!
        }
    }


    val downloadFiles = MutableStateFlow<List<DownloadFileBean>>(emptyList())

    val progress = mutableStateOf(0)

    private var isTask = false
    private var job: Job? = null
    private var dir: File? = null
    private var optId: String = ""

    fun addDir(dir: File) {
        if (this.dir == null) {
            this.dir = dir
        }
    }

    /**
     * 获取请求服务端token
     */
    private suspend fun findToken(database: AppDatabase): String {
        return database.tokenDao().findToken() ?: ""
    }

    /**
     * 获取进度条百分比数值
     */
    private fun findProgress(numerator: Int, denominator: Int): Int {
        // 将 Int 转换为 Double，然后计算百分比
        return ((numerator.toDouble() / denominator.toDouble()) * 100).toInt()
    }

    /*
    添加文件下载任务
    1.本地数据库添加下载任务
    2.刷新ui下载列表界面
    3.开启下载定时扫描任务
     */
    fun addDownloadTask(downloadFileBean: DownloadFileBean) {
        viewModelScope.launch(Dispatchers.IO) {
            //添加文件下载任务
            database.downloadFileDao().insertDownloadFile(downloadFileBean)
            //同步ui下载任务
            downloadFiles.value = database.downloadFileDao().findDownloadFile()
            //开启下载定时扫描任务
            openDownloadTask()
        }
    }

    /*
    文件下载定时扫描任务
    1.获取未完成下载的任务开始下载
     */
    private fun openDownloadTask() {
        if (!isTask) {
            isTask = true
            DownloadTask.startTask(0, 1000) {
                val downloadFiles = database.downloadFileDao().findByStatusSync(0)
                if (downloadFiles.isEmpty()) {
                    Log.d(Common.MY_TAG, "关闭文件下载定时扫描任务")
                    DownloadTask.cancelTask()
                    isTask = false
                } else {
                    runBlocking {
                        job = launch(Dispatchers.IO) {
                            downloadFile(downloadFiles[0], dir!!)
                        }
                    }
                }
            }
        }
    }

    /*
    文件下载
    1.创建文件目录（文件名）
    2.创建空文件
    3.请求服务端文件数据，追加数据或者先覆盖残缺分片后追加文件数据
     */
    private suspend fun downloadFile(downloadFileBean: DownloadFileBean, dir: File) {
        try {
            optId = downloadFileBean.id
            //创建文件目录
            val dirName = downloadFileBean.name.substringBefore(".")
            val dirFile = File(dir, dirName)
            if (!dirFile.exists()) {
                Log.d(Common.MY_TAG, "文件下载 目录创建 $dirName")
                dirFile.mkdir()
            }
            val file = File(dirFile, downloadFileBean.name)
            if (!file.exists()) {
                withContext(Dispatchers.IO) {
                    file.createNewFile()
                }
            }
            val id = downloadFileBean.id
            val findChunkRes = Api.get(findToken(database)).findDownloadChunk(id)
            if (findChunkRes.code == "200") {
                val chunkSize = findChunkRes.data!!.chunkSize
                val chunkTotal = findChunkRes.data!!.chunkTotal
                val fileSize = file.length()
                //文件追加或文件覆盖
                if (fileSize % chunkSize == 0L) {
                    //文件追加
                    Log.d(Common.MY_TAG, "文件追加 ${downloadFileBean.name}")
                    appendToFile(file, chunkTotal, chunkSize, downloadFileBean.id)
                } else {
                    //文件覆盖
                    Log.d(Common.MY_TAG, "文件覆盖追加 ${downloadFileBean.name}")
                    writeToPosition(file, chunkTotal, chunkSize, downloadFileBean.id)
                }
            } else {
                Log.w(Common.MY_TAG, "文件下载 查询分片数失败")
            }
        } catch (e: Exception) {
            Log.e(Common.MY_TAG, "文件下载异常", e)
        }
    }

    /*
    文件下载（追加）
     */
    private suspend fun appendToFile(
        file: File,
        chunkTotal: Long,
        chunkSize: Long,
        id: String
    ) {
        val fileSize = file.length()
        val index = if (fileSize > 0) (fileSize / chunkSize) + 1 else 1L
        Log.d(Common.MY_TAG, "文件追加 index $index")
        writeFile(file, id, index, chunkTotal)
    }


    /*
    文件下载（先覆盖残缺分片，再追加分片数据）
     */
    private suspend fun writeToPosition(
        file: File,
        chunkTotal: Long,
        chunkSize: Long,
        id: String
    ) {
        val fileSize = file.length()
        val hasChunkNum = fileSize / chunkSize
        val position = hasChunkNum * chunkSize
        var index = hasChunkNum + 1
        Log.d(Common.MY_TAG, "文件覆盖追加 index $index")
        //先覆盖残缺分片
        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "rw").use {
                it.seek(position)
                val dataRes = Api.get(findToken(database)).download(id, index)
                it.write(dataRes.bytes())
                index++
            }
        }
        writeFile(file, id, index, chunkTotal)
    }

    /*
    文件下载（分片数据追加）
     */
    private suspend fun writeFile(file: File, id: String, chunkIndex: Long, chunkTotal: Long) {
        var index = chunkIndex
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use {
                while (true) {
                    val dataRes = Api.get(findToken(database)).download(id, index)
                    it.write(dataRes.bytes())
                    val p = findProgress(index.toInt(), chunkTotal.toInt())
                    Log.d(Common.MY_TAG, "文件分片写入 $p")
                    progress.value = p
                    if (index == chunkTotal) {
                        break
                    }
                    index++
                }
                Log.d(Common.MY_TAG, "文件下载写入完成")
                database.downloadFileDao().delById(id)
                downloadFiles.value = database.downloadFileDao().findDownloadFile()
            }
        }
    }

    fun viewFindProgress(downloadFileBean: DownloadFileBean): Int {
        if (downloadFileBean.id == optId) {
            return max(progress.value, downloadFileBean.progress)
        }
        return downloadFileBean.progress
    }

    fun stopFile(id: String, status: Int) {
        // 0：恢复下载 1：暂停下载
        viewModelScope.launch(Dispatchers.IO) {
            if (status == 0) {
                database.downloadFileDao().updateFileStatus(id, 0)
                downloadFiles.value = database.downloadFileDao().findDownloadFile()
                openDownloadTask()
            } else if (status == -1) {
                database.downloadFileDao().updateFileStatusAndProgress(id, -1, progress.value)
                downloadFiles.value = database.downloadFileDao().findDownloadFile()
                job?.cancel()
            }
        }
    }

    fun cancelJob(id: String) {
        job?.cancel()
        delDownloadTask(id)
    }

    private fun delDownloadTask(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.downloadFileDao().delById(id)
            downloadFiles.value = database.downloadFileDao().findDownloadFile()
        }
    }

}