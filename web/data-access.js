// Authoritative Web Data-Access Layer for Divine Stamp Manufacturing MIS
// Adheres strictly to FirestoreConstants.kt and com.example.model domain contracts

import { db } from "./firebase-config.js";
import { 
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
  serverTimestamp 
} from "https://www.gstatic.com/firebasejs/10.13.0/firebase-firestore.js";

import {
  ManufacturingModel,
  ALL_MODELS,
  parseManufacturingModel,
  Department,
  CANONICAL_DEPARTMENTS,
  parseDepartment,
  ManufacturingFlow,
  ModelApplicabilityValidator,
  RejectionSource,
  BusinessArea,
  RejectionSide,
  parseRejectionSide,
  RebuffingRule,
  STANDARD_DEFECTS,
  validateDefectName,
  FirestoreConstants,
  MetricValue
} from "./manufacturing-contracts.js";

export {
  ManufacturingModel,
  ALL_MODELS,
  parseManufacturingModel,
  Department,
  CANONICAL_DEPARTMENTS,
  parseDepartment,
  ManufacturingFlow,
  ModelApplicabilityValidator,
  RejectionSource,
  BusinessArea,
  RejectionSide,
  parseRejectionSide,
  RebuffingRule,
  STANDARD_DEFECTS,
  validateDefectName,
  FirestoreConstants,
  MetricValue
};

/**
 * Authoritative Canonical Collection Names
 */
export const Collections = Object.freeze({
  PRODUCTION: FirestoreConstants.COLLECTION_PRODUCTION,
  REJECTION: FirestoreConstants.COLLECTION_REJECTION,
  STOCK: FirestoreConstants.COLLECTION_STOCK,
  PLANS: FirestoreConstants.COLLECTION_PLANS,
  USERS: FirestoreConstants.COLLECTION_USERS,
  ACTIONS: FirestoreConstants.COLLECTION_ACTIONS,
  ALERTS: FirestoreConstants.COLLECTION_ALERTS,
  CUSTOMER_END_PLANS: FirestoreConstants.COLLECTION_CUSTOMER_END_PLANS,
  MONTHLY_DEPARTMENT_PLANS: FirestoreConstants.COLLECTION_MONTHLY_DEPARTMENT_PLANS,
  DAILY_PLANS: FirestoreConstants.COLLECTION_DAILY_PLANS,
  AUDIT_LOGS: FirestoreConstants.COLLECTION_AUDIT_LOGS,
  CANONICAL_PRODUCTION: FirestoreConstants.COLLECTION_PRODUCTION_CANONICAL,
  CANONICAL_REJECTIONS: FirestoreConstants.COLLECTION_REJECTIONS_CANONICAL,
  CANONICAL_STOCK: FirestoreConstants.COLLECTION_STOCK_CANONICAL
});

/**
 * Authoritative Canonical User Roles & Capabilities
 */
export const UserRole = Object.freeze({
  CEO: "CEO",
  ADMIN: "ADMIN",
  MANAGER: "MANAGER",
  SUPERVISOR: "SUPERVISOR"
});

export const RoleCapabilities = Object.freeze({
  [UserRole.CEO]: {
    canViewExecutiveDashboard: true,
    canViewAllDepartments: true,
    canExportReports: true,
    canWriteProduction: false,
    canWriteStock: false,
    canWriteRejection: false,
    canWritePlans: false,
    canManageActions: true,
    canAcknowledgeAlerts: true,
    canManageUsers: false
  },
  [UserRole.ADMIN]: {
    canViewExecutiveDashboard: true,
    canViewAllDepartments: true,
    canExportReports: true,
    canWriteProduction: true,
    canWriteStock: true,
    canWriteRejection: true,
    canWritePlans: true,
    canManageActions: true,
    canAcknowledgeAlerts: true,
    canManageUsers: true
  },
  [UserRole.MANAGER]: {
    canViewExecutiveDashboard: true,
    canViewAllDepartments: true,
    canExportReports: true,
    canWriteProduction: true,
    canWriteStock: true,
    canWriteRejection: true,
    canWritePlans: true,
    canManageActions: true,
    canAcknowledgeAlerts: true,
    canManageUsers: false
  },
  [UserRole.SUPERVISOR]: {
    canViewExecutiveDashboard: false,
    canViewAllDepartments: false, // strictly assigned department
    canExportReports: false,
    canWriteProduction: true, // within assigned dept
    canWriteStock: true,
    canWriteRejection: true,
    canWritePlans: false,
    canManageActions: false,
    canAcknowledgeAlerts: false,
    canManageUsers: false
  }
});

/**
 * Enforces business invariant: 'ALL' is filter-only and must never be persisted.
 */
