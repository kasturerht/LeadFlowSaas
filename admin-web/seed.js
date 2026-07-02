import { initializeApp } from "firebase/app";
import { getFirestore, doc, setDoc, getDocs, collection } from "firebase/firestore";

// Read Firebase config from the existing config file 
import { db } from "./src/firebase.js";

const defaultProducts = [
  { id: "prod_1", name: "Spirulina", price: 999.0, description: "Premium Organic Spirulina", emojiIcon: "🌿", sortOrder: 1, isActive: true },
  { id: "prod_2", name: "Sea Buckthorn", price: 1299.0, description: "Himalayan Sea Buckthorn Juice", emojiIcon: "🥃", sortOrder: 2, isActive: true },
  { id: "prod_3", name: "Spirulina Face Pack", price: 499.0, description: "Rejuvenating Face Pack", emojiIcon: "🧴", sortOrder: 3, isActive: true },
  { id: "prod_4", name: "Spirulina Cookies", price: 299.0, description: "Healthy Snack Cookies", emojiIcon: "🍪", sortOrder: 4, isActive: true },
  { id: "prod_5", name: "Multiple / Combos", price: 0.0, description: "Custom combo package", emojiIcon: "📦", sortOrder: 5, isActive: true }
];

async function seed() {
  console.log("Seeding products...");
  
  // Checking if products already exist
  const snapshot = await getDocs(collection(db, "products"));
  if (!snapshot.empty) {
    console.log("Products already exist in database.");
  }
  
  // We'll write them anyway to update icons/sortOrder
  for (const p of defaultProducts) {
    const docRef = doc(db, "products", p.id);
    const { id, ...data } = p;
    await setDoc(docRef, data, { merge: true });
    console.log(`Added ${p.name}`);
  }
  console.log("Finished seeding!");
  process.exit(0);
}

seed().catch(console.error);
