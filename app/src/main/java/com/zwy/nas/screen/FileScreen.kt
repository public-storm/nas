package com.zwy.nas.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zwy.nas.Common
import com.zwy.nas.util.FileUtil
import com.zwy.nas.viewModel.GlobalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@SuppressLint("UnrememberedMutableState")
@Composable
fun FileScreen() {
//    val context = LocalContext.current
//    val globalViewModel = GlobalViewModel.getInstance(null)
//    if (globalViewModel.contentResolver == null) {
//        globalViewModel.contentResolver = context.contentResolver
//    }
//    val upFiles = mutableStateListOf<Pair<String, String>>()
//    PathRow(upFiles)
//    ListBox(upFiles)
    Test2()
}


@Composable
fun Test2() {
    val context = LocalContext.current
    val privateDir = context.getExternalFilesDir(null)
    val globalViewModel = GlobalViewModel.getInstance(null);
    val player = ExoPlayer.Builder(context).build()
    val mediaItem =
        MediaItem.fromUri(Uri.fromFile(File(File(privateDir, "my_file"), "my_file.mp4")))
    Column() {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = true
                    this.player = player
                }
            }, modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )
        Button(onClick = {
            if (privateDir != null) {
                globalViewModel.test(privateDir)
                player.setMediaItem(mediaItem)
                player.pause()
                player.play()
            }
        }) {
            Text(text = "测试")
        }
    }
}


@Composable
fun Test() {
    val hlsUri = "http://10.23.100.186:8777/video"
    val context = LocalContext.current
    val player = ExoPlayer.Builder(context).build()
    player.setMediaItem(MediaItem.fromUri(hlsUri))
    player.prepare()
    player.play()
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = true
                this.player = player
            }
        }, modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    )
}


@Composable
fun PlayerSurface(
    modifier: Modifier,
    onPlayerViewAvailable: (PlayerView) -> Unit = {}
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = true
                onPlayerViewAvailable(this)
            }
        },
        modifier = modifier
    )
}


@Composable
fun PathRow(list: MutableList<Pair<String, String>>) {
    val globalViewModel = GlobalViewModel.getInstance(null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 10.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            list.clear()
            globalViewModel.superId = "-1"
            globalViewModel.findServerFiles()
        }) {
            Icon(Icons.Default.Home, contentDescription = null)
        }
        list.forEachIndexed { i, v ->
            if (list.size > 3) {
                if (i < list.size - 3) {
                    return@forEachIndexed
                }
            }
            Text(
                text = if (list.size > 3 && list.size - i == 3) "..>" else ">",
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = {
                    if (i < list.size - 1) {
                        globalViewModel.superId = v.first
                        globalViewModel.findServerFiles()
                        for (j in i + 1 until list.size) {
                            list.removeLast()
                        }
                    }
                },
            ) {
                Text(
                    text = v.second, textAlign = TextAlign.Center
                )
            }
        }

    }
}

@Composable
fun ListBox(list: MutableList<Pair<String, String>>) {
    val globalViewModel = GlobalViewModel.getInstance(null);
    val files by globalViewModel.files.collectAsState()
    globalViewModel.findServerFiles()
    var openAddBtnDialog by remember { mutableStateOf(false) }
    var openRenameDialog by remember { mutableStateOf(false) }
    var openDelDialog by remember { mutableStateOf(false) }
    var openFileInfo by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 10.dp, bottom = 30.dp)
        ) {
            items(files.size) {
                var expanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = {
                        TextButton(onClick = {
                            val f = files[it]
                            if (f.file == 0) {
                                globalViewModel.superId = files[it].id
                                list.add(Pair(files[it].id, files[it].name))
                                globalViewModel.findServerFiles()
                            }
                        }) {
                            Text(
                                text = files[it].name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = when (files[it].file) {
                                0 -> Icons.Default.Folder
                                1 -> Icons.Default.Image
                                2 -> Icons.Default.MusicNote
                                3 -> Icons.Default.Movie
                                else -> {
                                    Icons.Default.Description
                                }
                            },
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        FilesTrailing(
                            expanded = expanded,
                            onClick = {
                                expanded = true
                                globalViewModel.optFileIndex = it
                            },
                            infoClick = {
                                openFileInfo = true
                            },
                            onDismissRequest = {
                                expanded = false
                            },
                            delClick = { openDelDialog = true }) {
                            openRenameDialog = true
                        }
                    },
                )
                Divider()
            }
        }
        FloatingActionButton(
            onClick = { openAddBtnDialog = true },
            modifier = Modifier
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        AddBtnDialog(openDialog = openAddBtnDialog) {
            openAddBtnDialog = false
        }
        RenameFileDialog(openRenameDialog = openRenameDialog) {
            openRenameDialog = false
        }
        DelFileDialog(openDelDialog = openDelDialog) {
            openDelDialog = false
        }
        FileInfo(openFileInfo = openFileInfo) {
            openFileInfo = false
        }
    }
}

