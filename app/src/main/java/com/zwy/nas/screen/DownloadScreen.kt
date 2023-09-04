package com.zwy.nas.screen

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.zwy.nas.util.FileUtil
import com.zwy.nas.viewModel.DownloadViewModel

@Composable
fun DownloadScreen() {
    val downloadViewModel = DownloadViewModel.getInstance(null)
    val downloadFiles by downloadViewModel.downloadFiles.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp, end = 10.dp, bottom = 30.dp)
        ) {
            items(downloadFiles.size) {
                ListItem(
                    headlineContent = {
                        Text(text = downloadFiles[it].name)
                    },
                    supportingContent = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = FileUtil.formatFileSize(downloadFiles[it].size))
                                Text(text = "${downloadViewModel.viewFindProgress(downloadFiles[it])}%")
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = downloadViewModel.viewFindProgress(downloadFiles[it]) / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = when (downloadFiles[it].type) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = {
                                    if (downloadFiles[it].status == 0) {
                                        downloadViewModel.stopFile(downloadFiles[it].id, -1)
                                    } else if (downloadFiles[it].status == -1) {
                                        downloadViewModel.stopFile(downloadFiles[it].id, 0)
                                    }
                                }
                            ) {
                                if (downloadFiles[it].status == 0) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                } else if (downloadFiles[it].status == -1) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                }
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = {
                                    downloadViewModel.cancelJob(downloadFiles[it].id)
                                }
                            ) {
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