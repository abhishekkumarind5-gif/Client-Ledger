package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Client
import com.example.data.model.Payment
import com.example.data.model.TransactionItem
import com.example.data.repository.LedgerRepository
import com.example.util.FileUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    object Dashboard : Screen
    object ClientsList : Screen
    data class ClientProfile(val clientId: Long) : Screen
    data class AddTransaction(val initialClientId: Long? = null) : Screen
}

data class LedgerUiState(
    val clients: List<Client> = emptyList(),
    val recentTransactions: List<TransactionItem> = emptyList(),
    val totalRevenue: Double = 0.0,
    val totalClients: Int = 0,
    val isLoading: Boolean = true
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LedgerRepository

    // Screen navigation state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Backstack for back press navigation
    private val screenBackStack = mutableListOf<Screen>()

    // Core application UI state
    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    // Selected client details for the profile view
    private val _selectedClient = MutableStateFlow<Client?>(null)
    val selectedClient: StateFlow<Client?> = _selectedClient.asStateFlow()

    private val _selectedClientPayments = MutableStateFlow<List<Payment>>(emptyList())
    val selectedClientPayments: StateFlow<List<Payment>> = _selectedClientPayments.asStateFlow()

    // Channel for triggering WhatsApp intent
    private val _whatsAppIntentUrl = MutableStateFlow<String?>(null)
    val whatsAppIntentUrl: StateFlow<String?> = _whatsAppIntentUrl.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = LedgerRepository(database.ledgerDao())

        // Fetch data reactively
        viewModelScope.launch {
            // Simulate initial fetch delay to showcase polished loading state
            delay(1200)
            
            combine(
                repository.allClients,
                repository.recentTransactions
            ) { clientsList, transactionsList ->
                val revenue = transactionsList.sumOf { it.amount }
                LedgerUiState(
                    clients = clientsList,
                    recentTransactions = transactionsList,
                    totalRevenue = revenue,
                    totalClients = clientsList.size,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun navigateTo(screen: Screen) {
        screenBackStack.add(_currentScreen.value)
        _currentScreen.value = screen
        
        // If navigating to profile, dynamically collect payments for that client
        if (screen is Screen.ClientProfile) {
            loadClientProfile(screen.clientId)
        }
    }

    fun navigateBack(): Boolean {
        if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeAt(screenBackStack.size - 1)
            return true
        }
        return false
    }

    private fun loadClientProfile(clientId: Long) {
        viewModelScope.launch {
            val client = repository.getClientById(clientId)
            _selectedClient.value = client
            if (client != null) {
                repository.getPaymentsForClient(clientId).collect { payments ->
                    _selectedClientPayments.value = payments
                }
            }
        }
    }

    /**
     * Records a new transaction, detects existing client by phone number,
     * updates internal storage photos if required, and automatically
     * generates a WhatsApp notification redirect link.
     */
    fun addTransaction(
        clientName: String,
        clientPhone: String,
        clientEmail: String,
        photoUri: Uri?,
        amount: Double,
        serviceName: String,
        notes: String,
        shouldSendWhatsApp: Boolean,
        context: Context
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Persist photo if selected
            val savedPath = photoUri?.let { uri ->
                FileUtil.saveImageToInternalStorage(context, uri)
            }

            // 2. Add transaction using Repository (with phone deduplication)
            val (client, _) = repository.recordTransaction(
                clientName = clientName,
                clientPhone = clientPhone,
                clientEmail = clientEmail,
                clientPhotoPath = savedPath,
                amount = amount,
                serviceName = serviceName,
                notes = notes
            )

            // 3. Build WhatsApp Redirect Url if requested
            if (shouldSendWhatsApp) {
                val message = generateWhatsAppMessage(
                    clientName = client.name,
                    amount = amount,
                    serviceName = serviceName
                )
                val cleanPhone = sanitizePhoneNumber(client.phoneNumber)
                val encodedMessage = URLEncoder.encode(message, "UTF-8")
                val url = "https://wa.me/$cleanPhone?text=$encodedMessage"
                _whatsAppIntentUrl.value = url
            }

            // Navigate back to Dashboard or the client's profile
            _uiState.value = _uiState.value.copy(isLoading = false)
            navigateTo(Screen.ClientProfile(client.id))
        }
    }

    /**
     * Clear WhatsApp URL after handling it in the UI
     */
    fun clearWhatsAppIntent() {
        _whatsAppIntentUrl.value = null
    }

    private fun generateWhatsAppMessage(clientName: String, amount: Double, serviceName: String): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date())
        return """
            *Payment Confirmation* ✅
            
            Dear $clientName,
            
            We have successfully received your payment of *$$amount* for:
            💼 *$serviceName*
            
            📅 Date: $formattedDate
            
            Thank you for your business! If you have any questions, feel free to reach out.
        """.trimIndent()
    }

    private fun sanitizePhoneNumber(phone: String): String {
        // Remove spaces, dashes, parentheses and leading '+' signs to build wa.me format
        var clean = phone.replace(Regex("[\\s\\-\\(\\)+]"), "")
        // If it does not start with a country code, you might want to prepend one, or just return as is.
        // We'll return it clean, so wa.me works correctly.
        return clean
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch {
            repository.deleteClient(client)
            navigateTo(Screen.Dashboard)
        }
    }
}
