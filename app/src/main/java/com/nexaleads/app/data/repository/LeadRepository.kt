package com.nexaleads.app.data.repository

import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.utils.PhoneUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.nexaleads.app.data.models.Product

class LeadRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    fun getLeadsForUser(userId: String): Flow<List<Lead>> = callbackFlow {
        val listener = db.collection("leads")
            .whereEqualTo("assignedTo", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val leads = snapshot.documents.mapNotNull { doc ->
                        Lead(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            phone = doc.getString("phone") ?: "",
                            source = doc.getString("source") ?: "",
                            status = doc.getString("status") ?: "New",
                            notes = doc.getString("notes") ?: "",
                            label = doc.getString("label") ?: "",
                            followUpDate = doc.getString("followUpDate"),
                            archived = doc.getBoolean("archived") ?: false,
                            assignedTo = doc.getString("assignedTo") ?: "",
                            product = doc.getString("product") ?: "",
                            address = doc.getString("address") ?: "",
                            city = doc.getString("city") ?: "",
                            pincode = doc.getString("pincode") ?: "",
                            paymentMethod = doc.getString("paymentMethod") ?: "",
                            orderAmount = doc.getString("orderAmount") ?: "",
                            subStatus = doc.getString("subStatus"),
                            followUpTimeSlot = doc.getString("followUpTimeSlot"),
                            paymentStatus = doc.getString("paymentStatus"),
                            isSuspiciousShortCall = doc.getBoolean("isSuspiciousShortCall") ?: false,
                            originalTotalValue = doc.getString("originalTotalValue") ?: "",
                            discountAmount = doc.getString("discountAmount") ?: ""
                        )
                    }
                    trySend(leads)
                }
            }
        
        awaitClose { listener.remove() }
    }

    fun getProducts(): Flow<List<Product>> = callbackFlow {
        val listener = db.collection("products")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList()) // Send empty list instead of crashing
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val products = snapshot.documents.mapNotNull { doc ->
                        Product(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            description = doc.getString("description") ?: "",
                            emojiIcon = doc.getString("emojiIcon") ?: "📦",
                            sortOrder = doc.getLong("sortOrder")?.toInt() ?: 1,
                            isActive = doc.getBoolean("isActive") ?: true
                        )
                    }
                    trySend(products.sortedBy { it.sortOrder })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun seedProductsIfEmpty() {
        try {
            val snapshot = db.collection("products").limit(1).get().await()
            if (snapshot.isEmpty) {
                val batch = db.batch()
                val defaultProducts = listOf(
                    Product(id = "prod_1", name = "Spirulina", price = 999.0, description = "Premium Organic Spirulina", emojiIcon = "🌿", sortOrder = 1),
                    Product(id = "prod_2", name = "Sea Buckthorn", price = 1299.0, description = "Himalayan Sea Buckthorn Juice", emojiIcon = "🥃", sortOrder = 2),
                    Product(id = "prod_3", name = "Spirulina Face Pack", price = 499.0, description = "Rejuvenating Face Pack", emojiIcon = "🧴", sortOrder = 3),
                    Product(id = "prod_4", name = "Spirulina Cookies", price = 299.0, description = "Healthy Snack Cookies", emojiIcon = "🍪", sortOrder = 4),
                    Product(id = "prod_5", name = "Multiple / Combos", price = 0.0, description = "Custom combo package", emojiIcon = "📦", sortOrder = 5)
                )
                for (product in defaultProducts) {
                    val docRef = db.collection("products").document(product.id)
                    batch.set(docRef, product)
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getRecentInteractions(userId: String): List<Interaction> {
        return try {
            val snapshot = db.collection("interactions")
                .whereEqualTo("callerId", userId)
                .get()
                .await()
            
            val interactionsList = snapshot.documents.mapNotNull { doc ->
                Interaction(
                    id = doc.id,
                    leadId = doc.getString("leadId") ?: "",
                    callerId = doc.getString("callerId") ?: "",
                    callerName = doc.getString("callerName") ?: "",
                    statusBefore = doc.getString("statusBefore") ?: "",
                    statusAfter = doc.getString("statusAfter") ?: "",
                    notes = doc.getString("notes") ?: "",
                    timestamp = doc.getString("timestamp") ?: "",
                    duration = doc.getLong("duration")?.toInt() ?: 0,
                    followUpDate = doc.getString("followUpDate"),
                    isVisitLog = doc.getBoolean("isVisitLog") ?: false,
                    subStatus = doc.getString("subStatus"),
                    followUpTimeSlot = doc.getString("followUpTimeSlot"),
                    paymentStatus = doc.getString("paymentStatus"),
                    isSuspiciousShortCall = doc.getBoolean("isSuspiciousShortCall") ?: false,
                    product = doc.getString("product"),
                    address = doc.getString("address"),
                    city = doc.getString("city"),
                    pincode = doc.getString("pincode"),
                    paymentMethod = doc.getString("paymentMethod"),
                    orderAmount = doc.getString("orderAmount")
                )
            }
            
            // Sort locally to bypass Firestore composite index requirement
            interactionsList.sortedByDescending { it.timestamp }.take(50)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateLead(leadId: String, updates: Map<String, Any?>) {
        db.collection("leads").document(leadId).update(updates).await()
    }

    suspend fun addInteraction(interaction: Interaction) {
        db.collection("interactions").document(interaction.id).set(interaction).await()
    }
    
    suspend fun deleteInteraction(interactionId: String) {
        db.collection("interactions").document(interactionId).delete().await()
    }

    suspend fun recalculateLeadStateAndBatch(leadId: String, interactionIdToDelete: String): Boolean {
        return try {
            val leadSnapshot = db.collection("leads").document(leadId).get().await()
            val currentNotes = leadSnapshot.getString("notes") ?: ""
            val safeMetaDump = if (currentNotes.contains("\n\n📞 ")) {
                currentNotes.substringBefore("\n\n📞 ")
            } else {
                if (currentNotes.isNotEmpty() && !currentNotes.contains("📞 ")) currentNotes else ""
            }

            val interactionsSnapshot = db.collection("interactions")
                .whereEqualTo("leadId", leadId)
                .get()
                .await()
            
            val remainingInteractions = interactionsSnapshot.documents
                .filter { it.id != interactionIdToDelete }
                .mapNotNull { doc ->
                    Interaction(
                        id = doc.id,
                        leadId = doc.getString("leadId") ?: "",
                        callerId = doc.getString("callerId") ?: "",
                        callerName = doc.getString("callerName") ?: "",
                        statusBefore = doc.getString("statusBefore") ?: "",
                        statusAfter = doc.getString("statusAfter") ?: "",
                        notes = doc.getString("notes") ?: "",
                        timestamp = doc.getString("timestamp") ?: "",
                        duration = doc.getLong("duration")?.toInt() ?: 0,
                    followUpDate = doc.getString("followUpDate"),
                    isVisitLog = doc.getBoolean("isVisitLog") ?: false,
                    subStatus = doc.getString("subStatus"),
                    followUpTimeSlot = doc.getString("followUpTimeSlot"),
                    paymentStatus = doc.getString("paymentStatus"),
                    isSuspiciousShortCall = doc.getBoolean("isSuspiciousShortCall") ?: false,
                    product = doc.getString("product"),
                    address = doc.getString("address"),
                    city = doc.getString("city"),
                    pincode = doc.getString("pincode"),
                    paymentMethod = doc.getString("paymentMethod"),
                    orderAmount = doc.getString("orderAmount")
                )
            }
            .sortedBy { it.timestamp }

        var rebuiltNotes = safeMetaDump
        remainingInteractions.forEach { interaction ->
            if (interaction.notes.trim().isNotEmpty()) {
                if (rebuiltNotes.isEmpty()) {
                    rebuiltNotes = interaction.notes.trim()
                } else {
                    rebuiltNotes += "\n\n📞 ${interaction.notes.trim()}"
                }
            }
        }

        val latestInteraction = remainingInteractions.lastOrNull()
        val finalStatus = latestInteraction?.statusAfter ?: "Pending"
        val finalFollowUpDate = latestInteraction?.followUpDate
        val finalSubStatus = latestInteraction?.subStatus
        val finalTimeSlot = latestInteraction?.followUpTimeSlot
        val finalPaymentStatus = latestInteraction?.paymentStatus

        val latestOrderInteraction = remainingInteractions.lastOrNull { 
            Constants.normalizeStatus(it.statusAfter) == Constants.STATUS_ORDER_PLACED || !it.product.isNullOrEmpty() 
        }

        val updateMap = mutableMapOf<String, Any?>(
            "status" to finalStatus,
            "notes" to rebuiltNotes,
            "followUpDate" to finalFollowUpDate,
            "subStatus" to finalSubStatus,
            "followUpTimeSlot" to finalTimeSlot,
            "paymentStatus" to finalPaymentStatus
        )

        if (Constants.normalizeStatus(finalStatus) == Constants.STATUS_ORDER_PLACED && latestOrderInteraction != null) {
            updateMap["product"] = latestOrderInteraction.product ?: ""
            updateMap["address"] = latestOrderInteraction.address ?: ""
            updateMap["city"] = latestOrderInteraction.city ?: ""
            updateMap["pincode"] = latestOrderInteraction.pincode ?: ""
            updateMap["paymentMethod"] = latestOrderInteraction.paymentMethod ?: ""
            updateMap["orderAmount"] = latestOrderInteraction.orderAmount ?: ""
        } else if (Constants.normalizeStatus(finalStatus) != Constants.STATUS_ORDER_PLACED) {
            // Wipe ghost order data when reverting from an order status!
            updateMap["product"] = ""
            updateMap["address"] = ""
            updateMap["city"] = ""
            updateMap["pincode"] = ""
            updateMap["paymentMethod"] = ""
            updateMap["orderAmount"] = ""
        }

        val batch = db.batch()
        val leadRef = db.collection("leads").document(leadId)
        batch.update(leadRef, updateMap)
            
            val interactionRef = db.collection("interactions").document(interactionIdToDelete)
            batch.delete(interactionRef)
            
            batch.commit().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun checkDuplicateLead(phone: String): Lead? {
        return try {
            val sanitized = PhoneUtils.sanitizePhoneNumber(phone)
            
            // Check exact input just in case
            var snapshot = db.collection("leads").whereEqualTo("phone", phone).limit(1).get().await()
            
            if (snapshot.isEmpty) {
                // Check purely sanitized
                snapshot = db.collection("leads").whereEqualTo("phone", sanitized).limit(1).get().await()
            }
            if (snapshot.isEmpty) {
                // Check sanitized with +91
                snapshot = db.collection("leads").whereEqualTo("phone", "+91$sanitized").limit(1).get().await()
            }
            if (snapshot.isEmpty) {
                // Check sanitized with 0
                snapshot = db.collection("leads").whereEqualTo("phone", "0$sanitized").limit(1).get().await()
            }
            if (snapshot.isEmpty && phone.startsWith("+91")) {
                val withoutCode = phone.removePrefix("+91").trim()
                snapshot = db.collection("leads").whereEqualTo("phone", withoutCode).limit(1).get().await()
            }
            
            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                Lead(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    phone = doc.getString("phone") ?: "",
                    source = doc.getString("source") ?: "",
                    status = doc.getString("status") ?: "New",
                    notes = doc.getString("notes") ?: "",
                    label = doc.getString("label") ?: "",
                    followUpDate = doc.getString("followUpDate"),
                    archived = doc.getBoolean("archived") ?: false,
                    assignedTo = doc.getString("assignedTo") ?: "",
                    product = doc.getString("product") ?: "",
                    address = doc.getString("address") ?: "",
                    city = doc.getString("city") ?: "",
                    pincode = doc.getString("pincode") ?: "",
                    paymentMethod = doc.getString("paymentMethod") ?: "",
                    orderAmount = doc.getString("orderAmount") ?: ""
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createManualLeadBatch(lead: Lead, interaction: Interaction): String? {
        return try {
            val batch = db.batch()
            
            val leadRef = db.collection("leads").document(lead.id)
            batch.set(leadRef, lead)

            val interactionRef = db.collection("interactions").document(interaction.id)
            batch.set(interactionRef, interaction)

            batch.commit().await()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Unknown Firebase Error"
        }
    }
}
