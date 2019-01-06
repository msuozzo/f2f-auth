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

import android.content.BroadcastReceiver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.matthewsuozzo.f2fauth.AppExecutors
import com.matthewsuozzo.f2fauth.R
import com.matthewsuozzo.f2fauth.api.*
import com.matthewsuozzo.f2fauth.binding.FragmentDataBindingComponent
import com.matthewsuozzo.f2fauth.databinding.SearchFragmentBinding
import com.matthewsuozzo.f2fauth.db.DeviceDao
import com.matthewsuozzo.f2fauth.db.ProfileDao
import com.matthewsuozzo.f2fauth.di.Injectable
import com.matthewsuozzo.f2fauth.repository.DeviceRepository
import com.matthewsuozzo.f2fauth.repository.ProfileRepository
import com.matthewsuozzo.f2fauth.testing.OpenForTesting
import com.matthewsuozzo.f2fauth.util.KeyStoreHelper
import com.matthewsuozzo.f2fauth.util.NfcHelper
import com.matthewsuozzo.f2fauth.util.autoCleared
import com.matthewsuozzo.f2fauth.vo.Device
import com.matthewsuozzo.f2fauth.vo.Profile
import com.matthewsuozzo.f2fauth.vo.Resource
import com.matthewsuozzo.f2fauth.vo.Status
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.experimental.and
import kotlin.math.max


