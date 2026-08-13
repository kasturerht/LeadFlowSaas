const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function fixOrderAssignees() {
    const orgId = 'ORG_SUJATA_NUTRILIVE_8117';
    const leadsRef = db.collection('organizations').doc(orgId).collection('leads');
    const ordersRef = db.collection('organizations').doc(orgId).collection('orders_data');

    const ordersSnapshot = await ordersRef.get();
    let updatedCount = 0;
    const batch = db.batch();
    let batchCount = 0;

    for (const doc of ordersSnapshot.docs) {
        const order = doc.data();
        if (order.customerId) {
            const leadDoc = await leadsRef.doc(order.customerId).get();
            if (leadDoc.exists) {
                const lead = leadDoc.data();
                if (lead.assignedTo && lead.assignedTo !== order.assignedTo) {
                    console.log(`Updating order ${doc.id} assignee from ${order.assignedTo} to ${lead.assignedTo}`);
                    batch.update(doc.ref, { assignedTo: lead.assignedTo });
                    updatedCount++;
                    batchCount++;

                    if (batchCount >= 400) {
                        await batch.commit();
                        batchCount = 0;
                    }
                }
            }
        }
    }

    if (batchCount > 0) {
        await batch.commit();
    }

    console.log(`Fixed assignee for ${updatedCount} orders.`);
}

fixOrderAssignees();
