package com.nexaleads.app.data.repository

import com.nexaleads.app.Constants
import com.nexaleads.app.data.model.Interaction
import com.nexaleads.app.data.model.getCreatedAtString
import com.nexaleads.app.data.model.Lead
import com.nexaleads.app.data.model.Order
import com.nexaleads.app.data.model.getPrimaryCategory
import com.nexaleads.app.utils.PhoneUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import javax.inject.Singleton
import javax.inject.Inject
import com.nexaleads.app.data.models.Product
import com.nexaleads.app.data.models.Category
import com.google.firebase.auth.FirebaseAuth

@Singleton
class LeadRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private var orgId: String = ""

    fun setOrgId(id: String) {
        orgId = id
    }

    private fun leadsCol() = db.collection("organizations").document(orgId).collection("leads")
    fun ordersCol() = db.collection("organizations").document(orgId).collection("orders_data")
    private fun interactionsCol() = db.collection("organizations").document(orgId).collection("interactions")
    private fun productsCol() = db.collection("organizations").document(orgId).collection("products")
    private fun categoriesCol() = db.collection("organizations").document(orgId).collection("categories")
    private fun whatsappTemplatesCol() = db.collection("organizations").document(orgId).collection("whatsapp_templates")
    fun getLeadsForUser(userId: String, limit: Long = 100): Flow<List<Lead>> = callbackFlow {
        val listener = leadsCol()
            .whereEqualTo("assignedTo", userId)
            .whereEqualTo("archived", false)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("LeadRepository", "getLeadsForUser error: ${error.message}")
                    cancel(java.util.concurrent.CancellationException(error.message))
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val leads = snapshot.documents.mapNotNull { doc -> parseLead(doc) }
                    trySend(leads)
                }
            }
        
        awaitClose { listener.remove() }
    }

    suspend fun searchMyLeads(userId: String, query: String): List<Lead> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()
        
        val isNumeric = cleanQuery.replace("+", "").all { it.isDigit() }
        
        return try {
            val baseQuery = leadsCol().whereEqualTo("assignedTo", userId).whereEqualTo("archived", false)
            
            val snapshotDocs = if (isNumeric) {
                val cleanPhone = cleanQuery.replace(" ", "").replace("+", "")
                val q1 = baseQuery.whereGreaterThanOrEqualTo("phone", cleanPhone).whereLessThanOrEqualTo("phone", cleanPhone + "\uf8ff").limit(20).get()
                val q2 = baseQuery.whereGreaterThanOrEqualTo("phone", "+91$cleanPhone").whereLessThanOrEqualTo("phone", "+91$cleanPhone" + "\uf8ff").limit(20).get()
                val q3 = baseQuery.whereGreaterThanOrEqualTo("phone", "91$cleanPhone").whereLessThanOrEqualTo("phone", "91$cleanPhone" + "\uf8ff").limit(20).get()
                
                val res1 = q1.await().documents
                val res2 = q2.await().documents
                val res3 = q3.await().documents
                
                (res1 + res2 + res3).distinctBy { it.id }
            } else {
                val lowerQuery = cleanQuery.lowercase()
                val upperQuery = cleanQuery.uppercase()
                val capitalizedQuery = cleanQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                
                val q1 = baseQuery.whereGreaterThanOrEqualTo("name", capitalizedQuery).whereLessThanOrEqualTo("name", capitalizedQuery + "\uf8ff").limit(20).get()
                val q2 = baseQuery.whereGreaterThanOrEqualTo("name", lowerQuery).whereLessThanOrEqualTo("name", lowerQuery + "\uf8ff").limit(20).get()
                val q3 = baseQuery.whereGreaterThanOrEqualTo("name", upperQuery).whereLessThanOrEqualTo("name", upperQuery + "\uf8ff").limit(20).get()
                
                val res1 = q1.await().documents
                val res2 = q2.await().documents
                val res3 = q3.await().documents
                
                (res1 + res2 + res3).distinctBy { it.id }
            }
            
            snapshotDocs.mapNotNull { parseLead(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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
        var leadsMetrics = mapOf<String, Long>("freshLeads" to 0L, "dueFollowups" to 0L, "inquiries" to 0L, "attempted" to 0L, "rejected" to 0L)
        var ordersMetrics = mapOf<String, Long>("confirmedOrders" to 0L, "pendingPayments" to 0L, "dispatched" to 0L, "delivered" to 0L, "rto" to 0L)

        val pushUpdate = {
            val combined = mutableMapOf<String, Long>()
            combined.putAll(leadsMetrics)
            combined.putAll(ordersMetrics)
            trySend(combined)
        }

        val leadsListener = leadsCol().whereEqualTo("assignedTo", userId).whereEqualTo("archived", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("LeadRepository", "getDashboardMetricsFlow leads error: ${error.message}")
                    cancel(java.util.concurrent.CancellationException(error.message))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    var freshLeads = 0L; var dueFollowups = 0L; var inquiries = 0L; var attempted = 0L; var rejected = 0L
                    for (doc in snapshot.documents) {
                        val lead = parseLead(doc) ?: continue
                        val category = lead.getPrimaryCategory()
                        when (category) {
                            "PENDING" -> freshLeads++
                            "FOLLOWUP" -> dueFollowups++
                            "INQUIRY" -> inquiries++
                            "ATTEMPTED" -> attempted++
                            "REJECTED" -> rejected++
                        }
                    }
                    leadsMetrics = mapOf("freshLeads" to freshLeads, "dueFollowups" to dueFollowups, "inquiries" to inquiries, "attempted" to attempted, "rejected" to rejected)
                    pushUpdate()
                }
            }

        val ordersListener = ordersCol().whereEqualTo("assignedTo", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("LeadRepository", "getDashboardMetricsFlow orders error: ${error.message}")
                    cancel(java.util.concurrent.CancellationException(error.message))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    var confirmedOrders = 0L; var pendingPayments = 0L; var dispatched = 0L; var delivered = 0L; var rto = 0L
                    for (doc in snapshot.documents) {
                        val order = doc.toObject(com.nexaleads.app.data.model.Order::class.java) ?: continue
                        when (order.status) {
                            "Order Placed", "Dispatched", "Delivered" -> {
                                if (order.status == "Order Placed" && order.paymentMethod.equals("Prepaid", ignoreCase=true) && order.paymentStatus.equals("Link Sent", ignoreCase=true)) {
                                    pendingPayments++
                                } else {
                                    confirmedOrders++
                                    if (order.status == "Dispatched") dispatched++
                                    if (order.status == "Delivered") delivered++
                                }
                            }
                            "RTO" -> rto++
                        }
                    }
                    ordersMetrics = mapOf("confirmedOrders" to confirmedOrders, "pendingPayments" to pendingPayments, "dispatched" to dispatched, "delivered" to delivered, "rto" to rto)
                    pushUpdate()
                }
            }

        awaitClose { 
            leadsListener.remove()
            ordersListener.remove()
        }
    }

    fun getSalesMetricsFlow(userId: String): Flow<Map<String, Long>> = callbackFlow {
        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
        val lastWeekStr = isoFormat.format(cal.time)

        val listener = ordersCol()
            .whereEqualTo("assignedTo", userId)
            .whereGreaterThanOrEqualTo("createdAt", lastWeekStr)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("LeadRepository", "getSalesMetricsFlow error: ${error.message}")
                    cancel(java.util.concurrent.CancellationException(error.message))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val todayStr = isoFormat.format(java.util.Date())
                    var todayCount = 0L; var todayRev = 0L; var weekRev = 0L; var weekCount = 0L
                    for (doc in snapshot.documents) {
                        val order = doc.toObject(com.nexaleads.app.data.model.Order::class.java) ?: continue
                        if (order.status == "Order Cancelled" || order.status == "RTO" || order.status == "Cancelled") continue
                        if (order.paymentMethod.equals("Prepaid", ignoreCase=true) && order.paymentStatus.equals("Link Sent", ignoreCase=true)) continue

                        val createdAtStr = order.getCreatedAtString()
                        val cAt = if (createdAtStr.length >= 10) createdAtStr.substring(0, 10) else continue
                        if (cAt >= todayStr) {
                            todayCount++
                            todayRev += order.orderAmountNum
                        }
                        if (cAt >= lastWeekStr) {
                            weekCount++
                            weekRev += order.orderAmountNum
                        }
                    }
                    trySend(mapOf("todayCount" to todayCount, "todayRev" to todayRev, "weekCount" to weekCount, "weekRev" to weekRev))
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
                    cancel(java.util.concurrent.CancellationException(error.message))
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

    fun getWhatsAppTemplates(): Flow<List<com.nexaleads.app.data.model.WhatsAppTemplate>> = callbackFlow {
        val listener = whatsappTemplatesCol()
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val templates = snapshot.documents.mapNotNull { doc ->
                        com.nexaleads.app.data.model.WhatsAppTemplate(
                            id = doc.id,
                            statusTrigger = doc.getString("statusTrigger") ?: "",
                            language = doc.getString("language") ?: "",
                            templateText = doc.getString("templateText") ?: "",
                            isActive = doc.getBoolean("isActive") ?: false,
                            updatedAt = doc.getString("updatedAt") ?: ""
                        )
                    }
                    trySend(templates)
                }
            }
        awaitClose { listener.remove() }
    }
    
    suspend fun getCustomerLeads(phone: String): List<Lead> {
        return try {
            val sanitized = PhoneUtils.sanitizePhoneNumber(phone)
            val purePhone = if (sanitized.length >= 10) sanitized else phone.replace(Regex("[^0-9+]"), "").trim()
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            
            val query1 = leadsCol().whereEqualTo("phone", purePhone).whereEqualTo("assignedTo", userId).get().await()
            val query2 = leadsCol().whereEqualTo("phone", sanitized).whereEqualTo("assignedTo", userId).get().await()
            val query3 = leadsCol().whereEqualTo("phone", "+91$sanitized").whereEqualTo("assignedTo", userId).get().await()
            val query4 = leadsCol().whereEqualTo("phone", "0$sanitized").whereEqualTo("assignedTo", userId).get().await()
            
            val allDocs = query1.documents + query2.documents + query3.documents + query4.documents
            val uniqueDocs = allDocs.distinctBy { it.id }
            
            uniqueDocs.mapNotNull { parseLead(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCustomerInteractions(leadIds: List<String>): List<Interaction> {
        if (leadIds.isEmpty()) return emptyList()

        return try {
            val chunked = leadIds.chunked(10)
            val allInteractions = mutableListOf<Interaction>()
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            
            for (chunk in chunked) {
                val snapshot = interactionsCol()
                    .whereIn("leadId", chunk)
                    .whereEqualTo("callerId", userId)
                    .get()
                    .await()
                
                snapshot.documents.forEach { doc ->
                    try {
                        val interaction = doc.toObject(Interaction::class.java)
                        if (interaction != null && !interaction.isReverted) {
                            allInteractions.add(interaction.copy(id = doc.id))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            allInteractions.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
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
            
            val interactionsList = snapshot.documents
                .filter { it.getBoolean("isReverted") != true }
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
                    isReverted = doc.getBoolean("isReverted") ?: false,
                    associatedOrderId = doc.getString("associatedOrderId")
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
            syncProfileDataAcrossSharedLeads(leadId, updates)
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
    
    
    fun getOrdersForCustomer(phone: String): Flow<List<com.nexaleads.app.data.model.Order>> = callbackFlow {
        if (orgId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = ordersCol()
            .whereEqualTo("customerPhone", phone)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    cancel(java.util.concurrent.CancellationException(error.message))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val orders = snapshot.documents.mapNotNull { it.toObject(com.nexaleads.app.data.model.Order::class.java) }
                    trySend(orders)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getLatestOrderForCustomer(customerId: String): com.nexaleads.app.data.model.Order? {
        return try {
            val snapshot = ordersCol().whereEqualTo("customerId", customerId).get().await()
            snapshot.documents
                .mapNotNull { it.toObject(com.nexaleads.app.data.model.Order::class.java)?.copy(id = it.id) }
                .maxByOrNull { it.createdAtMillis }
        } catch (e: Exception) {
            android.util.Log.e("LeadRepository", "Error getting latest order", e)
            null
        }
    }

    suspend fun updateLeadAddOrderAndInteractionBatch(
        leadId: String,
        leadUpdates: Map<String, Any?>,
        order: com.nexaleads.app.data.model.Order,
        interaction: Interaction
    ) {
        kotlinx.coroutines.withTimeoutOrNull(5000) {
            val batch = db.batch()
            val leadRef = leadsCol().document(leadId)
            batch.update(leadRef, leadUpdates)
            val orderRef = ordersCol().document(order.id)
            batch.set(orderRef, order, com.google.firebase.firestore.SetOptions.merge())
            val interactionRef = interactionsCol().document(interaction.id)
            batch.set(interactionRef, interaction)
            batch.commit().await()
            syncProfileDataAcrossSharedLeads(leadId, leadUpdates)
        }
    }

    suspend fun updateLeadAndAddInteractionBatch(
        leadId: String, 
        updates: Map<String, Any?>, 
        interaction: Interaction,
        orderId: String? = null,
        orderUpdates: Map<String, Any?>? = null,
        orderIdToCancel: String? = null
    ) {
        kotlinx.coroutines.withTimeoutOrNull(5000) {
            db.runTransaction { transaction ->
                val leadRef = leadsCol().document(leadId)
                val finalUpdates = updates.toMutableMap()
                finalUpdates["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                transaction.update(leadRef, finalUpdates)
                
                val interactionRef = interactionsCol().document(interaction.id)
                transaction.set(interactionRef, interaction)
                
                if (orderIdToCancel != null) {
                    val cancelRef = ordersCol().document(orderIdToCancel)
                    transaction.update(cancelRef, "status", com.nexaleads.app.Constants.STATUS_ORDER_CANCELLED)
                }

                if (orderId != null && orderUpdates != null && orderUpdates.isNotEmpty()) {
                    val orderRef = ordersCol().document(orderId)
                    val orderSnapshot = transaction.get(orderRef)
                    if (orderSnapshot.exists()) {
                        val currentStatus = orderSnapshot.getString("status")
                        val lockedStatuses = listOf("Dispatched", "Delivered", "Order Cancelled", "Cancelled", "RTO", "Returned")
                        if (currentStatus !in lockedStatuses) {
                            transaction.update(orderRef, orderUpdates)
                        } else {
                            throw com.google.firebase.firestore.FirebaseFirestoreException(
                                "Cannot edit: Order is already $currentStatus", 
                                com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                            )
                        }
                    }
                }
                
                // Return result from transaction
                true
            }.await()
            syncProfileDataAcrossSharedLeads(leadId, updates)
        }
    }

    private suspend fun syncProfileDataAcrossSharedLeads(currentLeadId: String, updates: Map<String, Any?>) {
        try {
            val phone = updates["phone"] as? String ?: return
            
            // We only want to sync core profile fields, not statuses, products, or funnel notes
            val profileUpdates = mutableMapOf<String, Any?>()
            val fieldsToSync = listOf("name", "address", "city", "pincode", "state")
            var hasSyncableData = false
            
            for (field in fieldsToSync) {
                if (updates.containsKey(field)) {
                    profileUpdates[field] = updates[field]
                    hasSyncableData = true
                }
            }
            
            if (!hasSyncableData) return
            
            profileUpdates["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            
            val snapshot = leadsCol().whereEqualTo("phone", phone).get().await()
            if (snapshot.documents.size <= 1) return // No shared leads
            
            val batch = db.batch()
            var hasUpdates = false
            for (doc in snapshot.documents) {
                if (doc.id != currentLeadId) {
                    batch.update(doc.reference, profileUpdates)
                    hasUpdates = true
                }
            }
            if (hasUpdates) {
                batch.commit().await()
                android.util.Log.d("LeadRepository", "Synced profile data to ${snapshot.documents.size - 1} shared leads for phone $phone")
            }
        } catch (e: Exception) {
            android.util.Log.e("LeadRepository", "Error syncing profile data across shared leads", e)
        }
    }

    suspend fun deleteInteraction(interactionId: String) {
        kotlinx.coroutines.withTimeoutOrNull(3000) {
            interactionsCol().document(interactionId).update("isReverted", true).await()
        }
    }

    suspend fun recalculateLeadStateAndBatch(leadId: String, interactionIdToDelete: String): Boolean {
        var retries = 3
        while (retries > 0) {
            try {
                android.util.Log.e("RevertDebug", "Attempt $retries for lead: $leadId, interaction: $interactionIdToDelete")
                
                // 0. Fetch the Interaction to delete and check for Order Deletion Logic
                val interactionToDeleteRef = interactionsCol().document(interactionIdToDelete)
                val interactionToDeleteSnapshot = interactionToDeleteRef.get().await()
                val interactionToDelete = interactionToDeleteSnapshot.toObject(Interaction::class.java)
                
                var orderIdToDelete: String? = null
                if (interactionToDelete != null) {
                    val statusAfter = Constants.normalizeStatus(interactionToDelete.statusAfter)
                    val isOrderRelated = statusAfter == Constants.STATUS_ORDER_PLACED
                    if (isOrderRelated || interactionToDelete.associatedOrderId != null) {
                        val associatedOrderId = interactionToDelete.associatedOrderId
                        if (associatedOrderId != null) {
                            val orderRef = ordersCol().document(associatedOrderId)
                            val orderSnapshot = orderRef.get().await()
                            if (orderSnapshot.exists()) {
                                val orderStatus = orderSnapshot.getString("status") ?: ""
                                if (orderStatus != "Order Placed") {
                                    throw Exception("Cannot Revert: Order has already been processed (Status: $orderStatus). Please cancel manually if needed.")
                                }
                                orderIdToDelete = associatedOrderId
                            }
                        } else {
                            throw Exception("Cannot Revert Legacy Order: This is a migrated older order without a direct link. Please cancel it manually from the Orders Dashboard.")
                        }
                    }
                }
                
                // 1. Fetch current Lead state for optimistic locking
                val leadRef = leadsCol().document(leadId)
                val initialLeadSnapshot = leadRef.get().await()
                if (!initialLeadSnapshot.exists()) {
                    android.util.Log.e("RevertDebug", "Lead does not exist!")
                    return false
                }
                val initialUpdatedAt = initialLeadSnapshot.get("updatedAt")
                
                val currentNotes = initialLeadSnapshot.getString("notes") ?: ""
                val safeMetaDump = if (currentNotes.contains("\n\ndY\"z ")) {
                    currentNotes.substringBefore("\n\ndY\"z ")
                } else {
                    if (currentNotes.isNotEmpty() && !currentNotes.contains("dY\"z ")) currentNotes else ""
                }
    
                // 2. Fetch ALL Interactions for this lead
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    return false
                }
                val interactionsSnapshot = interactionsCol()
                    .whereEqualTo("leadId", leadId)
                    .whereEqualTo("callerId", userId)
                    .get()
                    .await()
                
                val remainingInteractions = interactionsSnapshot.documents
                    .filter { it.id != interactionIdToDelete && it.getBoolean("isReverted") != true }
                    .mapNotNull { it.toObject(Interaction::class.java)?.copy(id = it.id) }
                    .sortedBy { it.timestamp }
    
                // 3. Rebuild Notes chronologically
                var rebuiltNotes = safeMetaDump
                remainingInteractions.forEach { interaction ->
                    if (interaction.notes.trim().isNotEmpty()) {
                        if (rebuiltNotes.isEmpty()) {
                            rebuiltNotes = interaction.notes.trim()
                        } else {
                            rebuiltNotes += "\n\ndY\"z ${interaction.notes.trim()}"
                        }
                    }
                }
    
                // 4. Determine True Status & State
                val latestInteraction = remainingInteractions.lastOrNull()
                var finalStatus = latestInteraction?.statusAfter ?: "New"
                val rawNormalizedStatus = Constants.normalizeStatus(finalStatus)
                
                val latestOrderInteraction = remainingInteractions.lastOrNull { 
                    Constants.normalizeStatus(it.statusAfter) == Constants.STATUS_ORDER_PLACED || !it.product.isNullOrEmpty() 
                }
                
                if (rawNormalizedStatus == Constants.STATUS_ORDER_PLACED || rawNormalizedStatus == Constants.STATUS_DISPATCHED || rawNormalizedStatus == Constants.STATUS_DELIVERED) {
                    if (latestOrderInteraction == null || latestOrderInteraction.product.isNullOrEmpty()) {
                        val fallbackInteraction = remainingInteractions.lastOrNull { 
                            val s = Constants.normalizeStatus(it.statusAfter)
                            s != Constants.STATUS_ORDER_PLACED && s != Constants.STATUS_DISPATCHED && s != Constants.STATUS_DELIVERED 
                        }
                        finalStatus = fallbackInteraction?.statusAfter ?: "New"
                    }
                }
    
                var finalFollowUpDate = remainingInteractions.lastOrNull { it.followUpDate != null }?.followUpDate
                var finalSubStatus = remainingInteractions.lastOrNull { it.subStatus != null }?.subStatus
                var finalTimeSlot = remainingInteractions.lastOrNull { it.followUpTimeSlot != null }?.followUpTimeSlot
                val finalPaymentStatus = remainingInteractions.lastOrNull { it.paymentStatus != null }?.paymentStatus
                
                val normFinalStatus = Constants.normalizeStatus(finalStatus)
                val isTerminalStatus = normFinalStatus == Constants.STATUS_NOT_INTERESTED || 
                                       normFinalStatus == Constants.STATUS_INVALID || 
                                       normFinalStatus == Constants.STATUS_ORDER_PLACED ||
                                       normFinalStatus == Constants.STATUS_ORDER_CANCELLED ||
                                       normFinalStatus == Constants.STATUS_DELIVERED
                
                if (isTerminalStatus) {
                    finalFollowUpDate = null
                    finalSubStatus = null
                    finalTimeSlot = null
                }
    
                val updateMap = mutableMapOf<String, Any?>(
                    "status" to finalStatus,
                    "notes" to rebuiltNotes,
                    "followUpDate" to finalFollowUpDate,
                    "subStatus" to finalSubStatus,
                    "followUpTimeSlot" to finalTimeSlot,
                    "paymentStatus" to finalPaymentStatus
                )

                // Archive if it was a self-generated lead (from call log/manual) and it has 0 interactions left
                if (remainingInteractions.isEmpty()) {
                    val label = initialLeadSnapshot.getString("label")
                    if (label == "Manual Inbound") {
                        updateMap["archived"] = true
                    }
                }
    
                if (normFinalStatus == Constants.STATUS_ORDER_PLACED || normFinalStatus == Constants.STATUS_DISPATCHED || normFinalStatus == Constants.STATUS_DELIVERED) {
                    updateMap["product"] = latestOrderInteraction?.product ?: ""
                    updateMap["address"] = latestOrderInteraction?.address ?: ""
                    updateMap["city"] = latestOrderInteraction?.city ?: ""
                    updateMap["pincode"] = latestOrderInteraction?.pincode ?: ""
                    updateMap["paymentMethod"] = latestOrderInteraction?.paymentMethod ?: ""
                    updateMap["orderAmount"] = latestOrderInteraction?.orderAmount ?: ""
                    updateMap["orderAmountNum"] = latestOrderInteraction?.orderAmountNum ?: 0L
                    
                    val pm = latestOrderInteraction?.paymentMethod
                    val ps = latestOrderInteraction?.paymentStatus
                    val isRev = !(pm == "Prepaid" && ps == "Link Sent")
                    updateMap["convertedAt"] = if (isRev) latestOrderInteraction?.timestamp else null
                } else {
                    updateMap["product"] = ""
                    updateMap["address"] = ""
                    updateMap["city"] = ""
                    updateMap["pincode"] = ""
                    updateMap["paymentMethod"] = ""
                    updateMap["orderAmount"] = ""
                    updateMap["orderAmountNum"] = 0L
                    updateMap["convertedAt"] = null
                }
                
                // 5. Recalculate Orders Count and LTV
                var ordersSnapshot: com.google.firebase.firestore.QuerySnapshot? = null
                try {
                    ordersSnapshot = ordersCol().whereEqualTo("customerId", leadId).get().await()
                } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                    if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        // Fallback for telecallers who can only read their own orders
                        ordersSnapshot = ordersCol()
                            .whereEqualTo("customerId", leadId)
                            .whereEqualTo("assignedTo", userId)
                            .get().await()
                    } else {
                        throw e
                    }
                }
                
                val remainingOrders = ordersSnapshot!!.documents.filter { it.id != orderIdToDelete }
                val newTotalOrdersCount = remainingOrders.size
                var newLtv = 0L
                for (doc in remainingOrders) {
                    val orderAmt = doc.getLong("orderAmountNum") ?: 0L
                    newLtv += orderAmt
                }
                updateMap["totalOrdersCount"] = newTotalOrdersCount
                updateMap["lifetimeOrderValue"] = newLtv
                
                updateMap["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
    
                // 6. Transaction Commit
                var transactionSuccess = false
                db.runTransaction { transaction ->
                    val currentLeadSnapshot = transaction.get(leadRef)
                    val currentUpdatedAt = currentLeadSnapshot.get("updatedAt")
                    
                    val safeToProceed = (initialUpdatedAt == null && currentUpdatedAt == null) || 
                                        (initialUpdatedAt?.toString() == currentUpdatedAt?.toString())
                    
                    if (safeToProceed) {
                        transaction.update(leadRef, updateMap)
                        transaction.update(interactionToDeleteRef, "isReverted", true, "serverCreatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
                        if (orderIdToDelete != null) {
                            transaction.delete(ordersCol().document(orderIdToDelete))
                        }
                        transactionSuccess = true
                    } else {
                        throw com.google.firebase.firestore.FirebaseFirestoreException("Concurrent modification detected", com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED)
                    }
                }.await()
                
                if (transactionSuccess) {
                    return true
                }
                
            } catch (e: Exception) {
                android.util.Log.e("RevertDebug", "Exception during revert attempt", e)
                if (e.message?.contains("Cannot Revert") == true) {
                    throw e // Re-throw custom validation errors so ViewModel can show them
                }
                if (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED) {
                    throw e
                }
                if (retries <= 1) {
                    throw Exception("Failed to revert after 3 attempts. Last error: ${e.message}")
                }
            }
            retries--
        }
        return false
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
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getAllMatchingLeads(phone: String): List<Lead> {
        return try {
            val sanitized = PhoneUtils.sanitizePhoneNumber(phone)
            val results = mutableListOf<Lead>()
            val ids = mutableSetOf<String>()

            val queries = listOf(
                phone,
                sanitized,
                "+91$sanitized",
                "0$sanitized",
                if (phone.startsWith("+91")) phone.removePrefix("+91").trim() else ""
            ).filter { it.isNotEmpty() }

            for (q in queries) {
                val snapshot = leadsCol().whereEqualTo("phone", q).get().await()
                for (doc in snapshot.documents) {
                    if (ids.add(doc.id)) {
                        results.add(
                            Lead(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                phone = doc.getString("phone") ?: "",
                                source = doc.getString("source") ?: "",
                                status = doc.getString("status") ?: "New",
                                subStatus = doc.getString("subStatus") ?: "",
                                notes = doc.getString("notes") ?: "",
                                label = doc.getString("label") ?: "",
                                followUpDate = doc.getString("followUpDate"),
                                followUpTimeSlot = doc.getString("followUpTimeSlot") ?: "",
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
                                originalTotalValue = doc.getString("originalTotalValue") ?: "",
                                discountAmount = doc.getString("discountAmount") ?: "",
                                paymentStatus = doc.getString("paymentStatus") ?: ""
                            )
                        )
                    }
                }
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun updateExistingLeadFromDuplicate(
        leadId: String,
        status: String,
        subStatus: String,
        notes: String,
        product: String,
        followUpDate: String?,
        followUpTimeSlot: String,
        orderAmount: String,
        originalTotalValue: String,
        discountAmount: String,
        paymentStatus: String,
        address: String,
        city: String,
        state: String,
        pincode: String,
        paymentMethod: String,
        interaction: Interaction
    ) {
        val leadRef = leadsCol().document(leadId)
        val interactionRef = interactionsCol().document(interaction.id)

        val isOrder = status in listOf("Order Placed", Constants.STATUS_ORDER_PLACED)

        db.runTransaction { transaction ->
            val leadSnap = transaction.get(leadRef)
            val oldNotes = leadSnap.getString("notes") ?: ""
            val newNotes = if (oldNotes.isNotEmpty()) "$oldNotes\n\n[Converted to $status]: $notes" else notes

            val updates = mutableMapOf<String, Any?>(
                "status" to status,
                "subStatus" to subStatus,
                "notes" to newNotes,
                "product" to product,
                "orderAmount" to orderAmount,
                "originalTotalValue" to originalTotalValue,
                "discountAmount" to discountAmount,
                "paymentStatus" to paymentStatus,
                "address" to address,
                "city" to city,
                "state" to state,
                "pincode" to pincode,
                "paymentMethod" to paymentMethod,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // Clear follow-up dates if converted to Order or if instructed
            if (isOrder) {
                updates["followUpDate"] = null
                updates["followUpTimeSlot"] = null
            } else {
                updates["followUpDate"] = followUpDate
                updates["followUpTimeSlot"] = followUpTimeSlot
            }

            transaction.update(leadRef, updates)
            transaction.set(interactionRef, interaction)
        }.await()
    }

    suspend fun createManualLeadBatch(lead: Lead, interaction: Interaction, order: Order? = null): String? {
        return try {
            val batch = db.batch()
            
            val leadRef = leadsCol().document(lead.id)
            batch.set(leadRef, lead)
            
            // Fix: Add Firestore Timestamps for both createdAt and updatedAt when a new lead is created
            batch.update(leadRef, 
                "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            val interactionRef = interactionsCol().document(interaction.id)
            batch.set(interactionRef, interaction)

            if (order != null) {
                val orderRef = ordersCol().document(order.id)
                batch.set(orderRef, order, com.google.firebase.firestore.SetOptions.merge())
            }

            batch.commit().await()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Unknown Firebase Error"
        }
    }

    suspend fun createSharedLeadBatch(lead: Lead, interaction: Interaction, originalLeadId: String, currentUserName: String, order: Order? = null): String? {
        return try {
            val batch = db.batch()
            
            val leadRef = leadsCol().document(lead.id)
            batch.set(leadRef, lead)
            
            batch.update(leadRef, 
                "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            val interactionRef = interactionsCol().document(interaction.id)
            batch.set(interactionRef, interaction)

            if (order != null) {
                val orderRef = ordersCol().document(order.id)
                batch.set(orderRef, order, com.google.firebase.firestore.SetOptions.merge())
            }

            val alertInteraction = Interaction(
                id = java.util.UUID.randomUUID().toString(),
                leadId = originalLeadId,
                statusAfter = "Shared Inquiry Created",
                notes = "⚠️ $currentUserName has created a shared inquiry for this customer.",
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
            )
            val alertRef = interactionsCol().document(alertInteraction.id)
            batch.set(alertRef, alertInteraction)

            batch.commit().await()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Unknown Firebase Error"
        }
    }

    suspend fun createReorderBatch(lead: Lead, order: com.nexaleads.app.data.model.Order, interaction: Interaction): String? {
        return try {
            val batch = db.batch()
            
            val leadRef = leadsCol().document(lead.id)
            batch.set(leadRef, lead)
            
            batch.update(leadRef, 
                "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            val orderRef = ordersCol().document(order.id)
            batch.set(orderRef, order)

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
    
    suspend fun diagnoseRawData(targetPhone: String): String {
        return try {
            val sanitized = com.nexaleads.app.utils.PhoneUtils.sanitizePhoneNumber(targetPhone)
            val pure = if (sanitized.length >= 10) sanitized else targetPhone.replace(Regex("[^0-9+]"), "").trim()
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            
            val result = java.lang.StringBuilder("=== DIAGNOSTICS FOR ($targetPhone) ===\n")
            result.append("purePhone: '$pure'\n")
            result.append("sanitized: '$sanitized'\n\n")
            
            val allLeads = leadsCol().whereEqualTo("assignedTo", userId).get().await()
            val exactMatches = allLeads.documents.filter { doc ->
                val p = doc.getString("phone") ?: ""
                p.contains(pure) || p.contains(sanitized) || p.contains(targetPhone)
            }
            
            result.append("Found ${exactMatches.size} leads containing this phone number in DB:\n")
            exactMatches.forEach { doc ->
                result.append("ID: ${doc.id}\n")
                result.append("  phone: '${doc.getString("phone")}'\n")
                result.append("  name: '${doc.getString("name")}'\n")
                result.append("  status: '${doc.getString("status")}'\n")
                result.append("  orderAmount: '${doc.getString("orderAmount")}'\n")
            }
            result.toString()
        } catch (e: Exception) {
            "Error diagnosing: ${e.message}"
        }
    }
}
