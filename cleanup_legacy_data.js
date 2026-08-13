const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function cleanupLegacyData() {
    const orgId = 'ORG_SUJATA_NUTRILIVE_8117';
    const leadsRef = db.collection('organizations').doc(orgId).collection('leads');
    const interactionsRef = db.collection('organizations').doc(orgId).collection('interactions');
    const ordersRef = db.collection('organizations').doc(orgId).collection('orders');

    console.log("Fetching leads...");
    const leadsSnapshot = await leadsRef.get();
    
    // Group by phone
    const leadsByPhone = {};
    const allLeads = [];
    
    leadsSnapshot.forEach(doc => {
        const lead = doc.data();
        allLeads.push({ id: doc.id, ...lead });
        const cleanPhone = (lead.phone || '').replace(/[^\d+]/g, '');
        if (cleanPhone.length >= 10) {
            if (!leadsByPhone[cleanPhone]) leadsByPhone[cleanPhone] = [];
            leadsByPhone[cleanPhone].push({ id: doc.id, ...lead });
        }
    });

    // 1. MERGE DUPLICATES
    console.log("Checking for duplicates...");
    for (const phone in leadsByPhone) {
        const duplicates = leadsByPhone[phone];
        if (duplicates.length > 1) {
            console.log(`Found ${duplicates.length} duplicates for phone ${phone}`);
            
            // Sort by most recent updatedAt or created timestamp
            duplicates.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
            
            const mainLead = duplicates[0];
            const duplicateLeads = duplicates.slice(1);
            
            for (const dup of duplicateLeads) {
                console.log(`Merging ${dup.id} into ${mainLead.id}`);
                
                // Reassign interactions
                const ints = await interactionsRef.where('leadId', '==', dup.id).get();
                for (const iDoc of ints.docs) {
                    await iDoc.ref.update({ leadId: mainLead.id });
                }
                
                // Reassign orders
                const ords = await ordersRef.where('customerId', '==', dup.id).get();
                for (const oDoc of ords.docs) {
                    await oDoc.ref.update({ customerId: mainLead.id });
                }
                
                // Merge metrics if any
                if (dup.totalOrdersCount) {
                    mainLead.totalOrdersCount = (mainLead.totalOrdersCount || 0) + dup.totalOrdersCount;
                    mainLead.lifetimeOrderValue = (mainLead.lifetimeOrderValue || 0) + (dup.lifetimeOrderValue || 0);
                    await leadsRef.doc(mainLead.id).update({
                        totalOrdersCount: mainLead.totalOrdersCount,
                        lifetimeOrderValue: mainLead.lifetimeOrderValue
                    });
                }
                
                // Delete duplicate
                await leadsRef.doc(dup.id).delete();
                console.log(`Deleted duplicate lead ${dup.id}`);
            }
        }
    }

    // 2. FIX STATUSES
    console.log("Fetching all interactions for status fix...");
    const allInteractionsSnapshot = await interactionsRef.get();
    const interactionsByLead = {};
    allInteractionsSnapshot.forEach(doc => {
        const data = doc.data();
        if (!interactionsByLead[data.leadId]) interactionsByLead[data.leadId] = [];
        interactionsByLead[data.leadId].push(data);
    });

    // Refresh leads snapshot after merges
    const finalLeadsSnapshot = await leadsRef.get();
    for (const doc of finalLeadsSnapshot.docs) {
        const lead = doc.data();
        const ints = interactionsByLead[doc.id] || [];
        
        if (ints.length > 0) {
            ints.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
            const latestInt = ints[0];
            
            // If the latest interaction is Order Placed, but current status is New or Pending
            if (latestInt.statusAfter === 'Order Placed' && (lead.status === 'New' || lead.status === 'Pending')) {
                console.log(`Fixing status for ${lead.name} (${doc.id}): ${lead.status} -> Order Placed`);
                await doc.ref.update({ status: 'Order Placed' });
            }
            // Fix Sandip's case specifically if totalOrdersCount > 0 but status is New
            else if ((lead.totalOrdersCount || 0) > 0 && (lead.status === 'New' || lead.status === 'Pending')) {
                console.log(`Fixing status for ${lead.name} (${doc.id}) because totalOrders > 0: ${lead.status} -> Order Placed`);
                await doc.ref.update({ status: 'Order Placed' });
            }
        }
    }

    console.log("Cleanup complete!");
}

cleanupLegacyData();
