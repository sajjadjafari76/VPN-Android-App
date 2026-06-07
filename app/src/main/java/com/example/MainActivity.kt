package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.VpnDatabase
import com.example.data.VpnRepository
import com.example.ui.VpnViewModel
import com.example.ui.VpnViewModelFactory
import com.example.ui.screens.VpnMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Local DB components
        val database = VpnDatabase.getDatabase(this)
        val repository = VpnRepository(database.vpnDao())
        val viewModelFactory = VpnViewModelFactory(repository)

        setContent {
            // Instantiate ViewModel with custom repository factory
            val mainViewModel: VpnViewModel = viewModel(factory = viewModelFactory)
            
            // Flow theme preferences chosen by the user
            val settings by mainViewModel.appSettings.collectAsState()
            val useDarkTheme = settings?.isDarkTheme ?: true

            MyApplicationTheme(darkTheme = useDarkTheme) {
                VpnMainScreen(viewModel = mainViewModel)
            }
        }
    }
}
