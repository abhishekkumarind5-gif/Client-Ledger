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
     * If they exist, add transaction to existing client. Otherwise, create new client and add transaction.
     */
    suspend fun recordTransaction(
        clientName: String,
        clientPhone: String,
        clientEmail: String,
        clientPhotoPath: String?,
        amount: Double,
        serviceName: String,
        notes: String
    ): Pair<Client, Long> {
        val sanitizedPhone = clientPhone.trim()
        val existingClient = ledgerDao.getClientByPhone(sanitizedPhone)

        val client = if (existingClient != null) {
            // Client already exists! If photo is updated, update the client.
            val updatedClient = if (clientPhotoPath != null && existingClient.profilePhotoPath == null) {
                val c = existingClient.copy(profilePhotoPath = clientPhotoPath)
                ledgerDao.updateClient(c)
                c
            } else {
                existingClient
            }
            updatedClient
        } else {
            // Client doesn't exist yet, create a new one!
            val newClient = Client(
                name = clientName.trim(),
                phoneNumber = sanitizedPhone,
                email = clientEmail.trim(),
                profilePhotoPath = clientPhotoPath
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
