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
import com.nexaleads.app.data.models.ComboItem
import com.nexaleads.app.data.models.Category

class LeadRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    fun getLeadsForUser(userId: String): Flow<List<Lead>> = callbackFlow {
        val listener = db.collection("leads")
            .whereEqualTo("assignedTo", userId)
            .whereEqualTo("archived", false)
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
                            discountAmount = doc.getString("discountAmount") ?: "",
                            convertedAt = doc.getString("convertedAt"),
                            dispatchStatus = doc.getString("dispatchStatus") ?: "",
                            cancellationReason = doc.getString("cancellationReason") ?: "",
                            cancellationNotes = doc.getString("cancellationNotes") ?: "",
                            cancellationRequestedAt = doc.getString("cancellationRequestedAt") ?: ""
                        )
                    }
                    trySend(leads)
                }
            }
        
        awaitClose { listener.remove() }
    }

    fun getCategories(): Flow<List<Category>> = callbackFlow {
        val listener = db.collection("categories")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val categories = snapshot.documents.mapNotNull { doc ->
                        Category(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            color = doc.getString("color") ?: "#ffffff",
                            icon = doc.getString("icon") ?: "📦",
                            isActive = doc.getBoolean("isActive") ?: true
                        )
                    }
                    trySend(categories.sortedBy { it.name })
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
                            isActive = doc.getBoolean("isActive") ?: true,
                            isCombo = doc.getBoolean("isCombo") ?: false,
                            categoryIds = doc.get("categoryIds") as? List<String> ?: emptyList()
                        )
                    }
                    trySend(products.sortedBy { it.sortOrder })
                }
            }
        awaitClose { listener.remove() }
    }

    
    suspend fun forceSeedProducts() {
        try {
            val batch = db.batch()
            val newProducts = listOf(
                // 1. Health Supplements & Combos
                Product(id = "prod_1", name = "Spirulina capsule 60 1 nos", price = 550.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 1),
                Product(id = "prod_2", name = "Spirulina tablets 120 1 nos", price = 699.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 2),
                Product(id = "prod_3", name = "Spirulina tablets 3 nos combo", price = 1800.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 3, isCombo = true, comboItems = listOf(ComboItem("prod_2", 3))),
                Product(id = "prod_4", name = "Spirulina capsule 3 nos combo", price = 1600.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 4, isCombo = true, comboItems = listOf(ComboItem("prod_1", 3))),
                Product(id = "prod_5", name = "Seabuckthorn 1 nos prepaid", price = 600.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 5),
                Product(id = "prod_6", name = "seabuckthorn 2 nos combo", price = 1200.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 6, isCombo = true, comboItems = listOf(ComboItem("prod_5", 2))),
                Product(id = "prod_7", name = "3 months combo spirulina seabuckthorn", price = 3600.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 7, isCombo = true, comboItems = listOf(ComboItem("prod_2", 3), ComboItem("prod_5", 3))),
                
                // 2. Cosmetics & Personal Care
                Product(id = "prod_8", name = "Hair oil 100 ml", price = 200.0, description = "Personal Care", emojiIcon = "🧴", sortOrder = 8),
                Product(id = "prod_9", name = "shampoo 100 ml", price = 200.0, description = "Personal Care", emojiIcon = "🧴", sortOrder = 9),
                Product(id = "prod_10", name = "facewash 100 ml", price = 200.0, description = "Personal Care", emojiIcon = "🧴", sortOrder = 10),
                Product(id = "prod_11", name = "soap", price = 60.0, description = "Personal Care", emojiIcon = "🧴", sortOrder = 11),
                Product(id = "prod_12", name = "spirulina facepack 50 gram", price = 150.0, description = "Personal Care", emojiIcon = "🧴", sortOrder = 12),
                Product(id = "prod_13", name = "spirulina korean cream 25 gram", price = 999.0, description = "Personal Care", emojiIcon = "🧴", sortOrder = 13),
                Product(id = "prod_14", name = "spirulina bride cream 25 gram", price = 1199.0, description = "Personal Care", emojiIcon = "🧴", sortOrder = 14),
                
                // 3. Powders & Extract Tablets
                Product(id = "prod_15", name = "moringa powder 100 gram", price = 200.0, description = "Powders & Extracts", emojiIcon = "🌿", sortOrder = 15),
                Product(id = "prod_16", name = "beet root powder 100 gram", price = 200.0, description = "Powders & Extracts", emojiIcon = "🌿", sortOrder = 16),
                Product(id = "prod_17", name = "Amla powder 100 gram", price = 200.0, description = "Powders & Extracts", emojiIcon = "🌿", sortOrder = 17),
                Product(id = "prod_18", name = "ashwagandha 100 gram powder", price = 200.0, description = "Powders & Extracts", emojiIcon = "🌿", sortOrder = 18),
                Product(id = "prod_19", name = "ashwagandha extract tablets 60", price = 350.0, description = "Powders & Extracts", emojiIcon = "🌿", sortOrder = 19),
                Product(id = "prod_20", name = "Moringa extract 60 tab", price = 350.0, description = "Powders & Extracts", emojiIcon = "🌿", sortOrder = 20),
                
                // 4. Edibles
                Product(id = "prod_21", name = "spirulina cookies 200 gram", price = 200.0, description = "Edibles", emojiIcon = "🍪", sortOrder = 21)
            )
            for (product in newProducts) {
                val docRef = db.collection("products").document(product.id)
                batch.set(docRef, product)
            }
            batch.commit().await()
            android.util.Log.d("LeadRepository", "Successfully synced 21 products to Firestore!")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("LeadRepository", "Failed to sync products: ${e.message}")
        }
    }

    suspend fun seedProductsIfEmpty() {
        try {
            val snapshot = db.collection("products").limit(1).get().await()
            if (snapshot.isEmpty) {
                forceSeedProducts()
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
            updateMap["convertedAt"] = null
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
                    orderAmount = doc.getString("orderAmount") ?: "",
                    convertedAt = doc.getString("convertedAt"),
                    dispatchStatus = doc.getString("dispatchStatus") ?: "",
                    cancellationReason = doc.getString("cancellationReason") ?: "",
                    cancellationNotes = doc.getString("cancellationNotes") ?: "",
                    cancellationRequestedAt = doc.getString("cancellationRequestedAt") ?: ""
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
