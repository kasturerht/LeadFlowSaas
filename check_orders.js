const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function checkOrders() {
    const orgId = 'ORG_SUJATA_NUTRILIVE_8117';
    const ordersRef = db.collection('organizations').doc(orgId).collection('orders');
    const snapshot = await ordersRef.get();
    
    console.log(`Total orders in DB: ${snapshot.size}`);
    
    let userOrders = {};
    snapshot.forEach(doc => {
        const order = doc.data();
        const assignee = order.assignedTo || 'Unassigned';
        if (!userOrders[assignee]) userOrders[assignee] = 0;
        userOrders[assignee]++;
    });
    
    console.log("Orders by assignedTo:");
    console.log(userOrders);
}

checkOrders();
