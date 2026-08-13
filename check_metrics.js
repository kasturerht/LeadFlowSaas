const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function checkMetrics() {
    const orgId = 'ORG_SUJATA_NUTRILIVE_8117';
    const metricsRef = db.collection('organizations').doc(orgId).collection('metrics').doc('calling');
    const doc = await metricsRef.get();
    
    if (doc.exists) {
        console.log("Metrics:", doc.data());
    } else {
        console.log("Metrics document not found!");
    }
}

checkMetrics();
