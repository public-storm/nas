package com.zwy.nas.screen

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.zwy.nas.Common
import com.zwy.nas.R
import com.zwy.nas.viewModel.GlobalViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(navController: NavHostController) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = { keyboardController!!.hide() })
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LoginImage()
            Spacer(modifier = Modifier.height(10.dp))
            LoginBox(navController, keyboardController!!)
        }
    }
}

@Composable
fun LoginImage() {
    Image(
        painter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.leaf_984998)),
        contentDescription = null,
        modifier = Modifier
            .wrapContentSize()
            .padding(start = 88.dp)
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginBox(
    navController: NavHostController,
    keyboardController: SoftwareKeyboardController
) {
    var name by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf("") }
    val globalViewModel = GlobalViewModel.getInstance(null)
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        MyTextField(
            label = "用户名",
            imageVector = Icons.Filled.Person,
            strValue = name,
            nameError = nameError,
            keyboardController = keyboardController,
            onValueChange = { name = it },
            visualTransformation = VisualTransformation.None,
            keyboardType = KeyboardType.Text
        )
        MyTextField(
            label = "密码",
            imageVector = Icons.Filled.Lock,
            strValue = pwd,
            nameError = pwdError,
            keyboardController = keyboardController,
            onValueChange = { pwd = it },
            visualTransformation = PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password
        )
        Spacer(modifier = Modifier.height(20.dp))
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = 10.dp, end = 10.dp),
            onClick = {
                if (name == "") {
                    nameError = "用户名不能为空"
                    pwdError = ""
                } else if (pwd == "") {
                    nameError = ""
                    pwdError = "密码不能为空"
                } else {
                    nameError = ""
                    pwdError = ""
                    globalViewModel.login(name = name, pwd = pwd)
                }
            },
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(text = "登录")
        }
        LaunchedEffect(globalViewModel.loginSuccess.value) {
            if (globalViewModel.loginSuccess.value) {
                Log.d(Common.MY_TAG, "登录成功 跳转home")
                navController.navigate("home") {
                    popUpTo("login") {
                        inclusive = true
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyTextField(
    label: String,
    imageVector: ImageVector,
    strValue: String,
    nameError: String,
    keyboardController: SoftwareKeyboardController,
    onValueChange: (String) -> Unit,
    visualTransformation: VisualTransformation,
    keyboardType: KeyboardType
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 15.dp, end = 10.dp, top = 7.dp)
        )
        OutlinedTextField(
            value = strValue,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 20.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = keyboardType
            ),
            keyboardActions = KeyboardActions(onDone = { keyboardController.hide() }),
            visualTransformation = visualTransformation,
            singleLine = true,
            supportingText = {
                Text(nameError)
            },
            isError = nameError != "",
            trailingIcon = {
                if (strValue == "") {
                    Spacer(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
            }
        )
    }
}