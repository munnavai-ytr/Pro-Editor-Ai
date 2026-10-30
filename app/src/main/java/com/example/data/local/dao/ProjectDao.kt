package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
  @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
  fun observeAllProjects(): Flow<List<ProjectEntity>>

  @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
  fun observeProjectById(projectId: Long): Flow<ProjectEntity?>

  @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
  suspend fun getProjectById(projectId: Long): ProjectEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProject(project: ProjectEntity): Long

  @Update
  suspend fun updateProject(project: ProjectEntity)

  @Delete
  suspend fun deleteProject(project: ProjectEntity)

  @Query("DELETE FROM projects WHERE id = :projectId")
  suspend fun deleteProjectById(projectId: Long)
}
