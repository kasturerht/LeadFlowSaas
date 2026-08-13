const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function syncLeadStatus() {
    const orgId = 'ORG_SUJATA_NUTRILIVE_8117';
    const leadsRef = db.collection('organizations').doc(orgId).collection('leads');
    const interactionsRef = db.collection('organizations').doc(orgId).collection('interactions');

    const leadsSnapshot = await leadsRef.get();
    let mismatchedCount = 0;
    
    // We can also fetch ALL interactions into memory to avoid hitting the DB N times
    console.log("Fetching all interactions...");
    const allInteractionsSnapshot = await interactionsRef.get();
    const interactionsByLead = {};
    
    allInteractionsSnapshot.forEach(doc => {
        const data = doc.data();
        if (!interactionsByLead[data.leadId]) {
            interactionsByLead[data.leadId] = [];
        }
        interactionsByLead[data.leadId].push(data);
    });
    
    console.log("Processing leads...");
    const batch = db.batch();
    let batchCount = 0;

    for (const leadDoc of leadsSnapshot.docs) {
        const lead = leadDoc.data();
        const interactions = interactionsByLead[leadDoc.id] || [];
        
        if (interactions.length > 0) {
            interactions.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
            const latestInteraction = interactions[0];
            const latestStatus = latestInteraction.statusAfter;

            if (latestStatus && latestStatus !== lead.status) {
                console.log(`[${leadDoc.id}] ${lead.name} | Current: ${lead.status} -> Latest: ${latestStatus}`);
                mismatchedCount++;
                // batch.update(leadDoc.ref, { status: latestStatus });
                // batchCount++;
                // if (batchCount >= 400) { await batch.commit(); batchCount = 0; }
            }
        }
    }
    // if (batchCount > 0) await batch.commit();
    console.log(`Total mismatched leads: ${mismatchedCount}`);
}

syncLeadStatus();
