const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

// We need to fetch product consumption days for retention calculation
async function getProductConsumptionDays(orgId, sku) {
    try {
        const prodDoc = await db.collection("organizations").doc(orgId).collection("products").doc(sku).get();
        if (prodDoc.exists) {
            const data = prodDoc.data();
            if (data.consumptionDays) {
                return Number(data.consumptionDays);
            }
        }
    } catch (e) {
        console.error("Error fetching product:", e);
    }
    return 30; // Default 30 days
}

exports.onLeadWritten = functions.region('asia-south1').firestore
    .document("organizations/{orgId}/leads/{leadId}")
    .onWrite(async (change, context) => {
        const { orgId, leadId } = context.params;

        // Document was deleted
        if (!change.after.exists) {
            return null;
        }

        const dataBefore = change.before.exists ? change.before.data() : {};
        const dataAfter = change.after.data();

        // 1. Prevent Infinite Loops
        // Only proceed if status actually changed, it's a new document, OR assignedToName is missing
        const statusChanged = dataBefore.status !== dataAfter.status;
        const isNew = !change.before.exists;
        const needsNameDenormalization = dataAfter.assignedTo && !dataAfter.assignedToName;

        // If status didn't change, it's not a new document, and we don't need to denormalize name
        if (!statusChanged && !isNew && !needsNameDenormalization) {
            return null;
        }

        const status = dataAfter.status || "";
        let updates = {};

        // 0. Denormalize Telecaller Name (Silicon Valley Architecture)
        // If assignedTo exists but assignedToName is missing, fetch and save it permanently
        if (dataAfter.assignedTo && !dataAfter.assignedToName) {
            try {
                const userDoc = await db.collection("organizations").doc(orgId).collection("users").doc(dataAfter.assignedTo).get();
                if (userDoc.exists) {
                    updates.assignedToName = userDoc.data().name || "Unknown Agent";
                } else {
                    updates.assignedToName = "Deleted/Unknown Agent";
                }
            } catch (e) {
                console.error("Error fetching user name:", e);
                updates.assignedToName = "Error Fetching Name";
            }
        }

        // 2. Business Rule: Order Placed -> Pending Pack
        if (status === "Order Placed") {
            // Only update if dispatchStatus isn't already set to Pending
            if (dataAfter.dispatchStatus !== "Pending") {
                updates.dispatchStatus = "Pending";
                updates.orderDate = admin.firestore.FieldValue.serverTimestamp();
            }
        } 
        // 3. Business Rule: Delivered -> Calculate Exhaustion Date
        else if (status === "Delivered") {
            if (dataAfter.dispatchStatus !== "Delivered") {
                updates.dispatchStatus = "Delivered";
                updates.deliveredAt = new Date().toISOString();
                
                // Calculate exhaustionDate
                let shortestConsumptionDays = 30;
                if (dataAfter.baseProductsBreakdown) {
                    const parts = dataAfter.baseProductsBreakdown.split(',').filter(Boolean);
                    let minDays = Infinity;
                    for (const part of parts) {
                        const [sku, qty] = part.split(':');
                        const pDays = await getProductConsumptionDays(orgId, sku);
                        const q = Number(qty) || 1;
                        const totalDays = pDays * q;
                        if (totalDays < minDays) minDays = totalDays;
                    }
                    if (minDays !== Infinity) shortestConsumptionDays = minDays;
                }

                const today = new Date();
                today.setDate(today.getDate() + shortestConsumptionDays);
                updates.exhaustionDate = today.toISOString();
            }
        }
        // 4. Business Rule: RTO / Returned
        else if (status === "RTO" || status === "Returned") {
            if (dataAfter.dispatchStatus !== "Returned") {
                updates.dispatchStatus = "Returned";
                updates.returnedAt = new Date().toISOString();
            }
        }
        // 5. Business Rule: Reverse / Mistake handling
        // If it was Order Placed, but now it's Cancelled or Not Interested
        else if (dataBefore.status === "Order Placed" && (status === "Order Cancelled" || status === "Not Interested")) {
            updates.dispatchStatus = "Cancelled";
        }
        else if (status === "Order Cancelled") {
            if (dataAfter.dispatchStatus !== "Cancelled") {
                updates.dispatchStatus = "Cancelled";
            }
        }

        // If there are updates to apply, apply them to break the loop
        if (Object.keys(updates).length > 0) {
            console.log(`Applying Cloud Function Rules for Lead ${leadId} in Org ${orgId}`);
            return change.after.ref.update(updates);
        }

        return null;
    });
