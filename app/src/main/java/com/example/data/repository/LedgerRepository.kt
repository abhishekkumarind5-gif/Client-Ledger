package com.example.data.repository

import com.example.data.dao.LedgerDao
import com.example.data.model.Client
import com.example.data.model.Payment
import com.example.data.model.TransactionItem
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val ledgerDao: LedgerDao) {

    val allClients: Flow<List<Client>> = ledgerDao.getAllClients()
    val recentTransactions: Flow<List<TransactionItem>> = ledgerDao.getRecentTransactions()

    suspend fun getClientById(clientId: Long): Client? {
        return ledgerDao.getClientById(clientId)
    }

    fun getPaymentsForClient(clientId: Long): Flow<List<Payment>> {
        return ledgerDao.getPaymentsForClient(clientId)
    }

    suspend fun insertClient(client: Client): Long {
        return ledgerDao.insertClient(client)
    }

    suspend fun updateClient(client: Client) {
        ledgerDao.updateClient(client)
    }

    suspend fun deleteClient(client: Client) {
        ledgerDao.deletePaymentsForClient(client.id)
        ledgerDao.deleteClient(client)
    }

    suspend fun insertPayment(payment: Payment): Long {
        return ledgerDao.insertPayment(payment)
    }

    suspend fun deletePayment(paymentId: Long) {
        ledgerDao.deletePaymentById(paymentId)
    }

    /**
     * core business logic: detect if a client already exists by phone number.
     * If they exist, update their profile with the new fields (smart merge) and add transaction.
     * Otherwise, create a new client with the extended profile and add transaction.
     */
    suspend fun recordTransaction(
        clientName: String,
        clientPhone: String,
        clientEmail: String,
        clientPhotoPath: String?,
        accountHolderName: String,
        bankAccountNumber: String,
        upiId: String,
        aadhaarNumber: String,
        amount: Double,
        serviceName: String,
        notes: String
    ): Pair<Client, Long> {
        val sanitizedPhone = clientPhone.trim()
        val existingClient = ledgerDao.getClientByPhone(sanitizedPhone)

        val client = if (existingClient != null) {
            // Client already exists! Update their details with newly entered info if any.
            val updatedClient = existingClient.copy(
                name = if (clientName.isNotBlank()) clientName.trim() else existingClient.name,
                email = if (clientEmail.isNotBlank()) clientEmail.trim() else existingClient.email,
                profilePhotoPath = clientPhotoPath ?: existingClient.profilePhotoPath,
                accountHolderName = if (accountHolderName.isNotBlank()) accountHolderName.trim() else existingClient.accountHolderName,
                bankAccountNumber = if (bankAccountNumber.isNotBlank()) bankAccountNumber.trim() else existingClient.bankAccountNumber,
                upiId = if (upiId.isNotBlank()) upiId.trim() else existingClient.upiId,
                aadhaarNumber = if (aadhaarNumber.isNotBlank()) aadhaarNumber.trim() else existingClient.aadhaarNumber
            )
            ledgerDao.updateClient(updatedClient)
            updatedClient
        } else {
            // Client doesn't exist yet, create a new one!
            val newClient = Client(
                name = clientName.trim(),
                phoneNumber = sanitizedPhone,
                email = clientEmail.trim(),
                profilePhotoPath = clientPhotoPath,
                accountHolderName = accountHolderName.trim(),
                bankAccountNumber = bankAccountNumber.trim(),
                upiId = upiId.trim(),
                aadhaarNumber = aadhaarNumber.trim()
            )
            val newId = ledgerDao.insertClient(newClient)
            newClient.copy(id = newId)
        }

        // Add payment to the client
        val payment = Payment(
            clientId = client.id,
            amount = amount,
            serviceName = serviceName,
            notes = notes
        )
        val paymentId = ledgerDao.insertPayment(payment)

        return Pair(client, paymentId)
    }
}
