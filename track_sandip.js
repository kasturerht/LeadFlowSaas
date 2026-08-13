const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function trackJourney(name) {
    console.log(`\n🔍 Tracking Journey for Lead: "${name}"...\n`);
    
    const orgsRef = db.collection('organizations');
    const orgsSnapshot = await orgsRef.select().get();
    
    let found = false;

    for (const orgDoc of orgsSnapshot.docs) {
        const orgId = orgDoc.id;
        const leadsRef = db.collection(`organizations/${orgId}/leads`);
        
        const nameSnapshot = await leadsRef.where('name', '==', name).get();
        if (nameSnapshot.empty) continue;
        
        for (const doc of nameSnapshot.docs) {
            found = true;
            const lead = doc.data();
            const leadId = doc.id;
            
            console.log(`====================================================`);
            console.log(`👤 PROFILE FOUND in Org: ${orgId}`);
            console.log(`----------------------------------------------------`);
            console.log(`- ID: ${leadId}`);
            console.log(`- Name: ${lead.name}`);
            console.log(`- Phone: ${lead.phone}`);
            console.log(`- Current Status: ${lead.status || 'N/A'}`);
            console.log(`- Total Orders Count: ${lead.totalOrdersCount !== undefined ? lead.totalOrdersCount : 'undefined (0)'}`);
            console.log(`- Lifetime Value: ₹${lead.lifetimeOrderValue !== undefined ? lead.lifetimeOrderValue : 'undefined (0)'}`);
            console.log(`- Original Lead ID (if any): ${lead.originalLeadId || 'None (This is the parent)'}`);
            
            // Fetch Orders
            console.log(`\n📦 PAST ORDERS (orders_data):`);
            const ordersRef = db.collection(`organizations/${orgId}/orders_data`);
            const ordersSnapshot = await ordersRef.where('customerId', '==', leadId).get();
            
            if (ordersSnapshot.empty) {
                console.log(`   -> No orders found in orders_data.`);
            } else {
                ordersSnapshot.forEach(orderDoc => {
                    const order = orderDoc.data();
                    console.log(`   -> [Order] ID: ${orderDoc.id} | Product: ${order.product} | Amt: ₹${order.orderAmountNum} | Status: ${order.status}`);
                });
            }

            // Fetch Interactions
            console.log(`\n📞 CALL TIMELINE & INTERACTIONS:`);
            const interactionsRef = db.collection(`organizations/${orgId}/interactions`);
            const interactionsSnapshot = await interactionsRef.where('leadId', '==', leadId).orderBy('timestamp', 'asc').get();
            
            if (interactionsSnapshot.empty) {
                console.log(`   -> No interactions found.`);
            } else {
                interactionsSnapshot.forEach(interactionDoc => {
                    const interaction = interactionDoc.data();
                    const date = new Date(interaction.timestamp).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' });
                    console.log(`   -> [${date}] By: ${interaction.callerName} | Type: ${interaction.type} | Notes: ${interaction.notes}`);
                });
            }
            console.log(`====================================================\n`);
        }
    }
    
    if (!found) {
        console.log(`❌ Lead with name "${name}" not found in any organization.`);
    }
    
    console.log("✅ Journey tracking completed.");
}

trackJourney('Sandip Bhosale').then(() => {
    process.exit(0);
}).catch(err => {
    console.error("Error tracking journey:", err);
    process.exit(1);
});
