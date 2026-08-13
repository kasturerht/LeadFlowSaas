const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const serviceAccount = require("../firebase-admin.json");

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function trackLead() {
    const orgsSnapshot = await db.collection("organizations").get();
    let found = false;
    for (const orgDoc of orgsSnapshot.docs) {
        const leadsSnapshot = await db.collection("organizations").doc(orgDoc.id).collection("leads")
            .where("name", ">=", "Mangesh")
            .where("name", "<=", "Mangesh\uf8ff")
            .get();
            
        for (const doc of leadsSnapshot.docs) {
            const data = doc.data();
            if (data.name.toLowerCase().includes("mangesh aug")) {
                console.log("=== LEAD FOUND ===");
                console.log(JSON.stringify(data, null, 2));
                
                // Fetch interactions correctly
                const interactionsSnapshot = await db.collection("organizations").doc(orgDoc.id).collection("interactions").where("leadId", "==", doc.id).get();
                console.log(`=== INTERACTIONS (${interactionsSnapshot.size}) ===`);
                interactionsSnapshot.forEach(iDoc => {
                    console.log(JSON.stringify(iDoc.data(), null, 2));
                });
                found = true;
            }
        }
    }
    if (!found) console.log("Lead not found.");
}
trackLead().catch(console.error);

