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

package com.matthewsuozzo.f2fauth.api

import android.content.res.Resources
import com.matthewsuozzo.f2fauth.R
import com.matthewsuozzo.f2fauth.util.LiveDataCallAdapterFactory
import com.matthewsuozzo.f2fauth.vo.Device
import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.time.ZonedDateTime

/*
@Entity(primaryKeys = ["login"])
data class Empty(
@field:SerializedName("login")
val login: String
)

*/

/**
 * REST API access points
 */
interface AuthService {
    @GET("ack")
    fun ack(): Call<ResponseBody>

    @POST("provision")
    fun provision(): Call<Device>

    @POST("provision/finalize")
    fun finalizeProvision(@Query("name") name: String, @Query("pk") pk: String): Call<Device>

    @GET("devices")
    fun getDevice(@Query("name") name: String): Call<Device>

//    @GET("test")
//    fun test(@Query("pk") pk: String, @Query("text") txt: String, @Query("sig") sig: String): Call<ResponseBody>

    @GET("refresh")
    fun refreshToken(
            @Query("name") name: String,
            @Query("ts") ts: String,
            @Query("sig") sig: String,
            @Query("peerName") peerName: String,
            @Query("peerTs") peerTs: String,
            @Query("peerSig") peerSig: String
    ): Call<ResponseBody>

    @GET("test")
    fun test(@Header("Authentication") token: String): Call<ResponseBody>
}

class AuthServiceFactory constructor(val url: String, var resources: Resources?, var client: OkHttpClient?) {
    var service: AuthService
    init {
        if (client == null) {
            client = OkHttpClient.Builder()
                    .sslSocketFactory(TlsHelper().getSSLConfig(resources!!.openRawResource(R.raw.tls_cert)).socketFactory)
                    .build()
        }
        val gson = GsonBuilder().registerTypeAdapter(ZonedDateTime::class.java, JsonDeserializer<ZonedDateTime> {
            json, typeOfT, context -> ZonedDateTime.parse(json.asJsonPrimitive.asString)
        }).create()
        service = Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(LiveDataCallAdapterFactory())
                .client(client)
                .build()
                .create(AuthService::class.java)
    }
}
