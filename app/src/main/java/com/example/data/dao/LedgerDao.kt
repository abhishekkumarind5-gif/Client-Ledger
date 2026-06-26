package com.example.data.dao

import androidx.room.*
import com.example.data.model.Client
import com.example.data.model.Payment
import com.example.data.model.TransactionItem
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    
    // Client Queries
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE id = :clientId LIMIT 1")
    suspend fun getClientById(clientId: Long): Client?

    @Query("SELECT * FROM clients WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getClientByPhone(phone: String): Client?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    // Payment Queries
    @Query("SELECT * FROM payments WHERE clientId = :clientId ORDER BY date DESC")
    fun getPaymentsForClient(clientId: Long): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Query("DELETE FROM payments WHERE clientId = :clientId")
    suspend fun deletePaymentsForClient(clientId: Long)

    @Query("DELETE FROM payments WHERE id = :paymentId")
    suspend fun deletePaymentById(paymentId: Long)

    // Joined Queries
    @Query("""
        SELECT 
            p.id as paymentId, 
            c.id as clientId, 
            c.name as clientName, 
            c.phoneNumber as clientPhone, 
            c.profilePhotoPath as clientPhotoPath, 
            p.amount as amount, 
            p.serviceName as serviceName, 
            p.date as date, 
            p.notes as notes
        FROM payments p
        JOIN clients c ON p.clientId = c.id
        ORDER BY p.date DESC
    """)
    fun getRecentTransactions(): Flow<List<TransactionItem>>
}
