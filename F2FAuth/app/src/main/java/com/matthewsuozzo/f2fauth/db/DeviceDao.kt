/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.matthewsuozzo.f2fauth.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matthewsuozzo.f2fauth.vo.Device

/**
 * Interface for database access for User related operations.
 */
@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(device: Device)

    @Query("SELECT * FROM device WHERE name = :name")
    fun immediateFindByName(name: String): Device

    @Query("SELECT * FROM device WHERE name = :name")
    fun findByName(name: String): LiveData<Device>

    @Query("SELECT * FROM device WHERE initialized = 1 LIMIT 1")
    fun immediateGetDefault(): Device

    @Query("SELECT * FROM device WHERE initialized = 1 LIMIT 1")
    fun getDefault(): LiveData<Device>

    @Query("DELETE FROM device")
    fun clear()
}