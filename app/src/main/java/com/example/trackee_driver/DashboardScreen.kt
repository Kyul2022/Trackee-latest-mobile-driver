package com.example.trackee_driver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(function: () -> Unit) {
    var currentCity by remember { mutableStateOf("Getting location...") }

    // Ici tu peux ajouter un LaunchedEffect pour mettre à jour currentCity depuis ton service si nécessaire

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current Location:")
                Text(currentCity, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Updates every 5 minutes", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
