package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.firebase.FirebaseHelper
import com.example.ui.screens.LedgerApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LedgerViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize Firebase (loads API key and Project ID gracefully if set in secrets)
        FirebaseHelper.initializeFirebase(this)

        // 2. Enable Edge to Edge Material 3 Styling
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    LedgerApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        // Handle back button on our custom backstack first!
        if (!viewModel.navigateBack()) {
            super.onBackPressed()
        }
    }
}
