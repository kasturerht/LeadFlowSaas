const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const fs = require('fs');

const serviceAccount = require('./firebase-admin.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function exportData() {
    console.log("Starting data export...");
    
    // Get all leads
    console.log("Fetching leads...");
    const leadsSnapshot = await db.collectionGroup('leads').get();
    const leads = [];
    leadsSnapshot.forEach(doc => {
        leads.push({ id: doc.id, ref: doc.ref.path, ...doc.data() });
    });
    fs.writeFileSync('leads_export.json', JSON.stringify(leads, null, 2));
    console.log(`Exported ${leads.length} leads.`);
    
    // Get all interactions
    console.log("Fetching interactions...");
    const interactionsSnapshot = await db.collectionGroup('interactions').get();
    const interactions = [];
    interactionsSnapshot.forEach(doc => {
        interactions.push({ id: doc.id, ref: doc.ref.path, ...doc.data() });
    });
    fs.writeFileSync('interactions_export.json', JSON.stringify(interactions, null, 2));
    console.log(`Exported ${interactions.length} interactions.`);
    
    // Get organizations
    console.log("Fetching organizations...");
    const orgsSnapshot = await db.collection('organizations').get();
    const orgs = [];
    orgsSnapshot.forEach(doc => {
        orgs.push({ id: doc.id, ...doc.data() });
    });
    fs.writeFileSync('orgs_export.json', JSON.stringify(orgs, null, 2));
    console.log(`Exported ${orgs.length} organizations.`);

    console.log("Export complete!");
    process.exit(0);
}

exportData().catch(console.error);
