package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
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
import java.io.File
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
        accountHolderName: String,
        bankAccountNumber: String,
        upiId: String,
        aadhaarNumber: String,
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
                accountHolderName = accountHolderName,
                bankAccountNumber = bankAccountNumber,
                upiId = upiId,
                aadhaarNumber = aadhaarNumber,
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
     * Updates an existing client's details directly (used by edit/pencil feature)
     */
    fun updateClientProfile(
        clientId: Long,
        name: String,
        phone: String,
        email: String,
        accountHolderName: String,
        bankAccountNumber: String,
        upiId: String,
        aadhaarNumber: String,
        photoUri: Uri?,
        context: Context
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val currentClient = repository.getClientById(clientId)
            if (currentClient != null) {
                val savedPath = photoUri?.let { uri ->
                    FileUtil.saveImageToInternalStorage(context, uri)
                } ?: currentClient.profilePhotoPath

                val updatedClient = currentClient.copy(
                    name = name.trim(),
                    phoneNumber = phone.trim(),
                    email = email.trim(),
                    profilePhotoPath = savedPath,
                    accountHolderName = accountHolderName.trim(),
                    bankAccountNumber = bankAccountNumber.trim(),
                    upiId = upiId.trim(),
                    aadhaarNumber = aadhaarNumber.trim()
                )
                repository.updateClient(updatedClient)
                
                // Refresh client profile state in UI
                loadClientProfile(clientId)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
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

    /**
     * Generates a CSV string of all client transactions, saves it to cache,
     * and triggers a secure Android chooser sharing intent.
     */
    fun exportTransactionsToCsv(context: Context) {
        viewModelScope.launch {
            try {
                val transactions = _uiState.value.recentTransactions
                if (transactions.isEmpty()) {
                    android.widget.Toast.makeText(context, "No transactions to export", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 1. Build CSV content
                val csvHeader = "Transaction ID,Client Name,Phone,Service Name,Amount ($),Date,Notes\n"
                val csvBody = StringBuilder()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (tx in transactions) {
                    val id = tx.paymentId
                    val name = escapeCsv(tx.clientName)
                    val phone = escapeCsv(tx.clientPhone)
                    val service = escapeCsv(tx.serviceName)
                    val amount = tx.amount
                    val formattedDate = dateFormat.format(Date(tx.date))
                    val notes = escapeCsv(tx.notes)

                    csvBody.append("$id,$name,$phone,$service,$amount,$formattedDate,$notes\n")
                }

                val csvContent = csvHeader + csvBody.toString()

                // 2. Write to a cache file
                val exportDir = File(context.cacheDir, "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                val file = File(exportDir, "client_ledger_export.csv")
                file.writeText(csvContent)

                // 3. Get Uri using FileProvider
                val authority = "com.aistudio.clientledger.asqpn.fileprovider"
                val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

                // 4. Create and launch sharing intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Client Ledger Transactions Export")
                    putExtra(Intent.EXTRA_TEXT, "Here is the exported transaction ledger from Client Ledger App.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Export Ledger CSV").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)

            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }
}
