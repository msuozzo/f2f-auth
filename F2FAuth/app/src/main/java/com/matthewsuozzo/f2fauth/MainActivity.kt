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

package com.matthewsuozzo.f2fauth

import android.app.PendingIntent
import android.content.*
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import javax.inject.Inject
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.Intent



class MainActivity : AppCompatActivity(), HasSupportFragmentInjector {
    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    private lateinit var intentFiltersArray: Array<IntentFilter>

    private lateinit var techListsArray: Array<Array<String>>
    private lateinit var mAdapter: NfcAdapter

    private lateinit var pendingIntent: PendingIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        mAdapter = NfcAdapter.getDefaultAdapter(applicationContext)
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")    /* Handles all MIME based dispatches.
                                 You should specify only the ones that you need. */
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }
        val beam = IntentFilter("com.matthewsuozzo.f2fauth.beam").apply {
            try {
                addDataType("*/*")    /* Handles all MIME based dispatches.
                                 You should specify only the ones that you need. */
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }

        intentFiltersArray = arrayOf(ndef, beam)
        techListsArray = arrayOf(arrayOf(NfcF::class.java.name, NfcV::class.java.name, Ndef::class.java.name))

        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(mMessageReceiver,
                IntentFilter("com.matthewsuozzo.f2fauth.beam"))
    }
    override fun onDestroy() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(mMessageReceiver)
        super.onDestroy()
    }
    private val mMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            NfcAdapter.getDefaultAdapter(applicationContext).invokeBeam(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent)
    }


    override fun onResume() {
        super.onResume()

        mAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            Log.d("AUTHRECEIVING", intent?.toString())
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.also { rawMsgs ->
                Log.d("AUTHCOUNT", "Received " + rawMsgs.size + " messages")
                (rawMsgs[0] as NdefMessage).apply {
                    // record 0 contains the MIME type, record 1 is the AAR, if present
                    Log.d("AUTHRECEIVED", String(records[0].payload))
                    val sections = String(records[0].payload).split("|")
                    when {
                        sections.isEmpty() -> Log.d("FOOOOO", "Empty sections")
                        sections[0] == "provision1" -> LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(
                                Intent("com.matthewsuozzo.f2fauth.provision1").apply {
                                    putExtra("url", sections[1])
                                    putExtra("realm", sections[2])
                                    putExtra("name", sections[3])
                                })
                        sections[0] == "provision2" -> LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(
                                Intent("com.matthewsuozzo.f2fauth.provision2").apply {
                                    putExtra("pubkey", sections[1])
                                })
                        sections[0] == "provision3" -> LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(
                                Intent("com.matthewsuozzo.f2fauth.provision3").apply {
                                    putExtra("name", sections[1])
                                })
                        sections[0] == "auth1" -> LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(
                                Intent("com.matthewsuozzo.f2fauth.auth1").apply {
                                    putExtra("peerName", sections[1])
                                    putExtra("peerTs", sections[2])
                                    putExtra("peerSig", sections[3])
                                })
                        sections[0] == "auth2" -> LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(
                                Intent("com.matthewsuozzo.f2fauth.auth2").apply {
                                    putExtra("name", sections[1])
                                    putExtra("ts", sections[2])
                                    putExtra("sig", sections[3])
                                    putExtra("peerName", sections[4])
                                    putExtra("peerTs", sections[5])
                                    putExtra("peerSig", sections[6])
                                })
                        else -> Log.d("FOOOOO", "Unknown NFC message")
                    }
                }
            }
        }
    }

    override fun supportFragmentInjector() = dispatchingAndroidInjector
}


