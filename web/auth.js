// Authoritative Firebase Authentication & Session Management for Divine Stamp MIS Web
// Connects to Firebase Auth and resolves canonical UserRole

import { auth } from "./firebase-config.js";
import { 
  onAuthStateChanged, 
  signInWithEmailAndPassword, 
  signOut as fbSignOut 
} from "https://www.gstatic.com/firebasejs/10.13.0/firebase-auth.js";
import { UserRole, RoleCapabilities, webDataAccess } from "./data-access.js";

class WebAuthService {
  constructor() {
    this.currentUser = null;
    this.currentRole = UserRole.CEO; // Default view role
    this.listeners = [];
    this.isLoaded = false;
    this.lastError = null;

    this.init();
  }

  init() {
    try {
      onAuthStateChanged(auth, async (user) => {
        if (user) {
          this.currentUser = user;
          try {
            const profile = await webDataAccess.getUserProfile(user.uid);
            if (profile && profile.role) {
              this.currentRole = profile.role;
            } else {
              const storedRole = localStorage.getItem("DIVINE_USER_ROLE");
              this.currentRole = storedRole || UserRole.MANAGER;
            }
          } catch (e) {
            console.warn("[Auth] Profile lookup warning, using fallback role:", e);
            this.currentRole = UserRole.MANAGER;
          }
        } else {
          this.currentUser = null;
          const storedRole = localStorage.getItem("DIVINE_USER_ROLE");
          this.currentRole = storedRole || UserRole.CEO;
        }
        this.isLoaded = true;
        this.notifyListeners();
      }, (err) => {
        console.warn("[Auth] Auth state change error:", err);
        this.lastError = err;
        this.isLoaded = true;
        this.notifyListeners();
      });
    } catch (e) {
      console.warn("[Auth] Firebase Auth initialization notice:", e);
      this.isLoaded = true;
      this.lastError = e;
      this.notifyListeners();
    }
  }

  subscribe(listener) {
    this.listeners.push(listener);
    listener({
      user: this.currentUser,
      role: this.currentRole,
      isLoaded: this.isLoaded,
      error: this.lastError
    });
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  notifyListeners() {
    const state = {
      user: this.currentUser,
      role: this.currentRole,
      isLoaded: this.isLoaded,
      error: this.lastError
    };
    this.listeners.forEach(fn => fn(state));
  }

  async signIn(email, password) {
    this.lastError = null;
    try {
      const cred = await signInWithEmailAndPassword(auth, email, password);
      this.currentUser = cred.user;
      return cred.user;
    } catch (err) {
      this.lastError = err;
      throw err;
    }
  }

  async signOut() {
    try {
      await fbSignOut(auth);
      this.currentUser = null;
      this.notifyListeners();
    } catch (err) {
      this.lastError = err;
      throw err;
    }
  }

  setRoleOverride(role) {
    if (Object.values(UserRole).includes(role)) {
      this.currentRole = role;
      localStorage.setItem("DIVINE_USER_ROLE", role);
      this.notifyListeners();
    }
  }

  getUserContext() {
    return {
      uid: this.currentUser?.uid || "web-user",
      email: this.currentUser?.email || `${this.currentRole.toLowerCase()}@divinestamp.com`,
      role: this.currentRole
    };
  }

  can(action) {
    const perms = RoleCapabilities[this.currentRole];
    return perms ? !!perms[action] : false;
  }
}

export const webAuth = new WebAuthService();
