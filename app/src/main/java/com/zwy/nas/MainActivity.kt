package com.zwy.nas

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zwy.nas.database.DatabaseHolder
import com.zwy.nas.screen.HomeScreen
import com.zwy.nas.screen.LoginScreen
import com.zwy.nas.ui.theme.NasTheme
import com.zwy.nas.viewModel.GlobalViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = DatabaseHolder.getInstance(applicationContext)
        setContent {
            NasTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
//                    color = MaterialTheme.colorScheme.background
                ) {
                    CreateDir()
                    val globalViewModel = GlobalViewModel.getInstance(database)
                    globalViewModel.findUser()
                    App(globalViewModel)
                }
            }
        }
    }
}

@Composable
fun CreateDir() {
    val context = LocalContext.current
    val externalFilesDir = context.getExternalFilesDir(null)
    if (externalFilesDir != null) {
        // 创建私有目录，例如 "my_private_directory"
        val privateDir = File(externalFilesDir, "nas")
        // 确保目录存在
        if (!privateDir.exists()) {
            Log.d(Common.MY_TAG, "CreateDir: 创建目录")
            privateDir.mkdirs()
        } else {
            Log.d(Common.MY_TAG, "CreateDir: 目录已经创建")
        }
        val absolutePath = privateDir.absolutePath
        println("Absolute Path: $absolutePath")
        val testFile = File(privateDir, "test.txt")

        try {
            testFile.createNewFile()
            testFile.writeText("Hello, world!")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun App(globalViewModel: GlobalViewModel) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (globalViewModel.navigateToLogin.value) "login" else "home"
    ) {
        composable("login") { LoginScreen(navController) }
        composable("home") { HomeScreen(navController) }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NasTheme {
//        Greeting("Android")
    }
}