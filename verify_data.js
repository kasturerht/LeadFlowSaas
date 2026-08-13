const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function verifyMigration() {
    console.log("🔍 Starting Data Verification...");
    
    const orgsRef = db.collection('organizations');
    const orgsSnapshot = await orgsRef.select().get();
    
    for (const orgDoc of orgsSnapshot.docs) {
        const orgId = orgDoc.id;
        console.log(`\n--- Verification for Organization: ${orgId} ---`);
        
        const leadsRef = db.collection(`organizations/${orgId}/leads`);
        const ordersRef = db.collection(`organizations/${orgId}/orders_data`);
        
        // 1. Count Total Customers (Leads)
        const leadsSnapshot = await leadsRef.count().get();
        const totalCustomers = leadsSnapshot.data().count;
        
        // 2. Count Total Orders
        const ordersSnapshot = await ordersRef.count().get();
        const totalOrders = ordersSnapshot.data().count;
        
        console.log(`📊 Total Customers (Leads): ${totalCustomers}`);
        console.log(`🛒 Total Real Orders (orders_data): ${totalOrders}`);
        
        if (totalCustomers === 0) continue;

        // 3. Find 1 parent customer who has multiple orders (or at least 1 order)
        console.log(`\n🔎 Fetching sample customer data for cross-checking...`);
        const sampleParents = await leadsRef.where('totalOrdersCount', '>', 0).limit(1).get();
        
        if (!sampleParents.empty) {
            const sampleCustomer = sampleParents.docs[0].data();
            sampleCustomer.id = sampleParents.docs[0].id;
            
            console.log(`\n👤 Customer Profile [${sampleCustomer.name} - ${sampleCustomer.phone}]:`);
            console.log(`   - totalOrdersCount: ${sampleCustomer.totalOrdersCount}`);
            console.log(`   - lifetimeOrderValue: ₹${sampleCustomer.lifetimeOrderValue}`);
            
            // 4. Fetch their orders
            const theirOrders = await ordersRef.where('customerId', '==', sampleCustomer.id).get();
            console.log(`\n📦 Orders found in 'orders_data' for this customer: ${theirOrders.size}`);
            
            theirOrders.forEach(orderDoc => {
                const order = orderDoc.data();
                console.log(`   -> Order ID: ${orderDoc.id} | Product: ${order.product} | Status: ${order.status} | Amount: ₹${order.orderAmountNum}`);
            });
            
            if (theirOrders.size === sampleCustomer.totalOrdersCount) {
                console.log(`\n✅ MATCH! The customer's totalOrdersCount matches the number of actual records in orders_data.`);
            } else {
                console.log(`\n⚠️ MISMATCH! The customer's totalOrdersCount is ${sampleCustomer.totalOrdersCount} but we found ${theirOrders.size} orders. (Note: Cancelled/RTO orders might not be counted in totalOrdersCount)`);
            }
        } else {
            console.log(`ℹ️ No customers found with totalOrdersCount > 0.`);
        }
        
        // 5. Look up "Mangesh Aug" if they exist
        const mangeshSnapshot = await leadsRef.where('name', '==', 'Mangesh Aug').get();
        if (!mangeshSnapshot.empty) {
            console.log(`\n🔍 Found 'Mangesh Aug'! Let's check their data:`);
            mangeshSnapshot.forEach(doc => {
                const data = doc.data();
                console.log(`   - ID: ${doc.id} | Orders: ${data.totalOrdersCount} | LTV: ₹${data.lifetimeOrderValue}`);
            });
        }
    }
    
    console.log("\n✅ Verification script completed.");
}

verifyMigration().then(() => {
    process.exit(0);
}).catch(err => {
    console.error("Verification failed:", err);
    process.exit(1);
});
