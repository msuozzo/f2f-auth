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

package com.matthewsuozzo.f2fauth.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.matthewsuozzo.f2fauth.repository.DeviceRepository
import com.matthewsuozzo.f2fauth.repository.ProfileRepository
import com.matthewsuozzo.f2fauth.testing.OpenForTesting
import com.matthewsuozzo.f2fauth.vo.*
import java.util.Locale
import javax.inject.Inject

@OpenForTesting
class SearchViewModel @Inject constructor(
        private val deviceRepository: DeviceRepository,
        private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _query = MutableLiveData<String>()
    private val _device = MutableLiveData<Resource<Device>>()
    private val _profile = MutableLiveData<Resource<Profile>>()
    private val _peerDevice = MutableLiveData<Resource<Device>>()
    private val _peerProfile = MutableLiveData<Resource<Profile>>()

    val query : LiveData<String> = _query

    val device: LiveData<Resource<Device>> = _device
    val profile: LiveData<Resource<Profile>> = _profile
//    deviceRepository.loadDefault()
//    profileRepository.loadDefault()
    val peerDevice: LiveData<Resource<Device>> = _peerDevice
    val peerProfile: LiveData<Resource<Profile>> = _peerProfile

    fun setQuery(originalInput: String) {
        val input = originalInput.toLowerCase(Locale.getDefault()).trim()
        if (input == _query.value) {
            return
        }
        _query.value = input
    }

    fun setDevice(d: Resource<Device>) {
        _device.value = d
    }

    fun clearDevice() {
        _device.value = null
    }

    fun setProfile(d: Resource<Profile>) {
        _profile.value = d
    }

    fun setPeerDevice(d: Resource<Device>) {
        _peerDevice.value = d
    }

    fun clearPeerDevice() {
        _peerDevice.value = null
    }

    fun setPeerProfile(d: Resource<Profile>) {
        _peerProfile.value = d
    }

    fun clearPeerProfile() {
        _peerProfile.value = null
    }
}
