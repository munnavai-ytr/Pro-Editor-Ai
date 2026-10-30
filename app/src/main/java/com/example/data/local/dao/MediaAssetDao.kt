package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.MediaAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAssetDao {
  @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY id ASC")
  fun observeAssetsForProject(projectId: Long): Flow<List<MediaAssetEntity>>

  @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY id ASC")
  suspend fun getAssetsForProject(projectId: Long): List<MediaAssetEntity>

  @Query("SELECT * FROM media_assets WHERE id = :assetId LIMIT 1")
  suspend fun getAssetById(assetId: Long): MediaAssetEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAsset(asset: MediaAssetEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAssets(assets: List<MediaAssetEntity>): List<Long>
}
