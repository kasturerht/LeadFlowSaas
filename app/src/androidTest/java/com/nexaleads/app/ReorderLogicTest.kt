package com.nexaleads.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.repository.LeadRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReorderLogicTest {

    private lateinit var db: FirebaseFirestore
    private lateinit var repository: LeadRepository
    private val orgId = "test-org-id"

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
    fun testReorderCreatesOrderDocument() = runBlocking {
        // 1. Create original lead
        val originalLeadId = "l-" + UUID.randomUUID().toString()
        val originalLead = Lead(
            id = originalLeadId,
            name = "Test Customer",
            phone = "9999999999",
            status = "Delivered"
        )
        val initialInteraction = Interaction(
            id = "i-" + UUID.randomUUID().toString(),
            leadId = originalLeadId,
            statusAfter = "Delivered"
        )
        repository.createManualLeadBatch(originalLead, initialInteraction)
        
        // 2. Simulate CallingViewModel.createReorder (Creating duplicate lead)
        val duplicateLeadId = "l-" + UUID.randomUUID().toString()
        val duplicateLead = Lead(
            id = duplicateLeadId,
            parentLeadId = originalLeadId,
            isReorder = true,
            status = "Order Placed",
            orderAmount = "5000",
            orderAmountNum = 5000L
        )
        val orderId = "o-" + UUID.randomUUID().toString()
        val reorderInteraction = Interaction(
            id = "i-" + UUID.randomUUID().toString(),
            leadId = duplicateLeadId,
            statusAfter = "Order Placed",
            associatedOrderId = orderId
        )
        
        val order = com.nexaleads.app.data.model.Order(
            id = orderId,
            customerId = duplicateLeadId,
            customerPhone = duplicateLead.phone,
            orderAmount = "5000",
            orderAmountNum = 5000L,
            status = "Order Placed",
            isReorder = true
        )
        
        repository.createReorderBatch(duplicateLead, order, reorderInteraction)

        // 3. ASSERTION: Order should exist!
        val ordersSnapshot = db.collection("organizations").document(orgId)
            .collection("orders_data")
            .whereEqualTo("customerId", duplicateLeadId)
            .get()
            .await()

        assertTrue("Order document was NOT created for the Reorder! Data Loss Bug exists.", !ordersSnapshot.isEmpty)
    }
}
