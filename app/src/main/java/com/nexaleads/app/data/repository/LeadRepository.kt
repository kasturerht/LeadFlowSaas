package com.nexaleads.app.data.repository

import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.getPrimaryCategory
import com.nexaleads.app.utils.PhoneUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import javax.inject.Inject
import com.nexaleads.app.data.models.Product
import com.nexaleads.app.data.models.Category

class LeadRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private var orgId: String = ""

    fun setOrgId(id: String) {
        orgId = id
    }

    private fun leadsCol() = db.collection("organizations").document(orgId).collection("leads")
    private fun interactionsCol() = db.collection("organizations").document(orgId).collection("interactions")
    private fun productsCol() = db.collection("organizations").document(orgId).collection("products")
    private fun categoriesCol() = db.collection("organizations").document(orgId).collection("categories")
    fun getLeadsForUser(userId: String, limit: Long = 100): Flow<List<Lead>> = callbackFlow {
        val listener = leadsCol()
            .whereEqualTo("assignedTo", userId)
            .whereEqualTo("archived", false)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val leads = snapshot.documents.mapNotNull { doc -> parseLead(doc) }
                    trySend(leads)
                }
            }
        
        awaitClose { listener.remove() }
    }

    fun getRetentionDueLeads(userId: String, limit: Long = 100): Flow<List<Lead>> = callbackFlow {
        val now = System.currentTimeMillis()
        val next7Days = now + (7L * 24 * 60 * 60 * 1000)
        
        val listener = leadsCol()
            .whereEqualTo("assignedTo", userId)
            .whereEqualTo("archived", false)
            .whereLessThanOrEqualTo("exhaustionTimestamp", next7Days)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val leads = snapshot.documents.mapNotNull { doc -> parseLead(doc) }
                    trySend(leads)
                }
            }
        awaitClose { listener.remove() }
    }

    fun searchLeads(query: String, userId: String, limit: Long = 50): Flow<List<Lead>> = callbackFlow {
        val sanitizedQuery = query.lowercase().trim()
        if (sanitizedQuery.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        
        val listener = leadsCol()
            .whereEqualTo("assignedTo", userId)
            .whereEqualTo("archived", false)
            .whereArrayContains("searchKeywords", sanitizedQuery)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val leads = snapshot.documents.mapNotNull { doc -> parseLead(doc) }
                    trySend(leads)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getCategories(): Flow<List<Category>> = callbackFlow {
        val listener = categoriesCol()
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
                            isActive = doc.getBoolean("isActive") ?: true,
                            order = doc.getLong("order")?.toInt() ?: 0
                        )
                    }
                    trySend(categories.sortedBy { it.name })
                }
            }
        awaitClose { listener.remove() }
    }

    fun getDashboardMetricsFlow(userId: String): Flow<Map<String, Long>> = callbackFlow {
        val baseQuery = leadsCol().whereEqualTo("assignedTo", userId).whereEqualTo("archived", false)
        
        val listener = baseQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(mapOf(
                    "freshLeads" to 0L, "dueFollowups" to 0L, "confirmedOrders" to 0L,
                    "pendingPayments" to 0L, "attempted" to 0L, "rejected" to 0L,
                    "dispatched" to 0L, "delivered" to 0L, "rto" to 0L, "inquiries" to 0L
                ))
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                var freshLeads = 0L
                var dueFollowups = 0L
                var confirmedOrders = 0L
                var pendingPayments = 0L
                var attempted = 0L
                var rejected = 0L
                var dispatched = 0L
                var delivered = 0L
                var rto = 0L
                var inquiries = 0L
                
                for (doc in snapshot.documents) {
                    val lead = parseLead(doc) ?: continue
                    val category = lead.getPrimaryCategory()
                    
                    when (category) {
                        "PENDING" -> freshLeads++
                        "FOLLOWUP" -> dueFollowups++
                        "INQUIRY" -> inquiries++
                        "ATTEMPTED" -> attempted++
                        "REJECTED" -> rejected++
                        "DISPATCHED" -> dispatched++
                        "DELIVERED" -> delivered++
                        "RTO" -> rto++
                        "CONVERTED" -> {
                            val isPending = lead.paymentMethod.equals("Prepaid", ignoreCase = true) && 
                                            lead.paymentStatus?.equals("Link Sent", ignoreCase = true) == true
                            if (isPending) pendingPayments++ else confirmedOrders++
                        }
                    }
                }
                
                trySend(mapOf(
                    "freshLeads" to freshLeads,
                    "dueFollowups" to dueFollowups,
                    "confirmedOrders" to confirmedOrders,
                    "pendingPayments" to pendingPayments,
                    "attempted" to attempted,
                    "rejected" to rejected,
                    "dispatched" to dispatched,
                    "delivered" to delivered,
                    "rto" to rto,
                    "inquiries" to inquiries
                ))
            }
        }
        
        awaitClose { listener.remove() }
    }

    fun getSalesMetricsFlow(userId: String): Flow<Map<String, Long>> = callbackFlow {
        val listener = leadsCol()
            .whereEqualTo("assignedTo", userId)
            .whereEqualTo("archived", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(mapOf("totalActive" to 0L, "todayCount" to 0L, "todayRev" to 0L, "weekRev" to 0L))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    val todayStr = isoFormat.format(java.util.Date())
                    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                    cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
                    val lastWeekStr = isoFormat.format(cal.time)

                    var todayCount = 0L
                    var todayRev = 0L
                    var weekRev = 0L
                    var totalActive = 0L

                    for (doc in snapshot.documents) {
                        val lead = parseLead(doc) ?: continue
                        val category = lead.getPrimaryCategory()
                        
                        if (category != "CONVERTED" && category != "DISPATCHED" && category != "DELIVERED") continue
                        
                        val isPending = lead.paymentMethod.equals("Prepaid", ignoreCase = true) && 
                                        lead.paymentStatus?.equals("Link Sent", ignoreCase = true) == true
                        if (isPending) continue
                        
                        totalActive++
                        val fallbackCAt = lead.updatedAt?.let {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(it))
                        }
                        val cAt = lead.convertedAt ?: fallbackCAt ?: continue
                        val amtNum = lead.orderAmountNum

                        if (cAt >= todayStr) {
                            todayCount++
                            todayRev += amtNum
                        }
                        if (cAt >= lastWeekStr) {
                            weekRev += amtNum
                        }
                    }
                    trySend(mapOf(
                        "totalActive" to totalActive,
                        "todayCount" to todayCount,
                        "todayRev" to todayRev,
                        "weekRev" to weekRev
                    ))
                }
            }
        awaitClose { listener.remove() }
    }
    
    suspend fun fetchTodayPipelineActivity(userId: String): List<Lead> {
        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val todayStr = isoFormat.format(java.util.Date())
        
        return try {
            val snapshot = leadsCol()
                .whereEqualTo("assignedTo", userId)
                .whereGreaterThanOrEqualTo("updatedAt", todayStr)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
                
            snapshot.documents.mapNotNull { doc ->
                parseLead(doc)
            }
        } catch (e: Exception) {
            if (e.message?.contains("FAILED_PRECONDITION") == true) {
                throw Exception("INDEX_REQUIRED: ${e.message}")
            }
            emptyList()
        }
    }
    
    fun getProducts(): Flow<List<Product>> = callbackFlow {
        val listener = productsCol()
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val products = snapshot.documents.mapNotNull { doc ->
                        try {
                            val bundledList = (doc.get("bundledProducts") as? List<Map<String, Any>>)?.mapNotNull { item ->
                                val pId = item["productId"] as? String
                                val qty = (item["quantity"] as? Number)?.toInt() ?: 1
                                if (pId != null) com.nexaleads.app.data.models.BundledProduct(pId, qty) else null
                            } ?: emptyList()

                        Product(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            mrp = doc.getDouble("mrp") ?: 0.0,
                            offerPrice = doc.getDouble("offerPrice") ?: 0.0,
                            bottomPrice = doc.getDouble("bottomPrice") ?: 0.0,
                            shippingFee = doc.getDouble("shippingFee") ?: 50.0,
                            description = doc.getString("description") ?: "",
                            emojiIcon = doc.getString("emojiIcon") ?: "📦",
                            sortOrder = doc.getLong("sortOrder")?.toInt() ?: 1,
                            isActive = doc.getBoolean("isActive") ?: true,
                            type = doc.getString("type") ?: "single",
                            bundledProducts = bundledList,
                            consumptionDays = doc.getLong("consumptionDays")?.toInt() ?: 30,
                            categoryIds = doc.get("categoryIds") as? List<String> ?: emptyList()
                        )
                        } catch (e: Exception) {
                            com.nexaleads.app.data.models.Product(id = "error_id", name = "Parsing Error: ${e.message}", description = "Firestore Error", price = 0.0)
                        }
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
                Product(id = "prod_3", name = "Spirulina tablets 3 nos combo", price = 1800.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 3, type = "combo", bundledProducts = listOf(com.nexaleads.app.data.models.BundledProduct("prod_2", 3))),
                Product(id = "prod_4", name = "Spirulina capsule 3 nos combo", price = 1600.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 4, type = "combo", bundledProducts = listOf(com.nexaleads.app.data.models.BundledProduct("prod_1", 3))),
                Product(id = "prod_5", name = "Seabuckthorn 1 nos prepaid", price = 600.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 5),
                Product(id = "prod_6", name = "seabuckthorn 2 nos combo", price = 1200.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 6, type = "combo", bundledProducts = listOf(com.nexaleads.app.data.models.BundledProduct("prod_5", 2))),
                Product(id = "prod_7", name = "3 months combo spirulina seabuckthorn", price = 3600.0, description = "Health Supplements", emojiIcon = "💊", sortOrder = 7, type = "combo", bundledProducts = listOf(com.nexaleads.app.data.models.BundledProduct("prod_2", 3), com.nexaleads.app.data.models.BundledProduct("prod_5", 3))),
                
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
                val docRef = productsCol().document(product.id)
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
            val snapshot = productsCol().limit(1).get().await()
            if (snapshot.isEmpty) {
                forceSeedProducts()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getRecentInteractions(userId: String): List<Interaction> {
        return try {
            val snapshot = interactionsCol()
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
                    orderAmount = doc.getString("orderAmount"),
                    orderAmountNum = doc.getLong("orderAmountNum") ?: 0L
                )
            }
            
            // Sort locally to bypass Firestore composite index requirement
            interactionsList.sortedByDescending { it.timestamp }.take(50)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateLead(leadId: String, updates: Map<String, Any?>) {
        kotlinx.coroutines.withTimeoutOrNull(3000) {
            val finalUpdates = updates.toMutableMap()
            finalUpdates["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            leadsCol().document(leadId).update(finalUpdates).await()
        }
    }

    suspend fun assignLeadToUser(leadId: String, userId: String): Boolean {
        return try {
            val leadRef = leadsCol().document(leadId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(leadRef)
                val currentAssignedTo = snapshot.getString("assignedTo") ?: ""
                if (currentAssignedTo.isEmpty() || currentAssignedTo == userId) {
                    transaction.update(leadRef, "assignedTo", userId)
                    transaction.update(leadRef, "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
                    true
                } else {
                    false // Someone else already claimed it
                }
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun addInteraction(interaction: Interaction) {
        kotlinx.coroutines.withTimeoutOrNull(3000) {
            interactionsCol().document(interaction.id).set(interaction).await()
        }
    }
    
    suspend fun updateLeadAndAddInteractionBatch(leadId: String, updates: Map<String, Any?>, interaction: Interaction) {
        kotlinx.coroutines.withTimeoutOrNull(5000) {
            val batch = db.batch()
            val leadRef = leadsCol().document(leadId)
            val finalUpdates = updates.toMutableMap()
            finalUpdates["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            batch.update(leadRef, finalUpdates)
            
            val interactionRef = interactionsCol().document(interaction.id)
            batch.set(interactionRef, interaction)
            
            batch.commit().await()
        }
    }

    suspend fun deleteInteraction(interactionId: String) {
        kotlinx.coroutines.withTimeoutOrNull(3000) {
            interactionsCol().document(interactionId).update("isReverted", true).await()
        }
    }

    suspend fun recalculateLeadStateAndBatch(leadId: String, interactionIdToDelete: String): Boolean {
        return try {
            val leadSnapshot = leadsCol().document(leadId).get().await()
            val currentNotes = leadSnapshot.getString("notes") ?: ""
            val safeMetaDump = if (currentNotes.contains("\n\n📞 ")) {
                currentNotes.substringBefore("\n\n📞 ")
            } else {
                if (currentNotes.isNotEmpty() && !currentNotes.contains("📞 ")) currentNotes else ""
            }

            val interactionDoc = interactionsCol().document(interactionIdToDelete).get().await()
            val callerId = interactionDoc.getString("callerId") ?: return false

            val interactionsSnapshot = interactionsCol()
                .whereEqualTo("leadId", leadId)
                .whereEqualTo("callerId", callerId)
                .get()
                .await()
            
            val remainingInteractions = interactionsSnapshot.documents
                .filter { it.id != interactionIdToDelete && it.getBoolean("isReverted") != true }
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
                    orderAmount = doc.getString("orderAmount"),
                    orderAmountNum = doc.getLong("orderAmountNum") ?: 0L,
                    isReverted = doc.getBoolean("isReverted") ?: false
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
            updateMap["orderAmountNum"] = latestOrderInteraction.orderAmountNum
            
            val pm = latestOrderInteraction.paymentMethod
            val ps = latestOrderInteraction.paymentStatus
            val isRev = (finalStatus == "Order Placed" || finalStatus == "Dispatched" || finalStatus == "Delivered") && !(pm == "Prepaid" && ps == "Link Sent")
            updateMap["convertedAt"] = if (isRev) latestOrderInteraction.timestamp else null
        } else if (Constants.normalizeStatus(finalStatus) != Constants.STATUS_ORDER_PLACED) {
            // Wipe ghost order data when reverting from an order status!
            updateMap["product"] = ""
            updateMap["address"] = ""
            updateMap["city"] = ""
            updateMap["pincode"] = ""
            updateMap["paymentMethod"] = ""
            updateMap["orderAmount"] = ""
            updateMap["orderAmountNum"] = 0L
            updateMap["convertedAt"] = null
        }

        val batch = db.batch()
        val leadRef = leadsCol().document(leadId)
        batch.update(leadRef, updateMap)
            
            val interactionRef = interactionsCol().document(interactionIdToDelete)
            batch.update(interactionRef, "isReverted", true, "serverCreatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
            
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
            var snapshot = leadsCol().whereEqualTo("phone", phone).limit(1).get().await()
            
            if (snapshot.isEmpty) {
                // Check purely sanitized
                snapshot = leadsCol().whereEqualTo("phone", sanitized).limit(1).get().await()
            }
            if (snapshot.isEmpty) {
                // Check sanitized with +91
                snapshot = leadsCol().whereEqualTo("phone", "+91$sanitized").limit(1).get().await()
            }
            if (snapshot.isEmpty) {
                // Check sanitized with 0
                snapshot = leadsCol().whereEqualTo("phone", "0$sanitized").limit(1).get().await()
            }
            if (snapshot.isEmpty && phone.startsWith("+91")) {
                val withoutCode = phone.removePrefix("+91").trim()
                snapshot = leadsCol().whereEqualTo("phone", withoutCode).limit(1).get().await()
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
            
            val leadRef = leadsCol().document(lead.id)
            batch.set(leadRef, lead)

            val interactionRef = interactionsCol().document(interaction.id)
            batch.set(interactionRef, interaction)

            batch.commit().await()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Unknown Firebase Error"
        }
    }

    private fun generateSearchKeywords(name: String, phone: String): List<String> {
        val keywords = mutableSetOf<String>()
        val words = name.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        for (word in words) {
            for (i in 1..word.length) {
                keywords.add(word.substring(0, i))
            }
        }
        val cleanPhone = PhoneUtils.sanitizePhoneNumber(phone)
        for (i in 3..cleanPhone.length) {
            keywords.add(cleanPhone.substring(cleanPhone.length - i)) // Suffixes (last 3, 4, 5... 10 digits)
        }
        keywords.add(cleanPhone)
        return keywords.toList()
    }

    suspend fun runSchemaMigration() {
        try {
            val snapshot = leadsCol().get().await()
            val batch = db.batch()
            var count = 0
            val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).apply { 
                timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") 
            }
            
            for (doc in snapshot.documents) {
                val updates = mutableMapOf<String, Any>()
                
                // 1. Search Keywords
                val name = doc.getString("name") ?: ""
                val phone = doc.getString("phone") ?: ""
                if (doc.get("searchKeywords") == null) {
                    updates["searchKeywords"] = generateSearchKeywords(name, phone)
                }
                
                // 2. Exhaustion Timestamp
                val exhaustionDateStr = doc.getString("exhaustionDate")
                if (!exhaustionDateStr.isNullOrEmpty() && doc.get("exhaustionTimestamp") == null) {
                    try {
                        val date = dateFormat.parse(exhaustionDateStr)
                        if (date != null) {
                            updates["exhaustionTimestamp"] = date.time
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                if (updates.isNotEmpty()) {
                    batch.update(doc.reference, updates)
                    count++
                    if (count >= 400) { // Firestore batch limit is 500
                        batch.commit().await()
                        count = 0
                    }
                }
            }
            if (count > 0) {
                batch.commit().await()
            }
            android.util.Log.d("LeadRepository", "Schema migration completed!")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun migrateOldOrders(userId: String) {
        try {
            val snapshot = leadsCol()
                .whereEqualTo("assignedTo", userId)
                .get().await()
            var batch = db.batch()
            var count = 0
            
            snapshot.documents.forEach { doc ->
                val status = doc.getString("status") ?: ""
                if (status in listOf("Order Placed", "Dispatched", "Delivered")) {
                    // Fetch the latest order interaction to get the correct amount
                    val interactionsSnapshot = interactionsCol()
                        .whereEqualTo("leadId", doc.id)
                        .get().await()
                    val latestOrderInteraction = interactionsSnapshot.documents
                        .mapNotNull { it.toObject(com.nexaleads.app.data.model.Interaction::class.java) }
                        .filter { it.statusAfter == "Order Placed" || !it.product.isNullOrEmpty() }
                        .maxByOrNull { it.timestamp }

                    val amtStr = latestOrderInteraction?.orderAmount ?: doc.getString("orderAmount") ?: "0"
                    val cleanAmtStr = amtStr.replace(Regex("[^0-9]"), "")
                    val amtNum = cleanAmtStr.toLongOrNull() ?: 0L
                    val cAt = latestOrderInteraction?.timestamp ?: doc.getString("convertedAt")
                
                val updates = mutableMapOf<String, Any>("orderAmountNum" to amtNum)
                
                if (cAt.isNullOrEmpty()) {
                    val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    updates["convertedAt"] = isoFormat.format(java.util.Date()) // Fallback to today if unknown
                } else {
                    updates["convertedAt"] = cAt
                }
                
                batch.update(doc.reference, updates)
                count++
                
                if (count == 400) {
                    batch.commit().await()
                    batch = db.batch()
                    count = 0
                }
                }
            }
            if (count > 0) {
                batch.commit().await()
            }
            android.util.Log.d("LeadRepository", "Revenue Migration Completed!")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseLead(doc: com.google.firebase.firestore.DocumentSnapshot): Lead {
        return Lead(
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
            orderAmountNum = doc.getLong("orderAmountNum") ?: 0L,
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
            cancellationRequestedAt = doc.getString("cancellationRequestedAt") ?: "",
            deliveredAt = doc.getString("deliveredAt"),
            exhaustionDate = doc.getString("exhaustionDate"),
            exhaustionTimestamp = doc.getLong("exhaustionTimestamp"),
            parentLeadId = doc.getString("parentLeadId"),
            isReorder = doc.getBoolean("isReorder") ?: false,
            searchKeywords = doc.get("searchKeywords") as? List<String> ?: emptyList(),
            updatedAt = try {
                val raw = doc.get("updatedAt")
                when (raw) {
                    is com.google.firebase.Timestamp -> raw.toDate().time
                    is Long -> raw
                    is Number -> raw.toLong()
                    is String -> {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        format.parse(raw)?.time
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        )
    }
}
