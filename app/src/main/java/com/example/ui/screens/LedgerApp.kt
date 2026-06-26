package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.Client
import com.example.data.model.Payment
import com.example.data.model.TransactionItem
import com.example.ui.viewmodel.LedgerUiState
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.Screen
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val whatsAppUrl by viewModel.whatsAppIntentUrl.collectAsStateWithLifecycle()

    // Handle automated WhatsApp redirection
    LaunchedEffect(whatsAppUrl) {
        whatsAppUrl?.let { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
                Toast.makeText(context, "Opening WhatsApp confirmation...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp is not installed. Copying message.", Toast.LENGTH_LONG).show()
            } finally {
                viewModel.clearWhatsAppIntent()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            is Screen.Dashboard -> "Financial Ledger"
                            is Screen.ClientsList -> "All Clients"
                            is Screen.ClientProfile -> "Client Profile"
                            is Screen.AddTransaction -> "Record Payment"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                navigationIcon = {
                    if (currentScreen !is Screen.Dashboard) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate Back"
                            )
                        }
                    }
                },
                actions = {
                    if (currentScreen is Screen.Dashboard) {
                        IconButton(
                            onClick = { viewModel.exportTransactionsToCsv(context) },
                            modifier = Modifier.testTag("export_csv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export CSV"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (currentScreen is Screen.Dashboard || currentScreen is Screen.ClientsList) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Dashboard,
                        onClick = { viewModel.navigateTo(Screen.Dashboard) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        modifier = Modifier.testTag("nav_dashboard")
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.ClientsList,
                        onClick = { viewModel.navigateTo(Screen.ClientsList) },
                        icon = { Icon(Icons.Default.List, contentDescription = "Clients") },
                        label = { Text("Clients") },
                        modifier = Modifier.testTag("nav_clients")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentScreen is Screen.Dashboard || currentScreen is Screen.ClientsList) {
                FloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.AddTransaction()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_transaction_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "screen_navigation"
            ) { screen ->
                when (screen) {
                    is Screen.Dashboard -> DashboardScreen(
                        uiState = uiState,
                        onTransactionClick = { tx -> viewModel.navigateTo(Screen.ClientProfile(tx.clientId)) },
                        onViewAllClients = { viewModel.navigateTo(Screen.ClientsList) }
                    )
                    is Screen.ClientsList -> ClientsListScreen(
                        uiState = uiState,
                        onClientClick = { client -> viewModel.navigateTo(Screen.ClientProfile(client.id)) }
                    )
                    is Screen.ClientProfile -> {
                        val client by viewModel.selectedClient.collectAsStateWithLifecycle()
                        val payments by viewModel.selectedClientPayments.collectAsStateWithLifecycle()
                        
                        ClientProfileScreen(
                            client = client,
                            payments = payments,
                            onAddPaymentClick = { viewModel.navigateTo(Screen.AddTransaction(client?.id)) },
                            onDeleteClientClick = { c -> viewModel.deleteClient(c) },
                            onSaveProfile = { name, phone, email, holder, bank, upi, aadhaar, photo ->
                                client?.let { c ->
                                    viewModel.updateClientProfile(
                                        clientId = c.id,
                                        name = name,
                                        phone = phone,
                                        email = email,
                                        accountHolderName = holder,
                                        bankAccountNumber = bank,
                                        upiId = upi,
                                        aadhaarNumber = aadhaar,
                                        photoUri = photo,
                                        context = context
                                    )
                                }
                            }
                        )
                    }
                    is Screen.AddTransaction -> {
                        val allClients = uiState.clients
                        val initialClient = screen.initialClientId?.let { id ->
                            allClients.find { it.id == id }
                        }
                        
                        AddTransactionScreen(
                            initialClient = initialClient,
                            allClients = allClients,
                            onSaveTransaction = { name, phone, email, photo, holder, bank, upi, aadhaar, amount, service, notes, sendWhatsApp ->
                                viewModel.addTransaction(
                                    clientName = name,
                                    clientPhone = phone,
                                    clientEmail = email,
                                    photoUri = photo,
                                    accountHolderName = holder,
                                    bankAccountNumber = bank,
                                    upiId = upi,
                                    aadhaarNumber = aadhaar,
                                    amount = amount,
                                    serviceName = service,
                                    notes = notes,
                                    shouldSendWhatsApp = sendWhatsApp,
                                    context = context
                                )
                            },
                            onCancel = { viewModel.navigateBack() }
                        )
                    }
                }
            }

            // Universal Loading State Overlay (Spinner)
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {}, // Intercept clicks
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.testTag("loading_spinner"),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Updating Ledger...",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(
    uiState: LedgerUiState,
    onTransactionClick: (TransactionItem) -> Unit,
    onViewAllClients: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Finance Gradient Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                // Beautiful abstract Canvas circles for modern geometric aesthetic
                val circleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                val circleColorAccent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = circleColor, radius = 250f, center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.2f))
                    drawCircle(color = circleColorAccent, radius = 180f, center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.8f))
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TOTAL REVENUE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatCurrency(uiState.totalRevenue),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Status",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(32.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tracking ${uiState.totalClients} Clients",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = onViewAllClients,
                            modifier = Modifier.testTag("dashboard_view_all_clients")
                        ) {
                            Text(
                                text = "View All",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Recent Transactions Section Header
        item {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
            )
        }

        // Transactions List
        if (uiState.recentTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "No Transactions",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No payments recorded yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(uiState.recentTransactions) { tx ->
                TransactionCard(
                    transaction = tx,
                    onClick = { onTransactionClick(tx) }
                )
            }
        }
    }
}

