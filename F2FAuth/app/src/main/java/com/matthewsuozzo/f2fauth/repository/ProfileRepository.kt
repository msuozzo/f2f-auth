package com.matthewsuozzo.f2fauth.repository

import androidx.lifecycle.LiveData
import com.matthewsuozzo.f2fauth.AppExecutors
import com.matthewsuozzo.f2fauth.api.ApiResponse
import com.matthewsuozzo.f2fauth.db.ProfileDao
import com.matthewsuozzo.f2fauth.testing.OpenForTesting
import com.matthewsuozzo.f2fauth.util.AbsentLiveData
import com.matthewsuozzo.f2fauth.vo.Profile
import com.matthewsuozzo.f2fauth.vo.Resource
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class ProfileRepository @Inject constructor(
        private val appExecutors: AppExecutors,
        private val profileDao: ProfileDao
) {
    fun loadProfile(realm: String): LiveData<Resource<Profile>> {
        return object : NetworkBoundResource<Profile, Profile>(appExecutors) {
            override fun saveCallResult(item: Profile) {
                profileDao.insert(item)
            }

            override fun shouldFetch(data: Profile?) = false

            override fun loadFromDb() = profileDao.findByRealm(realm)

            override fun createCall() = AbsentLiveData.create<ApiResponse<Profile>>()
        }.asLiveData()
    }

    fun loadDefault(): LiveData<Resource<Profile>> {
//        return profileDao.getDefault()
        return object : NetworkBoundResource<Profile, Profile>(appExecutors) {
            override fun saveCallResult(item: Profile) {
                profileDao.insert(item)
            }

            override fun shouldFetch(data: Profile?) = false

            override fun loadFromDb() = profileDao.getDefault()

            override fun createCall() = AbsentLiveData.create<ApiResponse<Profile>>()
        }.asLiveData()
    }

    fun insert(profile: Profile) {
        profileDao.insert(profile)
    }
}
