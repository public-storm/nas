package com.zwy.nas.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
主页面
 */
@Composable
fun HomeScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val items = findDrawerItems()
    val selectedItem = remember { mutableStateOf(items[0]) }
    var route by remember { mutableStateOf(0) }
    //侧边栏
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                items.forEachIndexed { i, item ->
                    //侧边栏菜单
                    NavigationDrawerItem(
                        icon = { Icon(item.imageVector, contentDescription = null) },
                        label = { Text(item.name) },
                        selected = item == selectedItem.value,
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedItem.value = item
                            route = i
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeTitle(scope = scope, drawerState = drawerState)
                when (route) {
                    0 -> {
                        //总文件页面
                        FileScreen()
                    }

                    1 -> {
                        //文件上传
                        UploadScreen()
                    }

                    2 -> {
                        //文件下载
                        DownloadScreen()
                    }
                }

            }
        }
    )
}

/*
总页面头
 */
@Composable
fun HomeTitle(scope: CoroutineScope, drawerState: DrawerState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { scope.launch { drawerState.open() } }) {
            Icon(Icons.Default.Menu, contentDescription = null)
        }
        IconButton(onClick = { /*TODO*/ }) {
            Icon(Icons.Default.Person, contentDescription = null)
        }
    }
}


data class DrawerItem(val imageVector: ImageVector, val name: String)

/*
侧边栏菜单定义
 */
fun findDrawerItems(): List<DrawerItem> {
    return listOf(
        DrawerItem(Icons.Default.Home, "主页"),
        DrawerItem(Icons.Default.ArrowUpward, "上传"),
        DrawerItem(Icons.Default.ArrowDownward, "下载"),
        DrawerItem(Icons.Default.Favorite, "收藏"),
    )
}



