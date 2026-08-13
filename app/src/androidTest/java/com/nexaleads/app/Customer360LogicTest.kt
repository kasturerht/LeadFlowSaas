package com.nexaleads.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.repository.LeadRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Customer360LogicTest {

    private lateinit var db: FirebaseFirestore
    private lateinit var repository: LeadRepository
    private val orgId = "test-org-id"
    private val customerPhone = "8888888888"

    @Before
    fun setup() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        
        db = FirebaseFirestore.getInstance()
        
        try {
            db.useEmulator("192.168.29.76", 8080)
        } catch (e: Exception) { }
        
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()
            )
            .build()
        db.firestoreSettings = settings

        repository = LeadRepository(db)
        repository.setOrgId(orgId)
    }

    @Test
    fun testCustomer360AccurateLTVAndOrderCount() = runBlocking {
        // 1. Create Initial Lead and Order (5000)
        val initialLeadId = "l-" + UUID.randomUUID().toString()
        val initialLead = Lead(
            id = initialLeadId,
            phone = customerPhone,
            status = "Order Placed",
            orderAmountNum = 5000L
        )
        val initialOrder = com.nexaleads.app.data.model.Order(
            id = "o-" + UUID.randomUUID().toString(),
            customerId = initialLeadId,
            customerPhone = customerPhone,
            orderAmountNum = 5000L,
            createdAtMillis = System.currentTimeMillis() - 100000 // Older order
        )
        val initialInteraction = Interaction(
            id = "i-" + UUID.randomUUID().toString(),
            leadId = initialLeadId,
            statusAfter = "Order Placed"
        )
        repository.createReorderBatch(initialLead, initialOrder, initialInteraction)
        
        // 2. Create Reorder Lead and Order (3000) - Different Lead ID, same phone
        val reorderLeadId = "l-" + UUID.randomUUID().toString()
        val reorderLead = Lead(
            id = reorderLeadId,
            phone = customerPhone,
            parentLeadId = initialLeadId,
            isReorder = true,
            status = "Order Placed",
            orderAmountNum = 3000L
        )
        val reorderOrder = com.nexaleads.app.data.model.Order(
            id = "o-" + UUID.randomUUID().toString(),
            customerId = reorderLeadId,
            customerPhone = customerPhone,
            orderAmountNum = 3000L,
            createdAtMillis = System.currentTimeMillis() // Newer order
        )
        val reorderInteraction = Interaction(
            id = "i-" + UUID.randomUUID().toString(),
            leadId = reorderLeadId,
            statusAfter = "Order Placed"
        )
        repository.createReorderBatch(reorderLead, reorderOrder, reorderInteraction)

        // 3. Simulate Customer360ViewModel logic
        val customerOrders = repository.getOrdersForCustomer(customerPhone).first()
        
        // Locally sort as per our new architecture fix
        val sortedOrders = customerOrders.sortedByDescending { it.createdAtMillis }
        
        val totalOrders = sortedOrders.size
        val lifetimeValue = sortedOrders.sumOf { it.orderAmountNum }
        
        // 4. Assertions
        assertEquals("Total Orders should be 2 regardless of how many leads were created", 2, totalOrders)
        assertEquals("LTV should be exactly 8000 by dynamically summing orders", 8000L, lifetimeValue)
    }
}
