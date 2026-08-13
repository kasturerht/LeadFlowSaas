const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function migrateOrders() {
    console.log("Starting Migration (Scalable for 100k+ Leads)...");
    
    // Process organizations in chunks if there are 1000+
    const orgsRef = db.collection('organizations');
    const orgsSnapshot = await orgsRef.select().get(); // Only get DocumentReference to save memory
    let totalMigrated = 0;
    
    for (const orgDoc of orgsSnapshot.docs) {
        const orgId = orgDoc.id;
        console.log(`\n--- Processing Organization: ${orgId} ---`);
        
        const leadsRef = db.collection(`organizations/${orgId}/leads`);
        const ordersRef = db.collection(`organizations/${orgId}/orders_data`);
        const interactionsRef = db.collection(`organizations/${orgId}/interactions`);
        
        // 1. Identify parent leads by streaming to save memory
        let parentLeads = [];
        const leadsStream = leadsRef.stream();
        
        let leadCount = 0;
        let childrenByParent = new Map(); // parentId -> [child1, child2]
        let parentsMap = new Map(); // id -> parent object
        
        for await (const doc of leadsStream) {
            leadCount++;
            const data = doc.data();
            data.id = doc.id;
            
            if (data.originalLeadId) {
                // This is a child
                if (!childrenByParent.has(data.originalLeadId)) {
                    childrenByParent.set(data.originalLeadId, []);
                }
                childrenByParent.get(data.originalLeadId).push(data);
            } else {
                // This is a parent
                parentsMap.set(data.id, data);
            }
            
            if (leadCount % 5000 === 0) {
                console.log(`  Read ${leadCount} leads from DB...`);
            }
        }
        
        console.log(`  Finished reading ${leadCount} total leads. Found ${parentsMap.size} parents and ${childrenByParent.size} parents with children.`);
        
        let batch = db.batch();
        let batchCount = 0;
        
        async function commitBatchIfNeeded(force = false) {
            if (batchCount > 400 || force) {
                if (batchCount > 0) {
                    await batch.commit();
                    batch = db.batch();
                    batchCount = 0;
                }
            }
        }
        
        // 2. Process all parents and their children
        for (const [parentId, parent] of parentsMap.entries()) {
            const children = childrenByParent.get(parentId) || [];
            
            const customerOrders = [];
            
            // Does parent have an order?
            const isParentOrder = parent.convertedAt || ['Order Placed', 'Dispatched', 'Delivered', 'RTO', 'Order Cancelled', 'Cancellation Pending'].includes(parent.status);
            if (isParentOrder) {
                customerOrders.push(parent);
            }
            
            customerOrders.push(...children);
            
            if (customerOrders.length === 0) continue; // No orders for this customer
            
            let totalOrdersCount = 0;
            let lifetimeOrderValue = 0;
            
            for (const orderLead of customerOrders) {
                const isRevenue = !['Order Cancelled', 'RTO', 'Cancelled', 'Cancellation Pending'].includes(orderLead.status);
                const isPending = orderLead.paymentMethod?.toLowerCase() === 'prepaid' && orderLead.paymentStatus?.toLowerCase() === 'link sent';
                
                if (isRevenue && !isPending) {
                    totalOrdersCount++;
                    lifetimeOrderValue += (orderLead.orderAmountNum || 0);
                }
                
                const orderId = orderLead.id === parent.id ? "o-" + parent.id : "o-" + orderLead.id;
                const orderDoc = {
                    id: orderId,
                    customerId: parent.id,
                    assignedTo: orderLead.assignedTo || parent.assignedTo || "",
                    product: orderLead.product || "",
                    baseProductsBreakdown: orderLead.baseProductsBreakdown || "",
                    originalTotalValue: orderLead.originalTotalValue || "",
                    discountAmount: orderLead.discountAmount || "",
                    orderAmount: orderLead.orderAmount || "",
                    orderAmountNum: orderLead.orderAmountNum || 0,
                    status: orderLead.status || "",
                    paymentMethod: orderLead.paymentMethod || "",
                    paymentStatus: orderLead.paymentStatus || "",
                    cancellationReason: orderLead.cancellationReason || null,
                    cancellationNotes: orderLead.cancellationNotes || null,
                    cancellationRequestedAt: orderLead.cancellationRequestedAt || null,
                    createdAt: orderLead.convertedAt || orderLead.createdAt || new Date().toISOString(),
                    createdAtMillis: orderLead.createdAtMillis || Date.now(),
                    updatedAt: orderLead.updatedAt || Date.now()
                };
                
                batch.set(ordersRef.doc(orderId), orderDoc);
                batchCount++;
                await commitBatchIfNeeded();
            }
            
            // 3. Move interactions and delete child leads
            for (const child of children) {
                // Fetch interactions. We await here but since interactions are small per child, it's ok.
                const intsSnapshot = await interactionsRef.where('leadId', '==', child.id).get();
                intsSnapshot.forEach(intDoc => {
                    batch.update(interactionsRef.doc(intDoc.id), { leadId: parent.id });
                    batchCount++;
                });
                
                batch.delete(leadsRef.doc(child.id));
                batchCount++;
                await commitBatchIfNeeded();
            }
            
            // 4. Update parent metrics
            if (parent.totalOrdersCount !== totalOrdersCount || parent.lifetimeOrderValue !== lifetimeOrderValue) {
                batch.update(leadsRef.doc(parent.id), {
                    totalOrdersCount: totalOrdersCount,
                    lifetimeOrderValue: lifetimeOrderValue
                });
                batchCount++;
                await commitBatchIfNeeded();
            }
            
            totalMigrated += customerOrders.length;
        }
        
        // Handle orphaned children (children whose parent was deleted)
        for (const [parentId, children] of childrenByParent.entries()) {
            if (!parentsMap.has(parentId)) {
                console.warn(`  Warning: Found ${children.length} orphaned child leads for non-existent parent ${parentId}. Re-promoting them as parents.`);
                
                // Promote the first child to parent
                const newParent = children[0];
                const otherChildren = children.slice(1);
                
                batch.update(leadsRef.doc(newParent.id), { originalLeadId: FieldValue.delete() });
                batchCount++;
                await commitBatchIfNeeded();
                
                const customerOrders = [...children];
                let totalOrdersCount = 0;
                let lifetimeOrderValue = 0;
                
                for (const orderLead of customerOrders) {
                    const isRevenue = !['Order Cancelled', 'RTO', 'Cancelled'].includes(orderLead.status);
                    const isPending = orderLead.paymentMethod?.toLowerCase() === 'prepaid' && orderLead.paymentStatus?.toLowerCase() === 'link sent';
                    
                    if (isRevenue && !isPending) {
                        totalOrdersCount++;
                        lifetimeOrderValue += (orderLead.orderAmountNum || 0);
                    }
                    
                    const orderId = "o-" + orderLead.id;
                    const orderDoc = {
                        id: orderId,
                        customerId: newParent.id,
                        assignedTo: orderLead.assignedTo || "",
                        product: orderLead.product || "",
                        baseProductsBreakdown: orderLead.baseProductsBreakdown || "",
                        originalTotalValue: orderLead.originalTotalValue || "",
                        discountAmount: orderLead.discountAmount || "",
                        orderAmount: orderLead.orderAmount || "",
                        orderAmountNum: orderLead.orderAmountNum || 0,
                        status: orderLead.status || "",
                        paymentMethod: orderLead.paymentMethod || "",
                        paymentStatus: orderLead.paymentStatus || "",
                        createdAt: orderLead.convertedAt || orderLead.createdAt || new Date().toISOString(),
                        createdAtMillis: orderLead.createdAtMillis || Date.now(),
                        updatedAt: orderLead.updatedAt || Date.now()
                    };
                    
                    batch.set(ordersRef.doc(orderId), orderDoc);
                    batchCount++;
                }
                
                for (const child of otherChildren) {
                    const intsSnapshot = await interactionsRef.where('leadId', '==', child.id).get();
                    intsSnapshot.forEach(intDoc => {
                        batch.update(interactionsRef.doc(intDoc.id), { leadId: newParent.id });
                        batchCount++;
                    });
                    
                    batch.delete(leadsRef.doc(child.id));
                    batchCount++;
                }
                
                batch.update(leadsRef.doc(newParent.id), {
                    totalOrdersCount: totalOrdersCount,
                    lifetimeOrderValue: lifetimeOrderValue
                });
                batchCount++;
                await commitBatchIfNeeded();
                totalMigrated += customerOrders.length;
            }
        }
        
        await commitBatchIfNeeded(true);
        console.log(`  Finished org ${orgId}. Migrated a total of ${totalMigrated} orders.`);
    }
    
    console.log(`\n✅ Migration script completed successfully. Total orders migrated across all orgs: ${totalMigrated}`);
}

migrateOrders().then(() => {
    process.exit(0);
}).catch(err => {
    console.error("Migration failed:", err);
    process.exit(1);
});
