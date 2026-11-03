package com.mindfullinking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {

    @Query("SELECT * FROM links WHERE canonical_url = :canonicalUrl ORDER BY saved_at_epoch_ms DESC LIMIT 1")
    suspend fun findLatestByCanonicalUrl(canonicalUrl: String): LinkEntity?

    @Query("SELECT * FROM links WHERE id = :id LIMIT 1")
    suspend fun getLinkById(id: String): LinkEntity?

    @Query("SELECT * FROM links WHERE id = :id LIMIT 1")
    fun getLinkByIdFlow(id: String): Flow<LinkEntity?>

    @Query("SELECT * FROM links WHERE status = 'ACTIVE'")
    fun getActiveLinksFlow(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE status = 'ACTIVE'")
    suspend fun getActiveLinks(): List<LinkEntity>

    @Query("SELECT * FROM links WHERE status = 'ARCHIVED'")
    fun getArchivedLinksFlow(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE status = 'ARCHIVED'")
    suspend fun getArchivedLinks(): List<LinkEntity>

    @Query("SELECT COUNT(*) FROM links WHERE status = 'ARCHIVED'")
    suspend fun getArchivedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(link: LinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinks(links: List<LinkEntity>)

    @Query("DELETE FROM links WHERE id = :linkId")
    suspend fun deleteLinkPermanently(linkId: String)

    @Query("DELETE FROM links")
    suspend fun deleteAllLinks()

    @Query("SELECT * FROM links")
    fun getAllLinksFlow(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links")
    suspend fun getAllLinks(): List<LinkEntity>
}
