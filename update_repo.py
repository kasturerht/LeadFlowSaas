import re

with open('app/src/main/java/com/nexaleads/app/data/repository/LeadRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. ordersCol
content = content.replace(
    'fun leadsCol() = db.collection("organizations").document(orgId).collection("leads")',
    'fun leadsCol() = db.collection("organizations").document(orgId).collection("leads")\n    fun ordersCol() = db.collection("organizations").document(orgId).collection("orders_data")'
)

# 2. Add methods
methods = """
    fun getOrdersForCustomer(customerId: String): Flow<List<com.nexaleads.app.data.model.Order>> = callbackFlow {
        if (orgId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = ordersCol()
            .whereEqualTo("customerId", customerId)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val orders = snapshot.documents.mapNotNull { it.toObject(com.nexaleads.app.data.model.Order::class.java) }
                    trySend(orders)
                }
            }
        awaitClose { listener.remove() }
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
            batch.set(orderRef, order)
            val interactionRef = interactionsCol().document(interaction.id)
            batch.set(interactionRef, interaction)
            batch.commit().await()
        }
    }
"""
content = content.replace('suspend fun updateLeadAndAddInteractionBatch', methods + '\n    suspend fun updateLeadAndAddInteractionBatch')

# 3. getDashboardMetricsFlow
dash_regex = r'fun getDashboardMetricsFlow.*?awaitClose \{ listener\.remove\(\) \}\n    \}'
dash_repl = """fun getDashboardMetricsFlow(userId: String): Flow<Map<String, Long>> = callbackFlow {
        var leadsMetrics = mapOf<String, Long>("freshLeads" to 0L, "dueFollowups" to 0L, "inquiries" to 0L, "attempted" to 0L, "rejected" to 0L)
        var ordersMetrics = mapOf<String, Long>("confirmedOrders" to 0L, "pendingPayments" to 0L, "dispatched" to 0L, "delivered" to 0L, "rto" to 0L)

        val pushUpdate = {
            val combined = mutableMapOf<String, Long>()
            combined.putAll(leadsMetrics)
            combined.putAll(ordersMetrics)
            trySend(combined)
        }

        val leadsListener = leadsCol().whereEqualTo("assignedTo", userId).whereEqualTo("archived", false)
            .addSnapshotListener { snapshot, _ ->
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
            .addSnapshotListener { snapshot, _ ->
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
    }"""
content = re.sub(dash_regex, dash_repl, content, flags=re.DOTALL)

# 4. getSalesMetricsFlow
sales_regex = r'fun getSalesMetricsFlow.*?awaitClose \{ listener\.remove\(\) \}\n    \}'
sales_repl = """fun getSalesMetricsFlow(userId: String): Flow<Map<String, Long>> = callbackFlow {
        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
        val lastWeekStr = isoFormat.format(cal.time)

        val listener = ordersCol()
            .whereEqualTo("assignedTo", userId)
            .whereGreaterThanOrEqualTo("createdAt", lastWeekStr)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val todayStr = isoFormat.format(java.util.Date())
                    var todayCount = 0L; var todayRev = 0L; var weekRev = 0L; var weekCount = 0L
                    for (doc in snapshot.documents) {
                        val order = doc.toObject(com.nexaleads.app.data.model.Order::class.java) ?: continue
                        if (order.status == "Order Cancelled" || order.status == "RTO" || order.status == "Cancelled") continue
                        if (order.paymentMethod.equals("Prepaid", ignoreCase=true) && order.paymentStatus.equals("Link Sent", ignoreCase=true)) continue

                        val cAt = if (order.createdAt.length >= 10) order.createdAt.substring(0, 10) else continue
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
    }"""
content = re.sub(sales_regex, sales_repl, content, flags=re.DOTALL)

with open('app/src/main/java/com/nexaleads/app/data/repository/LeadRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated successfully")
