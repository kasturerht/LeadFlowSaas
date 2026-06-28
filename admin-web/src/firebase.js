import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
  apiKey: "AIzaSyCYFB7XDtK_l4WgLS3gPgBp4Jufv7Xkb1o",
  authDomain: "nexaleads-98a12.firebaseapp.com",
  projectId: "nexaleads-98a12",
  storageBucket: "nexaleads-98a12.firebasestorage.app",
  messagingSenderId: "1035911168697",
  appId: "1:1035911168697:web:5b073b140c38706d105168",
  measurementId: "G-22WNM5CXYR"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
export const auth = getAuth(app);
