package com.example.repository.firestore

/**
 * Authoritative Firestore Collection & Field Constants for Divine Stamp Manufacturing MIS.
 *
 * Ensures all future Firebase Cloud Firestore operations adhere to the single source of truth.
 */
object FirestoreConstants {
    // Top-Level Collections
    const val COLLECTION_PRODUCTION = "production_records"
    const val COLLECTION_REJECTION = "rejection_records"
    const val COLLECTION_STOCK = "stock_records"
    const val COLLECTION_PLANS = "plan_records"
    const val COLLECTION_USERS = "users"
    const val COLLECTION_ACTIONS = "action_records"
    const val COLLECTION_ALERTS = "alert_records"

    // Authoritative Canonical Collections & Contracts
    const val COLLECTION_MODELS = "models"
    const val COLLECTION_DEPARTMENTS = "departments"
    const val COLLECTION_ACTION_TRACKER = "actionTracker"
    const val COLLECTION_CUSTOMER_END_PLANS = "customerEndPlans"
    const val COLLECTION_MONTHLY_DEPARTMENT_PLANS = "monthlyDepartmentPlans"
    const val COLLECTION_DAILY_PLANS = "dailyPlans"
    const val COLLECTION_AUDIT_LOGS = "auditLogs"
    const val COLLECTION_PRODUCTION_CANONICAL = "production"
    const val COLLECTION_REJECTIONS_CANONICAL = "rejections"
    const val COLLECTION_STOCK_CANONICAL = "stock"

    // Common Field Names
    const val FIELD_ID = "id"
    const val FIELD_DATE = "date" // Stored in canonical YYYY-MM-DD
    const val FIELD_MODEL = "model" // Stored as model code (e.g. "U86", NEVER "ALL")
    const val FIELD_DEPARTMENT = "department" // Stored as department code (e.g. "CASTING")
    const val FIELD_QUANTITY = "quantity"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_NOTES = "notes"

    // Rejection Specific Fields
    const val FIELD_DEFECT_NAME = "defectName"
    const val FIELD_SOURCE = "source" // KAMAL, MRN, DSPL
    const val FIELD_BUSINESS_AREA = "businessArea" // KAMAL, GABRIEL, DSPL
    const val FIELD_SIDE = "side" // LH, RH (NEVER "BOTH")
    const val FIELD_IS_REBUFFED = "isRebuffed" // true only if source is KAMAL

    // Stock Specific Fields
    const val FIELD_OPENING_QUANTITY = "openingQuantity"
    const val FIELD_CLOSING_QUANTITY = "closingQuantity"

    // Planning Specific Fields
    const val FIELD_PLANNED_QUANTITY = "plannedQuantity"
}
