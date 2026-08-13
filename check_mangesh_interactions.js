const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function checkMangeshInteractions() {
    const interactionsRef = db.collection('organizations').doc('ORG_SUJATA_NUTRILIVE_8117').collection('interactions');
    const q1 = await interactionsRef.where('leadId', '==', 'l-0700f743-685e-4f5d-ad17-056ff7fcfe4b').get();
    const q2 = await interactionsRef.where('leadId', '==', 'l-badfe6d1-0c1d-4c16-9435-bc413caa1379').get();
    
    console.log("Mangesh 1:");
    q1.forEach(doc => {
        console.log(`Interaction: ${doc.id} | StatusAfter: ${doc.data().statusAfter} | Timestamp: ${doc.data().timestamp} | isReorder: ${doc.data().isReorder}`);
    });
    
    console.log("Mangesh 2:");
    q2.forEach(doc => {
        console.log(`Interaction: ${doc.id} | StatusAfter: ${doc.data().statusAfter} | Timestamp: ${doc.data().timestamp} | isReorder: ${doc.data().isReorder}`);
    });
}

checkMangeshInteractions();
