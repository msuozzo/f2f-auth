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

package com.matthewsuozzo.f2fauth.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.matthewsuozzo.f2fauth.AppExecutors
import com.matthewsuozzo.f2fauth.R
import com.matthewsuozzo.f2fauth.api.*
import com.matthewsuozzo.f2fauth.binding.FragmentDataBindingComponent
import com.matthewsuozzo.f2fauth.databinding.HomeFragmentBinding
import com.matthewsuozzo.f2fauth.di.Injectable
import com.matthewsuozzo.f2fauth.testing.OpenForTesting
import com.matthewsuozzo.f2fauth.util.NfcHelper
import com.matthewsuozzo.f2fauth.util.autoCleared
import com.matthewsuozzo.f2fauth.vo.Device
import com.matthewsuozzo.f2fauth.vo.Resource
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class AckTask constructor(
        private val authService: AuthService,
        private val activity: FragmentActivity?,
        private val ackResultCallback: ((Resource<Boolean>) -> Unit)?
) : Runnable {
    override fun run() {
        val newValue = try {
            val response = authService.ack().execute()
            val apiResponse = ApiResponse.create(response)
            when (apiResponse) {
                is ApiSuccessResponse -> Resource.success(false)
                is ApiEmptyResponse -> Resource.success(true)
                is ApiErrorResponse -> Resource.error(apiResponse.errorMessage, true)
            }
        } catch (e: IOException) {
            Resource.error(e.message!!, true)
        }
        Log.d("Resource", newValue.toString())
        activity?.runOnUiThread {
            ackResultCallback?.invoke(newValue)
        }
    }
}

class ProvisionTask constructor(
        private val authService: AuthService,
        private val activity: FragmentActivity?,
        private val resultCallback: ((Resource<Device>) -> Unit)?
) : Runnable {
    override fun run() {
        val newValue: Resource<Device> = try {
            val resp = authService.provision().execute()
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
            resultCallback?.invoke(newValue)
        }
    }
}

class FinalizeTask constructor(
        private val authService: AuthService,
        private val activity: FragmentActivity?,
        private val name: String,
        private val pk: String,
        private val resultCallback: ((Resource<Device>) -> Unit)?
) : Runnable {
    override fun run() {
        val newValue: Resource<Device> = try {
            val resp = authService.finalizeProvision(name, pk).execute()
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
            resultCallback?.invoke(newValue)
        }
    }
}

