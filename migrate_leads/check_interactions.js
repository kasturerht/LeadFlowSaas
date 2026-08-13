const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const serviceAccount = require("../firebase-admin.json");

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

async function check() {
    const i1 = await db.collection("organizations").doc("ORG_SUJATA_NUTRILIVE_8117").collection("leads").doc("l-0700f743-685e-4f5d-ad17-056ff7fcfe4b").collection("interactions").get();
    console.log("Lead 1 interactions:", i1.size);
    const i2 = await db.collection("organizations").doc("ORG_SUJATA_NUTRILIVE_8117").collection("leads").doc("l-badfe6d1-0c1d-4c16-9435-bc413caa1379").collection("interactions").get();
    console.log("Lead 2 interactions:", i2.size);
}
check().catch(console.error);

