const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('../firebase-admin.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

function normalizeStatus(raw) {
    if (!raw || typeof raw !== 'string' || raw.trim() === '') return "Pending";
    const trimmed = raw.trim();
    const lower = trimmed.toLowerCase();
    if (lower.includes("enquiry") || lower.includes("inquiry")) return "Product Inquiry Only";
    if (lower.includes("wrong number") || lower.includes("invalid")) return "Invalid";
    
    switch(lower) {
        case "no answer":
        case "busy":
        case "busy / cut":
        case "call not answered":
            return "Call Not Answered";
        case "warm lead":
        case "warm / on hold":
        case "product inquiry only":
            return "Product Inquiry Only";
        case "follow-up":
        case "visit scheduled":
            return "Follow-up";
        case "converted":
        case "visited":
        case "visited (actual)":
        case "order placed":
            return "Order Placed";
        case "not interested":
            return "Not Interested";
        case "invalid no.":
        case "invalid/wrong number":
        case "invalid":
            return "Invalid";
        case "order cancelled":
        case "cancelled":
            return "Order Cancelled";
        case "dispatched":
            return "Dispatched";
        case "delivered":
            return "Delivered";
        case "rto":
        case "return":
        case "returned":
            return "RTO";
        default:
            return trimmed;
    }
}

function getPrimaryCategory(data) {
    if (data.archived === true) return "ARCHIVED";
    const normStatus = normalizeStatus(data.status);
    if (normStatus === "RTO") return "RTO";
    if (normStatus === "Delivered") return "DELIVERED";
    if (normStatus === "Dispatched") return "DISPATCHED";
    if (normStatus === "Order Placed") return "CONVERTED";
    if (normStatus === "Not Interested" || normStatus === "Invalid" || normStatus === "Order Cancelled") return "REJECTED";
    
    if (data.followUpDate && data.followUpDate.trim() !== "") return "FOLLOWUP";
    
    if (normStatus === "Follow-up") return "FOLLOWUP";
    if (normStatus === "Call Not Answered") return "ATTEMPTED";
    if (normStatus === "Product Inquiry Only") return "INQUIRY";
    return "PENDING";
}

async function migrate() {
    console.log('Starting migration...');
    const orgsSnapshot = await db.collection('organizations').get();
    let totalUpdated = 0;
    
    for (const orgDoc of orgsSnapshot.docs) {
        console.log(`Processing org: ${orgDoc.id}`);
        const leadsSnapshot = await db.collection('organizations').doc(orgDoc.id).collection('leads').get();
        console.log(`Found ${leadsSnapshot.size} leads in ${orgDoc.id}. Migrating...`);
        
        let count = 0;
        const batchSize = 400;
        let batch = db.batch();
        let batchCount = 0;
        
        for (const doc of leadsSnapshot.docs) {
            const data = doc.data();
            const primaryCategory = getPrimaryCategory(data);
            
            if (data.primaryCategory !== primaryCategory) {
                batch.update(doc.ref, { primaryCategory: primaryCategory });
                batchCount++;
                count++;
            }
            
            if (batchCount >= batchSize) {
                await batch.commit();
                console.log(`Committed batch of ${batchCount} in ${orgDoc.id}`);
                batch = db.batch();
                batchCount = 0;
            }
        }
        
        if (batchCount > 0) {
            await batch.commit();
            console.log(`Committed final batch of ${batchCount} in ${orgDoc.id}`);
        }
        
        console.log(`Migration completed for ${orgDoc.id}. Updated ${count} leads.`);
        totalUpdated += count;
    }
    
    console.log(`Total Migration completed. Updated ${totalUpdated} leads globally.`);
}

migrate().catch(console.error);
