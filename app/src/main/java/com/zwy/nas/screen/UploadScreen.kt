package com.zwy.nas.screen

import android.util.Log
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwy.nas.Common
import com.zwy.nas.util.FileUtil
import com.zwy.nas.viewModel.GlobalViewModel

@Composable
fun UploadScreen() {
    var state by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        UploadTab(state) {
            state = it
        }
        when (state) {
            0 -> UploadList()
            1 -> UploadHistoryList()
        }
    }
}

@Composable
fun UploadTab(state: Int, onClick: (Int) -> Unit) {
    val titles = listOf("上传", "已上传")
    Column {
        TabRow(selectedTabIndex = state) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = state == index,
                    onClick = { onClick(index) },
                    text = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun UploadList() {
    val globalViewModel = GlobalViewModel.getInstance(null);
    val uploadFiles by globalViewModel.uploadFiles.collectAsState()
    globalViewModel.findLocalUploadFile()
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 10.dp, bottom = 30.dp)
        ) {
            items(uploadFiles.size) {
                ListItem(
                    headlineContent = {
                        Text(text = uploadFiles[it].name)
                    },
                    supportingContent = {
                        Column() {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = FileUtil.formatFileSize(uploadFiles[it].size))
                                Text(text = "${globalViewModel.viewFindProgress(uploadFiles[it])}%")
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = globalViewModel.viewFindProgress(uploadFiles[it]) / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = when (uploadFiles[it].file) {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = {
                                    if (uploadFiles[it].stop == 0) {
                                        globalViewModel.stopFile(uploadFiles[it].id, 1)
                                    } else {
                                        globalViewModel.stopFile(uploadFiles[it].id, 0)
                                    }
                                }) {
                                if (uploadFiles[it].stop == 0) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                }
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = {
                                    globalViewModel.delLocalUploadFile(uploadFiles[it].id)
                                    globalViewModel.cancelJob()
                                }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                )
                Divider()
            }
        }
    }
}


@Composable
fun UploadHistoryList() {
    val globalViewModel = GlobalViewModel.getInstance(null);
    val uploadHistory by globalViewModel.uploadHistory.collectAsState()
    globalViewModel.findServerUploadHistory()
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (uploadHistory.isNotEmpty()) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    IconButton(
                        onClick = { globalViewModel.delServerAllHistoryFile() },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
                Divider()
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 10.dp, bottom = 30.dp)
        ) {
            items(uploadHistory.size) {
                ListItem(
                    headlineContent = {
                        Text(text = uploadHistory[it].name)
                    },
                    supportingContent = {
                        Text(text = FileUtil.formatFileSize(uploadHistory[it].size))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = when (uploadHistory[it].fileType) {
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
                )
                Divider()
            }
        }
    }
}