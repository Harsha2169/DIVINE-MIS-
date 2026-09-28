// Authoritative Manufacturing Contracts for Divine Stamp Manufacturing MIS Web
// Mirrors com.example.model and com.example.repository.firestore exactly.

/**
 * Authoritative Canonical Manufacturing Models
 * Exactly 7 models. ALL is strictly filter-only and never stored.
 */
export const ManufacturingModel = Object.freeze({
  U86: { code: "U86", displayName: "U86" },
  U180: { code: "U180", displayName: "U180" },
  U244: { code: "U244", displayName: "U244" },
  MAXR: { code: "MAXR", displayName: "MAXR" },
  N282_DISC: { code: "N282-DISC", displayName: "N282-DISC" },
  DRUM: { code: "DRUM", displayName: "DRUM" },
  N360: { code: "N360", displayName: "N360" }
});

export const ALL_MODELS = Object.values(ManufacturingModel);

export function parseManufacturingModel(code) {
  if (!code) throw new Error("Model code cannot be empty");
  const trimmed = code.trim();
  if (trimmed.toUpperCase() === "ALL") {
    throw new Error("Business Invariant Violation: 'ALL' is filter-only and cannot be resolved as a ManufacturingModel entity.");
  }
  const match = ALL_MODELS.find(m => m.code.toUpperCase() === trimmed.toUpperCase());
  if (!match) {
    throw new Error(`Unknown model: '${code}'. Valid canonical models are: ${ALL_MODELS.map(m => m.code).join(", ")}`);
  }
  return match;
}

/**
 * Authoritative Canonical Department Master
 * Exact Sequence:
 * 1. CASTING
 * 2. POST CASTING
 * 3. M/C (Machining)
 * 4. BUFFING
 * 5. FINAL
 * 6. KAMAL OK
 * 7. GIL DELIVERY
 */
export const Department = Object.freeze({
  CASTING: { code: "CASTING", displayName: "CASTING", sequenceNumber: 1 },
  POST_CASTING: { code: "POST_CASTING", displayName: "POST CASTING", sequenceNumber: 2 },
  MACHINING: { code: "M_C", displayName: "M/C", sequenceNumber: 3 },
  BUFFING: { code: "BUFFING", displayName: "BUFFING", sequenceNumber: 4 },
  FINAL: { code: "FINAL", displayName: "FINAL", sequenceNumber: 5 },
  KAMAL_OK: { code: "KAMAL_OK", displayName: "KAMAL OK", sequenceNumber: 6 },
  GIL_DELIVERY: { code: "GIL_DELIVERY", displayName: "GIL DELIVERY", sequenceNumber: 7 }
});

export const CANONICAL_DEPARTMENTS = Object.values(Department).sort((a, b) => a.sequenceNumber - b.sequenceNumber);

export function parseDepartment(name) {
  if (!name) throw new Error("Department name cannot be empty");
  const trimmed = name.trim();
  if (trimmed.toUpperCase() === "ALL") {
    throw new Error("Business Invariant Violation: 'ALL' is filter-only and cannot be resolved as a Department entity.");
  }
  const match = CANONICAL_DEPARTMENTS.find(d => 
    d.displayName.toUpperCase() === trimmed.toUpperCase() || 
    d.code.toUpperCase() === trimmed.toUpperCase()
  );
  if (!match) {
    throw new Error(`Unknown department: '${name}'. Canonical departments are: ${CANONICAL_DEPARTMENTS.map(d => d.displayName).join(", ")}`);
  }
  return match;
}

/**
 * Authoritative Canonical Manufacturing Flow definition
 */
export const ManufacturingFlow = Object.freeze({
  STEPS: CANONICAL_DEPARTMENTS,
  getSequence: () => CANONICAL_DEPARTMENTS,
  totalSteps: () => CANONICAL_DEPARTMENTS.length,
  getStepIndex: (dept) => CANONICAL_DEPARTMENTS.findIndex(d => d.code === dept.code),
  isSequentialTransition: (fromDept, toDept) => {
    const fromIdx = CANONICAL_DEPARTMENTS.findIndex(d => d.code === fromDept.code);
    const toIdx = CANONICAL_DEPARTMENTS.findIndex(d => d.code === toDept.code);
    return fromIdx !== -1 && toIdx === fromIdx + 1;
  }
});

/**
 * Model Applicability Invariants:
 * - U180 is applicable to GIL DELIVERY ONLY.
 * - MAXR is applicable to GIL DELIVERY ONLY.
 * - Other models apply to all departments.
 */
export const ModelApplicabilityValidator = Object.freeze({
  isApplicable(modelCode, departmentCode) {
    const normalizedModel = (modelCode || "").trim().toUpperCase();
    const normalizedDept = (departmentCode || "").trim().toUpperCase();

    if (normalizedModel === "U180" || normalizedModel === "MAXR") {
      return normalizedDept === "GIL_DELIVERY" || normalizedDept === "GIL DELIVERY";
    }
    return true;
  },

  validateApplicability(modelCode, departmentCode) {
    if (!this.isApplicable(modelCode, departmentCode)) {
      throw new Error(
        `Business Invariant Violation: Model '${modelCode}' is restricted to GIL DELIVERY only, and cannot be used in department '${departmentCode}'.`
      );
    }
  },

  applicableDepartmentsFor(modelCode) {
    return CANONICAL_DEPARTMENTS.filter(d => this.isApplicable(modelCode, d.code));
  }
});

