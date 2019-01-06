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

package com.matthewsuozzo.f2fauth.vo

import androidx.room.Entity
import com.google.gson.annotations.SerializedName

@Entity(primaryKeys = ["realm"])
data class Profile(
    val realm: String,
    val serverUrl: String,
    val deviceName: String?
)

@Entity(primaryKeys = ["name"])
data class Device(
    @field:SerializedName("Name")
    val name: String,
    @field:SerializedName("Realm")
    val realm: String,
//    @field:SerializedName("ID")
//    val id: String,
//    @field:SerializedName("CreatedAt")
//    val createdAt: ZonedDateTime?,
//    @field:SerializedName("UpdatedAt")
//    val updatedAt: ZonedDateTime?,
//    @field:SerializedName("DeletedAt")
//    val deletedAt: ZonedDateTime?,
    @field:SerializedName("PublicKey")
    val publicKey: String?,
    @field:SerializedName("PublicKeyFingerprint")
    val publicKeyFingerprint: String?,
    val initialized: Boolean = false
)