@Composable
fun TransactionCard(
    transaction: TransactionItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
            .testTag("transaction_card_${transaction.paymentId}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Client Photo or Fallback Avatar
            if (transaction.clientPhotoPath != null) {
                AsyncImage(
                    model = File(transaction.clientPhotoPath),
                    contentDescription = "${transaction.clientName}'s photo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = transaction.clientName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.clientName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = transaction.serviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDate(transaction.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Text(
                text = "+${formatCurrency(transaction.amount)}",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF2E7D32) // Soft forest green for credit transactions
            )
        }
    }
}

// ==========================================
// 2. CLIENTS LIST SCREEN
// ==========================================
@Composable
fun ClientsListScreen(
    uiState: LedgerUiState,
    onClientClick: (Client) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredClients = uiState.clients.filter {
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.phoneNumber.contains(searchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            },
            placeholder = { Text("Search clients by name or phone...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("client_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Clients list
        if (filteredClients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "No Clients Found",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No clients match \"$searchQuery\"." else "No clients saved yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(filteredClients) { client ->
                    // Calculate total transactions and revenue for this client
                    val clientTx = uiState.recentTransactions.filter { it.clientId == client.id }
                    val totalPaid = clientTx.sumOf { it.amount }
                    val count = clientTx.size

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onClientClick(client) }
                            .testTag("client_card_${client.id}"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (client.profilePhotoPath != null) {
                                AsyncImage(
                                    model = File(client.profilePhotoPath),
                                    contentDescription = "${client.name}'s photo",
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = client.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = client.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = client.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$count Payments recorded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatCurrency(totalPaid),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Total Paid",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. CLIENT PROFILE SCREEN
// ==========================================
@Composable
fun ClientProfileScreen(
    client: Client?,
    payments: List<Payment>,
    onAddPaymentClick: () -> Unit,
    onDeleteClientClick: (Client) -> Unit,
    onSaveProfile: (name: String, phone: String, email: String, holder: String, bank: String, upi: String, aadhaar: String, photo: Uri?) -> Unit
) {
    if (client == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }

    // State holding user edits
    var nameInput by remember(client) { mutableStateOf(client.name) }
    var phoneInput by remember(client) { mutableStateOf(client.phoneNumber) }
    var emailInput by remember(client) { mutableStateOf(client.email) }
    var accountHolderInput by remember(client) { mutableStateOf(client.accountHolderName) }
    var bankAccountInput by remember(client) { mutableStateOf(client.bankAccountNumber) }
    var upiIdInput by remember(client) { mutableStateOf(client.upiId) }
    var aadhaarNumberInput by remember(client) { mutableStateOf(client.aadhaarNumber) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val totalPaid = payments.sumOf { it.amount }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Client Ledger?") },
            text = { Text("Are you sure you want to permanently delete ${client.name} and their entire payment transaction history? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClientClick(client)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_client_confirm_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Profile Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile image launcher - Clickable area to view/change
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clickable(enabled = isEditMode) { imagePickerLauncher.launch("image/*") }
                        .testTag("profile_photo_container"),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    if (selectedPhotoUri != null) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Selected Photo",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else if (client.profilePhotoPath != null) {
                        AsyncImage(
                            model = File(client.profilePhotoPath),
                            contentDescription = "${client.name}'s photo",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = client.name.take(1).uppercase(),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 42.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    // Small edit icon indicator or dynamic action helper overlay
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (isEditMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Person,
                            contentDescription = "Profile Photo Edit Indicator",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isEditMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { isEditMode = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("edit_profile_pencil_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Client Full Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("edit_client_name_input")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isEditMode) {
                    // Quick Contact Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phoneNumber}"))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                        }
                        
                        if (client.email.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:${client.email}")
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("delete_client_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Client", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("edit_client_phone_input")
                    )
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("edit_client_email_input")
                    )
                }
            }
        }

        // Expanded Profile: Banking & Extended Profile Fields
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Banking & Identity Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (!isEditMode) {
                        ProfileDetailRow(
                            label = "Account Holder Name",
                            value = client.accountHolderName.ifBlank { "Not Provided" }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        
                        ProfileDetailRow(
                            label = "Bank Account Number",
                            value = client.bankAccountNumber.ifBlank { "Not Provided" }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        
                        ProfileDetailRow(
                            label = "UPI ID",
                            value = client.upiId.ifBlank { "Not Provided" }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        
                        ProfileDetailRow(
                            label = "Aadhaar Number",
                            value = redactAadhaar(client.aadhaarNumber)
                        )
                    } else {
                        OutlinedTextField(
                            value = accountHolderInput,
                            onValueChange = { accountHolderInput = it },
                            label = { Text("Account Holder Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("edit_holder_name_input")
                        )
                        OutlinedTextField(
                            value = bankAccountInput,
                            onValueChange = { bankAccountInput = it },
                            label = { Text("Bank Account Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("edit_bank_account_input")
                        )
                        OutlinedTextField(
                            value = upiIdInput,
                            onValueChange = { upiIdInput = it },
                            label = { Text("UPI ID") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("edit_upi_id_input")
                        )
                        OutlinedTextField(
                            value = aadhaarNumberInput,
                            onValueChange = { aadhaarNumberInput = it },
                            label = { Text("Aadhaar Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("edit_aadhaar_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isEditMode = false
                                    selectedPhotoUri = null
                                    nameInput = client.name
                                    phoneInput = client.phoneNumber
                                    emailInput = client.email
                                    accountHolderInput = client.accountHolderName
                                    bankAccountInput = client.bankAccountNumber
                                    upiIdInput = client.upiId
                                    aadhaarNumberInput = client.aadhaarNumber
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    onSaveProfile(
                                        nameInput,
                                        phoneInput,
                                        emailInput,
                                        accountHolderInput,
                                        bankAccountInput,
                                        upiIdInput,
                                        aadhaarNumberInput,
                                        selectedPhotoUri
                                    )
                                    isEditMode = false
                                },
                                enabled = nameInput.isNotBlank() && phoneInput.isNotBlank(),
                                modifier = Modifier.weight(1f).testTag("save_profile_button")
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOTAL AMOUNT PAID",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatCurrency(totalPaid),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Button(
                        onClick = onAddPaymentClick,
                        modifier = Modifier.testTag("profile_record_payment_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Payment")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Payment")
                    }
                }
            }
        }

        // Payment History Section Title
        item {
            Text(
                text = "Payment Ledger",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        // List of payments for this specific client
        if (payments.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No payments recorded for this client.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(payments) { payment ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = payment.serviceName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatDate(payment.date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatCurrency(payment.amount),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                
                                // Direct WhatsApp receipt confirmation click
                                IconButton(
                                    onClick = {
                                        val message = """
                                            *Payment Receipt* 🧾
                                            
                                            Hi *${client.name}*,
                                            
                                            Receipt confirming payment of *${formatCurrency(payment.amount)}* for:
                                            💼 *${payment.serviceName}*
                                            
                                            📅 Date: ${formatDate(payment.date)}
                                            
                                            Thank you!
                                        """.trimIndent()
                                        
                                        // Sanitize phone format
                                        val cleanPhone = client.phoneNumber.replace(Regex("[\\s\\-\\(\\)+]"), "")
                                        val intentUrl = "https://wa.me/$cleanPhone?text=${Uri.encode(message)}"
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open WhatsApp.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "WhatsApp Receipt Share",
                                        tint = Color(0xFF25D366) // WhatsApp brand green!
                                    )
                                }
                            }
                        }

                        if (payment.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = payment.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

private fun redactAadhaar(aadhaar: String): String {
    if (aadhaar.isBlank()) return "Not Provided"
    val clean = aadhaar.trim().replace(Regex("\\s+"), "")
    if (clean.length < 4) return "••••"
    val last4 = clean.takeLast(4)
    return "•••• •••• $last4"
}

// ==========================================
// 4. RECORD TRANSACTION SCREEN
// ==========================================
@Composable
fun AddTransactionScreen(
    initialClient: Client?,
    allClients: List<Client>,
    onSaveTransaction: (
        name: String,
        phone: String,
        email: String,
        photo: Uri?,
        accountHolder: String,
        bankAccount: String,
        upiId: String,
        aadhaar: String,
        amount: Double,
        service: String,
        notes: String,
        sendWhatsApp: Boolean
    ) -> Unit,
    onCancel: () -> Unit
) {
    var clientName by remember { mutableStateOf(initialClient?.name ?: "") }
    var clientPhone by remember { mutableStateOf(initialClient?.phoneNumber ?: "") }
    var clientEmail by remember { mutableStateOf(initialClient?.email ?: "") }
    var accountHolderName by remember { mutableStateOf(initialClient?.accountHolderName ?: "") }
    var bankAccountNumber by remember { mutableStateOf(initialClient?.bankAccountNumber ?: "") }
    var upiId by remember { mutableStateOf(initialClient?.upiId ?: "") }
    var aadhaarNumber by remember { mutableStateOf(initialClient?.aadhaarNumber ?: "") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    var transactionAmount by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var transactionNotes by remember { mutableStateOf("") }
    var sendWhatsAppReceipt by remember { mutableStateOf(true) }

    // Lock client input fields if initialized with a specific client
    val isClientLocked = initialClient != null

    val isFormValid = clientName.isNotBlank() && 
            clientPhone.isNotBlank() && 
            transactionAmount.toDoubleOrNull() != null && 
            transactionAmount.toDouble() > 0.0 &&
            serviceName.isNotBlank()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Section: Client Profile
        item {
            Text(
                text = "Client Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Profile Image Picker
                    if (!isClientLocked) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { imagePickerLauncher.launch("image/*") }
                                    .testTag("select_photo_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedPhotoUri != null) {
                                    AsyncImage(
                                        model = selectedPhotoUri,
                                        contentDescription = "Selected Photo",
                                        modifier = Modifier.size(64.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Choose Photo",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Client Photo",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Tap circle to upload a client profile photo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Client Name input
                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { if (!isClientLocked) clientName = it },
                        label = { Text("Client Full Name") },
                        enabled = !isClientLocked,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("client_name_input")
                    )

                    // Client Phone input
                    OutlinedTextField(
                        value = clientPhone,
                        onValueChange = {
                            if (!isClientLocked) {
                                clientPhone = it
                                // Smart duplicate / existing client detection
                                val matched = allClients.find { c -> c.phoneNumber.trim() == it.trim() }
                                if (matched != null) {
                                    clientName = matched.name
                                    clientEmail = matched.email
                                    accountHolderName = matched.accountHolderName
                                    bankAccountNumber = matched.bankAccountNumber
                                    upiId = matched.upiId
                                    aadhaarNumber = matched.aadhaarNumber
                                }
                            }
                        },
                        label = { Text("Phone Number") },
                        enabled = !isClientLocked,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("client_phone_input")
                    )

                    // Client Email input
                    OutlinedTextField(
                        value = clientEmail,
                        onValueChange = { if (!isClientLocked) clientEmail = it },
                        label = { Text("Email Address (Optional)") },
                        enabled = !isClientLocked,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("client_email_input")
                    )

                    if (!isClientLocked) {
                        Text(
                            text = "Banking & Identity (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )

                        // Account Holder Name
                        OutlinedTextField(
                            value = accountHolderName,
                            onValueChange = { accountHolderName = it },
                            label = { Text("Account Holder Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("client_holder_name_input")
                        )

                        // Bank Account Number
                        OutlinedTextField(
                            value = bankAccountNumber,
                            onValueChange = { bankAccountNumber = it },
                            label = { Text("Bank Account Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("client_bank_account_input")
                        )

                        // UPI ID
                        OutlinedTextField(
                            value = upiId,
                            onValueChange = { upiId = it },
                            label = { Text("UPI ID") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("client_upi_id_input")
                        )

                        // Aadhaar Number
                        OutlinedTextField(
                            value = aadhaarNumber,
                            onValueChange = { aadhaarNumber = it },
                            label = { Text("Aadhaar Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("client_aadhaar_input")
                        )
                    }
                }
            }
        }

        // Section: Payment / Transaction details
        item {
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Service Name
                    OutlinedTextField(
                        value = serviceName,
                        onValueChange = { serviceName = it },
                        label = { Text("Service or Product Name") },
                        singleLine = true,
                        placeholder = { Text("e.g. Consulting Fee, Haircut, Web Design") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("service_name_input")
                    )

                    // Payment Amount
                    OutlinedTextField(
                        value = transactionAmount,
                        onValueChange = { transactionAmount = it },
                        label = { Text("Amount Paid ($)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("transaction_amount_input")
                    )

                    // Notes
                    OutlinedTextField(
                        value = transactionNotes,
                        onValueChange = { transactionNotes = it },
                        label = { Text("Transaction Notes (Optional)") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_notes_input")
                    )
                }
            }
        }

        // WhatsApp receipt check box
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clickable { sendWhatsAppReceipt = !sendWhatsAppReceipt },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = sendWhatsAppReceipt,
                    onCheckedChange = { sendWhatsAppReceipt = it },
                    modifier = Modifier.testTag("whatsapp_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Automated WhatsApp Invoice",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Instantly redirects to send receipt confirmation on submit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Submit & Cancel buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cancel_transaction_button")
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val amount = transactionAmount.toDoubleOrNull() ?: 0.0
                        onSaveTransaction(
                            clientName,
                            clientPhone,
                            clientEmail,
                            selectedPhotoUri,
                            accountHolderName,
                            bankAccountNumber,
                            upiId,
                            aadhaarNumber,
                            amount,
                            serviceName,
                            transactionNotes,
                            sendWhatsAppReceipt
                        )
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("submit_transaction_button")
                ) {
                    Text("Save Record")
                }
            }
        }
    }
}

// ==========================================
// FORMAT UTILITIES
// ==========================================
private fun formatCurrency(amount: Double): String {
    return NumberFormat.getCurrencyInstance(Locale.US).format(amount)
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

// helper workaround to simplify mutableStateOf with Kotlin type-safe backing in Compose
@Composable
fun <T> rememberMutableStateOf(value: T) = remember { mutableStateOf(value) }