/**
 * Authoritative Rejection Master:
 * - Sources: KAMAL, MRN, DSPL
 * - Business Areas: KAMAL, GABRIEL, DSPL
 * - Rejection Sides: LH, RH (BOTH is strictly invalid)
 * - Rebuffing: KAMAL rejection source ONLY
 */
export const RejectionSource = Object.freeze({
  KAMAL: "KAMAL",
  MRN: "MRN",
  DSPL: "DSPL"
});

export const BusinessArea = Object.freeze({
  KAMAL: "KAMAL",
  GABRIEL: "GABRIEL",
  DSPL: "DSPL"
});

export const RejectionSide = Object.freeze({
  LH: "LH",
  RH: "RH"
});

export function parseRejectionSide(side) {
  if (!side) throw new Error("Rejection side cannot be empty");
  const trimmed = side.trim().toUpperCase();
  if (trimmed === "BOTH") {
    throw new Error("Business Invariant Violation: Rejection side 'BOTH' is strictly invalid. Only LH or RH is permitted.");
  }
  if (trimmed !== "LH" && trimmed !== "RH") {
    throw new Error(`Invalid rejection side: '${side}'. Must be LH or RH.`);
  }
  return trimmed;
}

export const RebuffingRule = Object.freeze({
  canBeRebuffed: (source) => source === RejectionSource.KAMAL,
  validateRebuffingEligibility: (source) => {
    if (source !== RejectionSource.KAMAL) {
      throw new Error(`Business Invariant Violation: Rebuffing is allowed for KAMAL rejection ONLY. Source '${source}' cannot be rebuffed.`);
    }
  }
});

/**
 * Authoritative Standard Defect Categories (DefectMaster)
 */
export const STANDARD_DEFECTS = Object.freeze([
  "BLOWHOLE",
  "CRACK",
  "DENT",
  "PIN HOLE",
  "POROSITY",
  "SLAG INCLUSION",
  "SHRINKAGE",
  "UNDER FILL",
  "MACHINING DAMAGE",
  "ROUGH BUFFING",
  "BEND",
  "OTHER"
]);

export function validateDefectName(name) {
  if (!name || name.trim().length < 2) {
    throw new Error(`Defect name must be at least 2 characters long: '${name}'`);
  }
}

/**
 * Authoritative Firestore Constants (FirestoreConstants.kt)
 */
export const FirestoreConstants = Object.freeze({
  // Collections
  COLLECTION_PRODUCTION: "production_records",
  COLLECTION_REJECTION: "rejection_records",
  COLLECTION_STOCK: "stock_records",
  COLLECTION_PLANS: "plan_records",
  COLLECTION_USERS: "users",
  COLLECTION_ACTIONS: "action_records",
  COLLECTION_ALERTS: "alert_records",

  // Canonical Collections & Contracts
  COLLECTION_MODELS: "models",
  COLLECTION_DEPARTMENTS: "departments",
  COLLECTION_ACTION_TRACKER: "actionTracker",
  COLLECTION_CUSTOMER_END_PLANS: "customerEndPlans",
  COLLECTION_MONTHLY_DEPARTMENT_PLANS: "monthlyDepartmentPlans",
  COLLECTION_DAILY_PLANS: "dailyPlans",
  COLLECTION_AUDIT_LOGS: "auditLogs",
  COLLECTION_PRODUCTION_CANONICAL: "production",
  COLLECTION_REJECTIONS_CANONICAL: "rejections",
  COLLECTION_STOCK_CANONICAL: "stock",

  // Fields
  FIELD_ID: "id",
  FIELD_DATE: "date",
  FIELD_MODEL: "model",
  FIELD_DEPARTMENT: "department",
  FIELD_QUANTITY: "quantity",
  FIELD_CREATED_AT: "createdAt",
  FIELD_NOTES: "notes",
  FIELD_DEFECT_NAME: "defectName",
  FIELD_SOURCE: "source",
  FIELD_BUSINESS_AREA: "businessArea",
  FIELD_SIDE: "side",
  FIELD_IS_REBUFFED: "isRebuffed",
  FIELD_OPENING_QUANTITY: "openingQuantity",
  FIELD_CLOSING_QUANTITY: "closingQuantity",
  FIELD_PLANNED_QUANTITY: "plannedQuantity"
});

/**
 * Metric Representation distinguishing '0' from 'NO DATA'
 */
export const MetricValue = Object.freeze({
  NO_DATA: "NO_DATA",
  format(val, unit = "") {
    if (val === null || val === undefined || val === "NO_DATA") {
      return "NO DATA";
    }
    if (typeof val === "number") {
      return `${val.toLocaleString()}${unit ? " " + unit : ""}`;
    }
    return `${val}${unit ? " " + unit : ""}`;
  }
});

/**
 * Authoritative Canonical Collections
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

