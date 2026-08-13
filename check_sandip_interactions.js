const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function checkSandipInteractions() {
    const interactionsRef = db.collection('organizations').doc('ORG_SUJATA_NUTRILIVE_8117').collection('interactions');
    const q = await interactionsRef.where('leadId', '==', 'l-16235a10-9180-4a98-9f51-7a84e547e954').get();
    
    q.forEach(doc => {
        console.log(`Interaction: ${doc.id} | StatusAfter: ${doc.data().statusAfter} | Timestamp: ${doc.data().timestamp} | isReorder: ${doc.data().isReorder}`);
    });
}

checkSandipInteractions();
