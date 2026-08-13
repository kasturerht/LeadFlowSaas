const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function checkLeads() {
    const leadsRef = db.collection('organizations').doc('ORG_SUJATA_NUTRILIVE_8117').collection('leads');
    
    console.log("Checking Sandip Bhosale...");
    const sandip = await leadsRef.where('name', '==', 'Sandip Bhosale').get();
    sandip.forEach(doc => {
        console.log(`[${doc.id}] Status: ${doc.data().status}, Total Orders: ${doc.data().totalOrdersCount}, Lifetime: ${doc.data().lifetimeOrderValue}`);
    });
    
    console.log("Checking Mangesh Aug...");
    const mangesh = await leadsRef.where('name', '==', 'Mangesh Aug').get();
    mangesh.forEach(doc => {
        console.log(`[${doc.id}] Phone: ${doc.data().phone}, Status: ${doc.data().status}, Total Orders: ${doc.data().totalOrdersCount}, Lifetime: ${doc.data().lifetimeOrderValue}, Label: ${doc.data().label}`);
    });
}

checkLeads();
