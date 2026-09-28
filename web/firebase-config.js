// Authoritative Firebase Web Configuration and SDK Initialization
// Divine Stamp Manufacturing MIS

import { initializeApp, getApps, getApp } from "https://www.gstatic.com/firebasejs/10.13.0/firebase-app.js";
import { 
  getAuth, 
  onAuthStateChanged, 
  signInWithEmailAndPassword, 
  signOut as fbSignOut,
  createUserWithEmailAndPassword
} from "https://www.gstatic.com/firebasejs/10.13.0/firebase-auth.js";
import { 
  getFirestore, 
  collection, 
  doc, 
  getDoc, 
  getDocs, 
  setDoc, 
  addDoc, 
  updateDoc, 
  query, 
  where, 
  orderBy, 
  limit, 
  onSnapshot,
  serverTimestamp,
  connectFirestoreEmulator
} from "https://www.gstatic.com/firebasejs/10.13.0/firebase-firestore.js";

// Canonical Firebase Configuration
// Supports dynamic injection via window.__FIREBASE_CONFIG__ or localStorage
const defaultFirebaseConfig = {
  apiKey: "AIzaSyDivineMIS2026ProductionKeyPlaceholder",
  authDomain: "divine-mis-dspl.firebaseapp.com",
  projectId: "divine-mis-dspl",
  storageBucket: "divine-mis-dspl.appspot.com",
  messagingSenderId: "399514583924",
  appId: "1:399514583924:web:divinemisweb2026prod"
};

export function getActiveFirebaseConfig() {
  if (typeof window !== "undefined" && window.__FIREBASE_CONFIG__) {
    return window.__FIREBASE_CONFIG__;
  }
  try {
    const saved = localStorage.getItem("DIVINE_FIREBASE_CONFIG");
    if (saved) {
      return JSON.parse(saved);
    }
  } catch (e) {
    console.warn("Could not read saved Firebase config:", e);
  }
  return defaultFirebaseConfig;
}

// Initialize Firebase App
export const app = getApps().length === 0 ? initializeApp(getActiveFirebaseConfig()) : getApp();

// Initialize Services
export const auth = getAuth(app);
export const db = getFirestore(app);

// Connectivity state notifier
export const FirebaseState = {
  initialized: true,
  authReady: false,
  firestoreReady: false,
  lastError: null,
  isEmulator: false
};

console.log("[Divine MIS Web] Firebase Web SDK v10.13.0 successfully initialized. Target project:", getActiveFirebaseConfig().projectId);