@OpenForTesting
class SearchFragment : Fragment(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var appExecutors: AppExecutors

    @Inject
    lateinit var deviceRepository: DeviceRepository
    @Inject
    lateinit var deviceDao: DeviceDao

    @Inject
    lateinit var profileRepository: ProfileRepository
    @Inject
    lateinit var profileDao: ProfileDao

    var dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent(this)

    var binding by autoCleared<SearchFragmentBinding>()

    lateinit var searchViewModel: SearchViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.search_fragment,
            container,
            false,
            dataBindingComponent
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        searchViewModel = ViewModelProviders.of(this, viewModelFactory)
            .get(SearchViewModel::class.java)
        binding.setLifecycleOwner(viewLifecycleOwner)

        binding.device = searchViewModel.device
        binding.profile = searchViewModel.profile
        appExecutors.diskIO().execute {
            val d = Resource.success(deviceDao.immediateGetDefault())
            val p = Resource.success(profileDao.immediateGetDefault())
            activity?.runOnUiThread {
                searchViewModel.setDevice(d)
                searchViewModel.setProfile(p)
            }
        }
        binding.peerDevice = searchViewModel.peerDevice
        binding.peerProfile = searchViewModel.peerProfile

        binding.homeButton.setOnClickListener {
            navController().navigate(SearchFragmentDirections.showHome())
        }

        binding.deleteButton.setOnClickListener {
            appExecutors.diskIO().execute {
                deviceDao.clear()
            }
            searchViewModel.clearDevice()
            binding.header.visibility = View.INVISIBLE
            binding.authButton.visibility = View.INVISIBLE
            binding.deleteButton.visibility = View.INVISIBLE
        }

        binding.authButton.setOnClickListener {
            requestAuth()
        }

        binding.acceptButton.setOnClickListener {
            doAuth()
        }

        binding.rejectButton.setOnClickListener {
            searchViewModel.clearPeerDevice()
        }

        binding.testButton.setOnClickListener {
            if (token.isEmpty()) {
                Toast.makeText(context!!, "No Authentication Token", LENGTH_SHORT).show()
            } else{
                var service = AuthServiceFactory(searchViewModel.profile.value!!.data!!.serverUrl, resources, null).service
                appExecutors.networkIO().execute {
                    val newValue: Resource<String> = try {
                        val resp = service.test(token).execute()
                        val apiResponse = ApiResponse.create(resp)
                        Log.d("Resource", resp.toString())
                        when (apiResponse) {
                            is ApiSuccessResponse -> Resource.success(apiResponse.body.string())
                            is ApiEmptyResponse -> Resource.error("Empty Response", null)
                            is ApiErrorResponse -> Resource.error(apiResponse.errorMessage, null)
                            null -> Resource.error("Failed to fetch", null)
                        }
                    } catch (e: IOException) {
                        Resource.error(e.message!!, null)
                    }
                    Log.d("Resource", newValue.toString())
                    activity?.runOnUiThread {
                        if (newValue.status == Status.SUCCESS) {
                            Toast.makeText(context!!, "SUCCESS!!!", LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context!!, "FAILED!!!", LENGTH_LONG).show()
                        }
                        // TODO
                        // searchViewModel.setPeerDevice(newValue)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IntentFilter()
        LocalBroadcastManager.getInstance(context!!).registerReceiver(mMessageReceiver,
                IntentFilter().apply {
                    addAction("com.matthewsuozzo.f2fauth.provision1")
                    addAction("com.matthewsuozzo.f2fauth.provision3")
                    addAction("com.matthewsuozzo.f2fauth.auth1")
                    addAction("com.matthewsuozzo.f2fauth.auth2")
                })

    }
    override fun onDestroy() {
        LocalBroadcastManager.getInstance(context!!).unregisterReceiver(mMessageReceiver)
        super.onDestroy()
    }

    private val mMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when {
                intent.action == "com.matthewsuozzo.f2fauth.provision1" -> this@SearchFragment.initialize(
                        intent.getStringExtra("url"),
                        intent.getStringExtra("realm"),
                        intent.getStringExtra("name")
                )
                intent.action == "com.matthewsuozzo.f2fauth.provision3" -> this@SearchFragment.finalize(
                        intent.getStringExtra("name"))
                intent.action == "com.matthewsuozzo.f2fauth.auth1" -> this@SearchFragment.handleAuthRequest(
                        intent.getStringExtra("peerName"),
                        intent.getStringExtra("peerTs"),
                        intent.getStringExtra("peerSig")
                )
                intent.action == "com.matthewsuozzo.f2fauth.auth2" -> this@SearchFragment.relayAuth(
                        intent.getStringExtra("name"),
                        intent.getStringExtra("ts"),
                        intent.getStringExtra("sig"),
                        intent.getStringExtra("peerName"),
                        intent.getStringExtra("peerTs"),
                        intent.getStringExtra("peerSig")
                )
            }
        }
    }

    private val hexArray = "0123456789abcdef".toCharArray()
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j] and 0xFF.toByte()
            hexChars[j * 2] = hexArray[v.div(0b10000) and 0x0F]
            hexChars[j * 2 + 1] = hexArray[(v and 0x0F).toInt()]
        }
        return String(hexChars)
    }

    fun initialize(url: String, realm: String, name: String) {
        Log.d("FOOOOO", "$url|$realm|$name")
        appExecutors.diskIO().execute {
            val newProf = Profile(realm, url, name)
            profileRepository.insert(newProf)
            activity?.runOnUiThread {
                searchViewModel.setProfile(Resource.success(newProf))
            }
            val keypair = KeyStoreHelper(name).createKeyPair()
            Log.d("PUBKEY", Base64.encodeToString(keypair?.public?.encoded!!, Base64.URL_SAFE))
            if (keypair != null) {
                val b64pub = Base64.encodeToString(keypair.public.encoded, Base64.URL_SAFE)
                val fingerprint = bytesToHex(MessageDigest.getInstance("SHA256").digest(keypair.public.encoded))
                Log.d("PUBKEY_FINGERPRINT", fingerprint)
                Log.d("NAME", name)
                val newDev = Device(name = name, realm = realm, publicKey = b64pub, publicKeyFingerprint = fingerprint)
                deviceDao.insert(newDev)
                activity?.runOnUiThread {
                    searchViewModel.setDevice(Resource.success(newDev))
                }
                Log.d("WRITTEN", "Written: "+ deviceDao.immediateFindByName(name))
                val data = arrayOf(
                        "provision2",
                        b64pub)
                NfcHelper().sendNfc(this.activity!!, data.joinToString("|"))
                LocalBroadcastManager.getInstance(context!!).sendBroadcast(
                        Intent("com.matthewsuozzo.f2fauth.beam"))
            } else {
                Log.d("FUUUU", "Failed to create keypair")
            }
        }
    }

    fun finalize(name: String) {
        Log.d("FOOOOO", name)
        appExecutors.diskIO().execute {
            val foo = deviceDao.immediateFindByName(name)
            Log.d("FOOOOO", "${foo}")
            val newCopy = foo.copy(initialized = true)
            deviceDao.insert(newCopy)
            val newdefault = deviceDao.immediateGetDefault()
            Log.d("FOOOOO", "$newdefault")
            this@SearchFragment.activity?.runOnUiThread {
                searchViewModel.setDevice(Resource.success(newdefault))
            }
        }
    }

    fun requestAuth() {
        val data = arrayOf(
            "auth1",
            searchViewModel.device.value!!.data!!.name,
            getTime().toString()
        )
        val payload = signAndAppend(data.joinToString("|"))
        Log.d("GGGGGG", payload)
        NfcHelper().sendNfc(this.activity!!, payload)
        LocalBroadcastManager.getInstance(context!!).sendBroadcast(
                Intent("com.matthewsuozzo.f2fauth.beam"))
    }

    private lateinit var peerName: String
    private var peerTs: Long = 0
    private lateinit var peerSig: String

    private fun getTime(): Long {
        return System.currentTimeMillis() / 1000
    }

    private fun isExpired(ts: Long): Boolean {
        return (max(getTime(), ts) - ts) > 10
    }

    fun handleAuthRequest(name: String, tsStr: String, sig: String) {
        val ts = tsStr.toLong()
        if (isExpired(ts)) {
            Toast.makeText(context, "Expired", LENGTH_SHORT).show()
            return
        }
        this.peerName = name
        this.peerTs = ts
        this.peerSig = sig
        var service = AuthServiceFactory(searchViewModel.profile.value!!.data!!.serverUrl, resources, null).service
        appExecutors.networkIO().execute {
            val newValue: Resource<Device> = try {
                val resp = service.getDevice(peerName).execute()
                val apiResponse = ApiResponse.create(resp)
                Log.d("Resource", resp.toString())
                when (apiResponse) {
                    is ApiSuccessResponse -> Resource.success(apiResponse.body)
                    is ApiEmptyResponse -> Resource.error("Empty Response", null)
                    is ApiErrorResponse -> Resource.error(apiResponse.errorMessage, null)
                    null -> Resource.error("Failed to fetch", null)
                }
            } catch (e: IOException) {
                Resource.error(e.message!!, null)
            }
            Log.d("Resource", newValue.toString())
            activity?.runOnUiThread {
                searchViewModel.setPeerDevice(newValue)
            }
        }
    }

    private fun signAndAppend(payload: String): String {
        val sig = KeyStoreHelper(searchViewModel.device.value!!.data!!.name).signData(payload)
        return payload + "|${sig!!}"
    }

    fun doAuth() {
        appExecutors.diskIO().execute {
            val data = arrayOf(
                    "auth2",
                    searchViewModel.peerDevice.value!!.data!!.name,
                    peerTs,
                    peerSig,
                    searchViewModel.device.value!!.data!!.name,
                    getTime().toString()
            )
            val payload = signAndAppend(data.joinToString("|"))
            Log.d("GGGGGG", payload)
            NfcHelper().sendNfc(this.activity!!, payload)
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(
                    Intent("com.matthewsuozzo.f2fauth.beam"))
            activity?.runOnUiThread {
                searchViewModel.clearPeerDevice()
            }
        }
    }

    fun relayAuth(name: String, ts: String, sig: String, peerName: String, peerTs: String, peerSig: String) {
        var service = AuthServiceFactory(searchViewModel.profile.value!!.data!!.serverUrl, resources, null).service
        appExecutors.networkIO().execute {
            val newValue: Resource<String> = try {
                val resp = service.refreshToken(name, ts, sig, peerName, peerTs, peerSig).execute()
                val apiResponse = ApiResponse.create(resp)
                Log.d("Resource", resp.toString())
                when (apiResponse) {
                    is ApiSuccessResponse -> Resource.success(apiResponse.body.string())
                    is ApiEmptyResponse -> Resource.error("Empty Response", null)
                    is ApiErrorResponse -> Resource.error(apiResponse.errorMessage, null)
                    null -> Resource.error("Failed to fetch", null)
                }
            } catch (e: IOException) {
                Resource.error(e.message!!, null)
            }
            Log.d("Resource", newValue.toString())
            activity?.runOnUiThread {
                token = newValue.data!!
                binding.tokenField.text = token
            }
        }
    }
    private var token: String = ""

    /**
     * Created to be able to override in tests
     */
    fun navController() = findNavController()
}
