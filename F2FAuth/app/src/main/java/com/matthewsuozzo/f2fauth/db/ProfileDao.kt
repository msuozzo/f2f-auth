package com.matthewsuozzo.f2fauth.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matthewsuozzo.f2fauth.vo.Profile

/**
 * Interface for database access for User related operations.
 */
@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(profile: Profile)

    @Query("SELECT * FROM profile WHERE realm = :realm")
    fun findByRealm(realm: String): LiveData<Profile>

    @Query("SELECT * FROM profile LIMIT 1")
    fun getDefault(): LiveData<Profile>

    @Query("SELECT * FROM profile LIMIT 1")
    fun immediateGetDefault(): Profile
}
