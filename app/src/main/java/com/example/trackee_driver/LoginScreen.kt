package com.example.trackee_driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    var matricule by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = matricule,
            onValueChange = { matricule = it },
            label = { Text("Matricule") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            errorMessage = ""
            isLoading = true
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val token = loginToBackend(matricule, password)
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        if (token != null) {
                            onLoginSuccess(token)
                        } else {
                            errorMessage = "Invalid username or password"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        errorMessage = "Error: ${e.message}"
                    }
                }
            }
        }) {
            Text("Login")
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun loginToBackend(matricule: String, password: String): String? {
    val client = OkHttpClient()
    val json = JSONObject().apply {
        put("matricule", matricule)
        put("password", password)
    }.toString()

    val request = Request.Builder()
        .url("http://172.20.197.225:8080/auth/login") // ton endpoint Spring
        .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()

    client.newCall(request).execute().use { response ->
        return if (response.isSuccessful) {
            response.body?.string()?.replace("\"", "") // supprime les quotes si le token est entre ""
        } else {
            null
        }
    }
}
