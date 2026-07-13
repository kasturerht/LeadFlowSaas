import { db } from "./src/firebase.js";
import { collection, query, limit, getDocs } from "firebase/firestore";

async function inspect() {
  console.log("=== INSPECTING INTERACTIONS ===");
  const intSnap = await getDocs(query(collection(db, "interactions"), limit(2)));
  if (intSnap.empty) {
    console.log("No interactions found.");
  } else {
    intSnap.forEach(doc => {
      console.log(`Interaction ID: ${doc.id}`);
      console.log(JSON.stringify(doc.data(), null, 2));
    });
  }

  console.log("\n=== INSPECTING LEADS ===");
  const leadSnap = await getDocs(query(collection(db, "leads"), limit(2)));
  if (leadSnap.empty) {
    console.log("No leads found.");
  } else {
    leadSnap.forEach(doc => {
      console.log(`Lead ID: ${doc.id}`);
      console.log(JSON.stringify(doc.data(), null, 2));
    });
  }
  process.exit(0);
}

inspect().catch(console.error);