@OpenForTesting
class HomeFragment : Fragment(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var appExecutors: AppExecutors

    var dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent(this)

    var binding by autoCleared<HomeFragmentBinding>()

    lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        this.binding = DataBindingUtil.inflate(
                inflater,
                R.layout.home_fragment,
                container,
                false,
                dataBindingComponent
        )

        return this.binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        this.homeViewModel = ViewModelProviders.of(this, this.viewModelFactory)
                .get(HomeViewModel::class.java)
        this.binding.setLifecycleOwner(this.viewLifecycleOwner)

        initInputListener()

        this.binding.provisionButton.setOnClickListener {
            // TODO: Try to get the button to trigger a write of the edittext
//            this.binding.endpointInput.isEnabled = false
//            this.binding.endpointInput.isEnabled = true
//            this.binding.endpointInput.onEditorAction(EditorInfo.IME_NULL)
            this@HomeFragment.provision(binding.endpointInput.text.toString())
//            navController().navigate(HomeFragmentDirections
//                    .provision(this.binding.endpointInput.text.toString())
//            )
        }

        this.binding.provisionButton2.setOnClickListener {
            if (binding.provisionResult?.value?.data != null) {
                val data = arrayOf(
                        "provision1",
                        endpointToUrl(binding.endpointInput.text.toString()),
                        binding.provisionResult!!.value!!.data!!.realm,
                        binding.provisionResult!!.value!!.data!!.name)
                NfcHelper().sendNfc(this.activity!!, data.joinToString("|"))
                LocalBroadcastManager.getInstance(context!!).sendBroadcast(
                        Intent("com.matthewsuozzo.f2fauth.beam"))
            } else {
                Log.d("FOOOOO", "No provision result")
            }
        }
        this.binding.provisionButton3.setOnClickListener {
            // TODO: imageupload
        }
        this.binding.provisionButton4.setOnClickListener {
            if (binding.provisionResult?.value?.data?.publicKey != null) {
                finalize(
                        binding.endpointInput.text.toString(),
                        binding.provisionResult!!.value!!.data!!.name,
                        binding.provisionResult!!.value!!.data!!.publicKey!!)
            }
        }

//        this.homeViewModel.ackResult.observe(this.viewLifecycleOwner, Observer {result ->
//            Log.d("observed", "${result.status}")
//        })
//        this.homeViewModel.provisionResult.observe(this.viewLifecycleOwner, Observer {result ->
//            Log.d("observed", "${result.status}")
//        })

        this.binding.provisionResult = homeViewModel.provisionResult
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalBroadcastManager.getInstance(context!!).registerReceiver(mMessageReceiver,
                IntentFilter("com.matthewsuozzo.f2fauth.provision2"))

    }
    override fun onDestroy() {
        LocalBroadcastManager.getInstance(context!!).unregisterReceiver(mMessageReceiver)
        super.onDestroy()
    }

    private val mMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            this@HomeFragment.updatePublicKey(intent.getStringExtra("pubkey"))
        }
    }

    private fun initInputListener() {
        this.binding.endpointInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveEndpoint(view)
                true
            } else {
                false
            }
        }
        this.binding.endpointInput.setOnKeyListener { view, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                saveEndpoint(view)
                true
            } else {
                false
            }
        }
    }

    private fun saveEndpoint(v: View) {
        val endpoint = this.binding.endpointInput.text.toString()
        dismissKeyboard(v.windowToken)
        this.homeViewModel.setEndpoint(endpoint)
        validateEndpoint(endpoint)
    }

    private fun dismissKeyboard(windowToken: IBinder) {
        val imm = this.activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun endpointToUrl(endpoint: String): String {
        return if (endpoint.startsWith("http")) endpoint else "https://$endpoint"
    }

    private fun validateEndpoint(endpoint: String) {
        val quickTimeoutClient = OkHttpClient.Builder()
                .readTimeout(3, TimeUnit.SECONDS)
                .connectTimeout(3, TimeUnit.SECONDS)
                .sslSocketFactory(TlsHelper().getSSLConfig(resources.openRawResource(R.raw.tls_cert)).socketFactory)
                .build()
        var service = AuthServiceFactory(endpointToUrl(endpoint), null, quickTimeoutClient).service
        this.appExecutors.networkIO().execute(AckTask(service, activity) {
            this.binding.textInputLayout4.error = if (it.data!!) it.message else null
            this.binding.provisionButton.isEnabled = !it.data
            this.homeViewModel.setAckResult(it)
        })
    }

    private fun provision(endpoint: String) {
        var service = AuthServiceFactory(endpointToUrl(endpoint), resources, null).service
        this.appExecutors.networkIO().execute(ProvisionTask(service, activity) {
            binding.provisionButton.isEnabled = it.data == null
            binding.provisionButton2.isEnabled = it.data != null
            homeViewModel.setProvisionResult(it)
        })
    }

    fun updatePublicKey(pubkey: String) {
        homeViewModel.provisionResult.value?.data?.copy(publicKey = pubkey)?.let {
            binding.provisionButton2.isEnabled = false
            binding.provisionButton4.isEnabled = true
            homeViewModel.setProvisionResult(Resource.success(it))
        }
    }

    fun finalize(endpoint: String, name: String, pk: String) {
        var service = AuthServiceFactory(endpointToUrl(endpoint), resources, null).service
        this.appExecutors.networkIO().execute(FinalizeTask(service, activity, name, pk) {
            it.data?.let {
                val data = arrayOf(
                        "provision3",
                        it.name)
                binding.provisionButton.isEnabled = true
                binding.provisionButton4.isEnabled = false
                homeViewModel.clearProvisionResult()
                NfcHelper().sendNfc(this.activity!!, data.joinToString("|"))
                LocalBroadcastManager.getInstance(context!!).sendBroadcast(
                        Intent("com.matthewsuozzo.f2fauth.beam"))
            }
        })
    }

    /**
     * Created to be able to override in tests
     */
    fun navController() = findNavController()
}
