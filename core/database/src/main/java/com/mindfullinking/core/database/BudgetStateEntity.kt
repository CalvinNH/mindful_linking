package com.mindfullinking.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_state")
data class BudgetStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "standard_budget_cents") val standardBudgetCents: Int = 10000,
    @ColumnInfo(name = "temporary_overage_cents") val temporaryOverageCents: Int = 0,
    @ColumnInfo(name = "temporary_overage_expires_at_epoch_ms") val temporaryOverageExpiresAtEpochMs: Long?,
    @ColumnInfo(name = "last_overage_reminder_at_epoch_ms") val lastOverageReminderAtEpochMs: Long?
)
