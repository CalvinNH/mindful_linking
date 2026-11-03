package com.mindfullinking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budget_state WHERE id = 1 LIMIT 1")
    suspend fun getBudgetState(): BudgetStateEntity?

    @Query("SELECT * FROM budget_state WHERE id = 1 LIMIT 1")
    fun getBudgetStateFlow(): Flow<BudgetStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgetState(budgetState: BudgetStateEntity)

    @Query("DELETE FROM budget_state")
    suspend fun resetBudgetState()
}
