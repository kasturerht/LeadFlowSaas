package com.enterprise.leadflow.data.repository

import com.enterprise.leadflow.data.model.Interaction
import com.enterprise.leadflow.data.model.Lead
import com.enterprise.leadflow.utils.PhoneUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

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
                            visited = doc.getBoolean("visited") ?: false,
                            assignedTo = doc.getString("assignedTo") ?: ""
                        )
                    }
                    trySend(leads)
                }
            }
        
        awaitClose { listener.remove() }
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
                    isVisitLog = doc.getBoolean("isVisitLog") ?: false
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
                        isVisitLog = doc.getBoolean("isVisitLog") ?: false
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
            val hasEverVisited = remainingInteractions.any { it.statusAfter == "Visited" || it.isVisitLog }

            val batch = db.batch()
            val leadRef = db.collection("leads").document(leadId)
            batch.update(leadRef, mapOf(
                "status" to finalStatus,
                "notes" to rebuiltNotes,
                "followUpDate" to finalFollowUpDate,
                "visited" to hasEverVisited
            ))
            
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
                    visited = doc.getBoolean("visited") ?: false,
                    assignedTo = doc.getString("assignedTo") ?: ""
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createManualLeadBatch(lead: Lead, interaction: Interaction): Boolean {
        return try {
            val batch = db.batch()
            
            val leadRef = db.collection("leads").document(lead.id)
            batch.set(leadRef, mapOf(
                "id" to lead.id,
                "name" to lead.name,
                "phone" to lead.phone,
                "source" to lead.source,
                "status" to lead.status,
                "notes" to lead.notes,
                "label" to lead.label,
                "followUpDate" to lead.followUpDate,
                "archived" to lead.archived,
                "visited" to lead.visited,
                "assignedTo" to lead.assignedTo
            ))

            val interactionRef = db.collection("interactions").document(interaction.id)
            batch.set(interactionRef, interaction)

            batch.commit().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
