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

package com.matthewsuozzo.f2fauth.repository

import androidx.lifecycle.LiveData
import com.matthewsuozzo.f2fauth.AppExecutors
import com.matthewsuozzo.f2fauth.api.ApiResponse
import com.matthewsuozzo.f2fauth.db.DeviceDao
import com.matthewsuozzo.f2fauth.db.ProfileDao
import com.matthewsuozzo.f2fauth.testing.OpenForTesting
import com.matthewsuozzo.f2fauth.util.AbsentLiveData
import com.matthewsuozzo.f2fauth.vo.Device
import com.matthewsuozzo.f2fauth.vo.Resource
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Repository that handles User objects.
 */
@OpenForTesting
@Singleton
class DeviceRepository @Inject constructor(
    private val appExecutors: AppExecutors,
     val deviceDao: DeviceDao,
     val profileDao: ProfileDao
) {

    fun loadDevice(name: String): LiveData<Resource<Device>> {
        return object : NetworkBoundResource<Device, Device>(appExecutors) {
            override fun saveCallResult(item: Device) {
                deviceDao.insert(item)
            }

            override fun shouldFetch(data: Device?) = false

            override fun loadFromDb() = deviceDao.findByName(name)

            override fun createCall() = AbsentLiveData.create<ApiResponse<Device>>()
        }.asLiveData()
    }

    fun loadDefault(): LiveData<Resource<Device>> {
        return object : NetworkBoundResource<Device, Device>(appExecutors) {
            override fun saveCallResult(item: Device) {
                deviceDao.insert(item)
            }

            override fun shouldFetch(data: Device?) = false

            override fun loadFromDb() = deviceDao.getDefault()

            override fun createCall() = AbsentLiveData.create<ApiResponse<Device>>()
        }.asLiveData()
    }
}
