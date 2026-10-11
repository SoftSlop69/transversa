// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun TransversaApp(
    selectedName: String?,
    onSelectMedia: () -> Unit,
) {
    val navController = rememberNavController()

    TransversaTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(contentPadding),
            ) {
                composable("home") {
                    HomeScreen(
                        selectedName = selectedName,
                        onSelectMedia = onSelectMedia,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    selectedName: String?,
    onSelectMedia: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = selectedName?.let { stringResource(R.string.selected_media, it) }
                ?: stringResource(R.string.home_prompt),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 32.dp, bottom = 20.dp),
        )
        Button(onClick = onSelectMedia) {
            Text(stringResource(R.string.select_media))
        }
        Text(
            text = stringResource(R.string.incomplete_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}