export function validateFilterInvariant(record) {
  if (record.model && record.model.trim().toUpperCase() === "ALL") {
    throw new Error("Business Invariant Violation: 'ALL' is filter-only and must never be persisted to Firestore.");
  }
  if (record.department && record.department.trim().toUpperCase() === "ALL") {
    throw new Error("Business Invariant Violation: 'ALL' is filter-only and must never be persisted to Firestore.");
  }
}

/**
 * Web Data-Access Service connecting to Cloud Firestore
 * Maintains synchronized live state caches for all authoritative collections.
 */
export class WebDataAccessService {
  constructor() {
    this.db = db;
    // Reactive in-memory state caches
    this.state = {
      productions: [],
      rejections: [],
      stocks: [],
      dailyPlans: [],
      customerPlans: [],
      monthlyPlans: [],
      alerts: [],
      actions: [],
      isLoaded: false,
      lastError: null
    };
    this.listeners = [];
    this.initSubscriptions();
    this.initPreloadedData();
  }

  initPreloadedData() {
    try {
      if (typeof window !== "undefined" && window.localStorage) {
        const cached = localStorage.getItem("DIVINE_SEPTEMBER_IMPORT_DATA");
        if (cached) {
          const parsed = JSON.parse(cached);
          this.loadImportedSeptemberData(parsed);
        }
      }
    } catch (e) {
      console.warn("Could not load cached September data:", e);
    }

    if (typeof fetch !== "undefined") {
      fetch("./september-data.json")
        .then(res => res.ok ? res.json() : null)
        .then(data => {
          if (data) {
            this.loadImportedSeptemberData(data);
          }
        })
        .catch(() => {});
    }
  }

  loadImportedSeptemberData(data) {
    if (!data) return;
    let updated = false;
    if (Array.isArray(data.productions) && data.productions.length > 0) {
      const prodMap = new Map();
      this.state.productions.forEach(p => prodMap.set(p.id, p));
      data.productions.forEach(p => prodMap.set(p.id, p));
      this.state.productions = Array.from(prodMap.values());
      updated = true;
    }
    if (Array.isArray(data.rejections) && data.rejections.length > 0) {
      const rejMap = new Map();
      this.state.rejections.forEach(r => rejMap.set(r.id, r));
      data.rejections.forEach(r => rejMap.set(r.id, r));
      this.state.rejections = Array.from(rejMap.values());
      updated = true;
    }
    if (updated) {
      this.notifyState();
    }
  }

  subscribeState(fn) {
    this.listeners.push(fn);
    fn(this.state);
    return () => {
      this.listeners = this.listeners.filter(l => l !== fn);
    };
  }

  notifyState() {
    this.listeners.forEach(fn => fn(this.state));
  }

  initSubscriptions() {
    this.subscribeProductionRecords((data) => {
      this.state.productions = data;
      this.state.isLoaded = true;
      this.notifyState();
    });

    this.subscribeRejectionRecords((data) => {
      this.state.rejections = data;
      this.state.isLoaded = true;
      this.notifyState();
    });

    this.subscribeStockRecords((data) => {
      this.state.stocks = data;
      this.state.isLoaded = true;
      this.notifyState();
    });

    this.subscribeDailyPlans((data) => {
      this.state.dailyPlans = data;
      this.notifyState();
    });

    this.subscribeCustomerEndPlans((data) => {
      this.state.customerPlans = data;
      this.notifyState();
    });

    this.subscribeMonthlyDepartmentPlans((data) => {
      this.state.monthlyPlans = data;
      this.notifyState();
    });

    this.subscribeAlerts((data) => {
      this.state.alerts = data;
      this.notifyState();
    });

    this.subscribeActions((data) => {
      this.state.actions = data;
      this.notifyState();
    });
  }