@Composable
fun FilesTrailing(
    expanded: Boolean,
    onClick: () -> Unit,
    onDismissRequest: () -> Unit,
    infoClick: () -> Unit,
    delClick: () -> Unit,
    rNameClick: () -> Unit
) {
    Box() {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onDismissRequest() },
            offset = DpOffset(10.dp, 10.dp)
        ) {
            DropdownMenuItem(
                text = { Text("详细信息") },
                onClick = {
                    onDismissRequest()
                    infoClick()
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Sms,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    onDismissRequest()
                    delClick()
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text("下载") },
                onClick = { onDismissRequest() },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    onDismissRequest()
                    rNameClick()
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null
                    )
                },
            )
        }
    }
}

@Composable
fun FileInfo(openFileInfo: Boolean, onClick: () -> Unit) {
    val globalViewModel = GlobalViewModel.getInstance(null)
    val files by globalViewModel.files.collectAsState()
    val index = globalViewModel.optFileIndex
    if (index >= 0 && files.size > index) {
        val file = files[index]
        MyDialog(
            openBol = openFileInfo,
            onClick = onClick
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(text = "文件名", fontSize = 20.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = file.name)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "创建时间", fontSize = 20.sp)
                    Text(text = file.createTime)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "文件大小", fontSize = 20.sp)
                    Text(text = FileUtil.formatFileSize(file.size))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "文件类型", fontSize = 20.sp)
                    Text(text = FileUtil.findFileType(file.file))
                }
            }
        }
    }
}

@Composable
fun DelFileDialog(openDelDialog: Boolean, onClick: () -> Unit) {
    val globalViewModel = GlobalViewModel.getInstance(null)
    val files by globalViewModel.files.collectAsState()
    MyDialog(
        openBol = openDelDialog, onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (files[globalViewModel.optFileIndex].file) {
                    0 -> "删除文件夹"
                    1 -> "删除图片"
                    2 -> "删除音频"
                    3 -> "删除视频"
                    else -> {
                        "删除文件"
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = files[globalViewModel.optFileIndex].name,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    globalViewModel.delServerDirectory()
                    onClick()
                }) {
                    Text(text = "确定")
                }
                TextButton(onClick = { onClick() }) {
                    Text(text = "取消")
                }
            }
        }
    }
}

@Composable
fun RenameFileDialog(openRenameDialog: Boolean, onClick: () -> Unit) {
    var rName by remember { mutableStateOf("") }
    var rNameError by remember { mutableStateOf(false) }
    val globalViewModel = GlobalViewModel.getInstance(null)
    MyDialog(openBol = openRenameDialog, onClick = {
        onClick()
        rName = ""
        rNameError = false
    }) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "重命名")
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = rName,
                onValueChange = { rName = it },
                isError = rNameError,
                label = { Text(text = "新名称") },
                supportingText = {
                    if (rNameError) {
                        Text(
                            text = "名称不能为空",
                            color = Color.Red
                        )
                    }
                })
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = {
                    if (rName == "") {
                        rNameError = true
                    } else {
                        onClick()
                        globalViewModel.renameServerFile(rName)
                        rName = ""
                        rNameError = false
                    }
                }) {
                    Text(text = "确定")
                }
                TextButton(onClick = {
                    onClick()
                    rName = ""
                    rNameError = false
                }) {
                    Text(text = "取消")
                }
            }
        }
    }
}


@Composable
fun AddBtnDialog(openDialog: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var openCreateDirDialog by remember { mutableStateOf(false) }
    val globalViewModel = GlobalViewModel.getInstance(null)
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                globalViewModel.addLocalUploadFile(uri)
            }
        }
    )
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                fileChooserLauncher.launch(arrayOf("*/*"))
            } else {
                Log.d(Common.MY_TAG, "Test: 未授权处理")
            }
        }
    )
    MyDialog(
        openBol = openDialog, onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = {
                onClick()
                openCreateDirDialog = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "创建文件夹")
            }
            TextButton(onClick = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(Common.MY_TAG, "Test: 已经授权")
                    fileChooserLauncher.launch(arrayOf("*/*"))
                } else {
                    Log.d(Common.MY_TAG, "Test: 未授权，开始授权")
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                onClick()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("上传文件")
            }
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = {
                    onClick()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("取消")
            }
        }
    }
    CreateDirDialog(openCreateDirDialog = openCreateDirDialog) {
        openCreateDirDialog = false
    }
}

@Composable
fun CreateDirDialog(openCreateDirDialog: Boolean, onClick: () -> Unit) {
    var dirName by remember { mutableStateOf("") }
    val globalViewModel = GlobalViewModel.getInstance(null)
    MyDialog(openBol = openCreateDirDialog, onClick = { onClick() }) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "创建文件夹")
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = dirName,
                onValueChange = { dirName = it },
                label = { Text(text = "文件名") }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    onClick()
                    globalViewModel.createServerDirectory(dirName)
                    dirName = ""
                }) {
                    Text(text = "确定")
                }
                TextButton(onClick = {
                    onClick()
                    dirName = ""
                }) {
                    Text(text = "取消")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDialog(
    openBol: Boolean,
    onClick: () -> Unit,
    body: @Composable () -> Unit
) {
    if (openBol) {
        AlertDialog(onDismissRequest = onClick) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                body()
            }
        }
    }
}