  /**
   * Listen to production shift records in real-time
   */
  subscribeProductionRecords(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.PRODUCTION);
      const q = query(colRef, orderBy("date", "desc"), limit(100));
      return onSnapshot(q, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Production subscription notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Listen to rejection records in real-time
   */
  subscribeRejectionRecords(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.REJECTION);
      const q = query(colRef, orderBy("date", "desc"), limit(100));
      return onSnapshot(q, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Rejection subscription notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Listen to stock records in real-time
   */
  subscribeStockRecords(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.STOCK);
      const q = query(colRef, orderBy("date", "desc"), limit(100));
      return onSnapshot(q, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Stock subscription notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Listen to Daily Plans
   */
  subscribeDailyPlans(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.DAILY_PLANS);
      const q = query(colRef, orderBy("date", "desc"), limit(100));
      return onSnapshot(q, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Daily plans subscription notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Listen to Customer End Plans
   */
  subscribeCustomerEndPlans(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.CUSTOMER_END_PLANS);
      return onSnapshot(colRef, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Customer plans notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Listen to Monthly Department Plans
   */
  subscribeMonthlyDepartmentPlans(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.MONTHLY_DEPARTMENT_PLANS);
      return onSnapshot(colRef, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Monthly plans notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Listen to active alerts in real-time
   */
  subscribeAlerts(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.ALERTS);
      const q = query(colRef, orderBy("timestamp", "desc"), limit(50));
      return onSnapshot(q, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Alerts subscription notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Listen to action records in real-time
   */
  subscribeActions(onData, onError) {
    try {
      const colRef = collection(this.db, Collections.ACTIONS);
      const q = query(colRef, orderBy("createdAt", "desc"), limit(50));
      return onSnapshot(q, (snapshot) => {
        const records = [];
        snapshot.forEach((docSnap) => {
          records.push({ id: docSnap.id, ...docSnap.data() });
        });
        onData(records);
      }, (err) => {
        console.warn("[Firestore] Actions subscription notice:", err);
        if (onError) onError(err);
      });
    } catch (e) {
      if (onError) onError(e);
      return () => {};
    }
  }

  /**
   * Records production entry adhering to canonical constraints:
   * - Model and Department must be canonical.
   * - Model applicability strictly validated (U180 & MAXR restricted to GIL DELIVERY).
   * - Quantity is non-negative and has NO LH/RH concept.
   */
  async recordProduction(entry, userContext) {
    if (!userContext || !RoleCapabilities[userContext.role]?.canWriteProduction) {
      throw new Error(`Permission Denied: Role '${userContext?.role}' cannot write production records.`);
    }

    validateFilterInvariant(entry);
    parseManufacturingModel(entry.model);
    parseDepartment(entry.department);
    ModelApplicabilityValidator.validateApplicability(entry.model, entry.department);

    if (typeof entry.quantity !== "number" || entry.quantity < 0) {
      throw new Error(`Validation Error: Production quantity must be a non-negative number, got: ${entry.quantity}`);
    }

    // Invariant: Production has NO LH/RH concept
    if (entry.side) {
      delete entry.side;
    }

    const docData = {
      ...entry,
      createdAt: serverTimestamp(),
      createdBy: userContext.uid || userContext.email,
      createdRole: userContext.role
    };

    const colRef = collection(this.db, Collections.PRODUCTION);
    const docRef = await addDoc(colRef, docData);

    // Also update local cache optimistically
    this.state.productions.unshift({ id: docRef.id, ...docData, createdAt: new Date() });
    this.notifyState();
    return docRef;
  }

  /**
   * Records rejection entry adhering to canonical constraints:
   * - Model and Department must be canonical.
   * - Model applicability strictly validated.
   * - Rejection side must be LH or RH only (BOTH is strictly invalid).
   * - Rebuffing is eligible for KAMAL rejection source ONLY.
   */
  async recordRejection(rejectionEntry, userContext) {
    if (!userContext || !RoleCapabilities[userContext.role]?.canWriteRejection) {
      throw new Error(`Permission Denied: Role '${userContext?.role}' cannot write rejection records.`);
    }

    validateFilterInvariant(rejectionEntry);
    parseManufacturingModel(rejectionEntry.model);
    parseDepartment(rejectionEntry.department);
    ModelApplicabilityValidator.validateApplicability(rejectionEntry.model, rejectionEntry.department);
    validateDefectName(rejectionEntry.defectName);

    const side = parseRejectionSide(rejectionEntry.side);
    const source = rejectionEntry.source || RejectionSource.DSPL;

    if (rejectionEntry.isRebuffed) {
      RebuffingRule.validateRebuffingEligibility(source);
    }

    if (typeof rejectionEntry.quantity !== "number" || rejectionEntry.quantity < 0) {
      throw new Error(`Validation Error: Rejection quantity must be a non-negative number, got: ${rejectionEntry.quantity}`);
    }

    const docData = {
      ...rejectionEntry,
      side,
      source,
      createdAt: serverTimestamp(),
      loggedBy: userContext.uid || userContext.email
    };

    const colRef = collection(this.db, Collections.REJECTION);
    const docRef = await addDoc(colRef, docData);

    this.state.rejections.unshift({ id: docRef.id, ...docData, createdAt: new Date() });
    this.notifyState();
    return docRef;
  }

  /**
   * Records stock reconciliation ensuring:
   * - Model is canonical ManufacturingModel (ALL is strictly invalid).
   * - Opening and Closing quantities are manually entered non-negative numbers.
   * - Opening stock is NEVER derived from yesterday's closing.
   */
  async recordStockReconciliation(stockEntry, userContext) {
    if (!userContext || !RoleCapabilities[userContext.role]?.canWriteStock) {
      throw new Error(`Permission Denied: Role '${userContext?.role}' cannot record stock.`);
    }

    validateFilterInvariant(stockEntry);
    parseManufacturingModel(stockEntry.model);

    if (typeof stockEntry.openingQuantity !== "number" || stockEntry.openingQuantity < 0) {
      throw new Error("Validation Error: Opening stock quantity must be an explicitly entered non-negative number.");
    }
    if (typeof stockEntry.closingQuantity !== "number" || stockEntry.closingQuantity < 0) {
      throw new Error("Validation Error: Closing stock quantity must be an explicitly entered non-negative number.");
    }

    // Variance = Closing - (Opening + Received - Consumed)
    const expected = stockEntry.openingQuantity + (stockEntry.receivedQuantity || 0) - (stockEntry.consumedQuantity || 0);
    const variance = stockEntry.closingQuantity - expected;

    const docData = {
      ...stockEntry,
      variance,
      createdAt: serverTimestamp(),
      auditedBy: userContext.uid || userContext.email
    };

    const colRef = collection(this.db, Collections.STOCK);
    const docRef = await addDoc(colRef, docData);

    this.state.stocks.unshift({ id: docRef.id, ...docData, createdAt: new Date() });
    this.notifyState();
    return docRef;
  }

  /**
   * Records daily plan
   */
  async recordDailyPlan(planEntry, userContext) {
    if (!userContext || !RoleCapabilities[userContext.role]?.canWritePlans) {
      throw new Error(`Permission Denied: Role '${userContext?.role}' cannot create plans.`);
    }
    validateFilterInvariant(planEntry);
    parseManufacturingModel(planEntry.model);
    parseDepartment(planEntry.department);
    ModelApplicabilityValidator.validateApplicability(planEntry.model, planEntry.department);

    const docData = {
      ...planEntry,
      createdAt: serverTimestamp(),
      createdBy: userContext.uid || userContext.email
    };

    const colRef = collection(this.db, Collections.DAILY_PLANS);
    const docRef = await addDoc(colRef, docData);
    this.state.dailyPlans.unshift({ id: docRef.id, ...docData });
    this.notifyState();
    return docRef;
  }

  /**
   * Action Tracker: Create action record
   */
  async createAction(actionEntry, userContext) {
    if (!userContext || !RoleCapabilities[userContext.role]?.canManageActions) {
      throw new Error(`Permission Denied: Role '${userContext?.role}' cannot manage actions.`);
    }

    const docData = {
      ...actionEntry,
      createdAt: serverTimestamp(),
      createdBy: userContext.uid || userContext.email,
      status: actionEntry.status || "OPEN"
    };

    const colRef = collection(this.db, Collections.ACTIONS);
    const docRef = await addDoc(colRef, docData);
    this.state.actions.unshift({ id: docRef.id, ...docData, createdAt: new Date() });
    this.notifyState();
    return docRef;
  }

  /**
   * Action Tracker: Update action status
   */
  async updateActionStatus(actionId, newStatus, userContext) {
    if (!userContext || !RoleCapabilities[userContext.role]?.canManageActions) {
      throw new Error(`Permission Denied: Role '${userContext?.role}' cannot update actions.`);
    }
    const docRef = doc(this.db, Collections.ACTIONS, actionId);
    await updateDoc(docRef, {
      status: newStatus,
      updatedAt: serverTimestamp(),
      updatedBy: userContext.uid || userContext.email
    });

    const item = this.state.actions.find(a => a.id === actionId);
    if (item) {
      item.status = newStatus;
      this.notifyState();
    }
  }

  /**
   * Alerts: Acknowledge/resolve alert
   */
  async resolveAlert(alertId, userContext) {
    if (!userContext || !RoleCapabilities[userContext.role]?.canAcknowledgeAlerts) {
      throw new Error(`Permission Denied: Role '${userContext?.role}' cannot resolve alerts.`);
    }
    const docRef = doc(this.db, Collections.ALERTS, alertId);
    await updateDoc(docRef, {
      status: "RESOLVED",
      resolvedAt: serverTimestamp(),
      resolvedBy: userContext.uid || userContext.email
    });

    const item = this.state.alerts.find(a => a.id === alertId);
    if (item) {
      item.status = "RESOLVED";
      this.notifyState();
    }
  }

  /**
   * Retrieves user profile from Firestore 'users' collection
   */
  async getUserProfile(uid) {
    try {
      const userDocRef = doc(this.db, Collections.USERS, uid);
      const snapshot = await getDoc(userDocRef);
      if (snapshot.exists()) {
        return { uid: snapshot.id, ...snapshot.data() };
      }
      return null;
    } catch (e) {
      console.warn("[Firestore] User profile notice:", e);
      return null;
    }
  }
}

export const webDataAccess = new WebDataAccessService();
