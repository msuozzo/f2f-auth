package com.matthewsuozzo.f2fauth.util

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.util.Log
import androidx.fragment.app.FragmentActivity
import java.nio.charset.Charset

class NfcHelper {
    fun sendNfc(activity: FragmentActivity, payload: String) {
        val adapter = NfcAdapter.getDefaultAdapter(activity.applicationContext)
        val mimeRecord = NdefRecord.createMime(
                "application/vnd.com.example.android.beam",
                payload.toByteArray(Charset.forName("US-ASCII"))
        )
        Log.d("AUTHSENDING", mimeRecord.payload.toString())
        adapter.setNdefPushMessage(
                NdefMessage(mimeRecord, NdefRecord.createApplicationRecord("com.matthewsuozzo.f2fauth")),
                activity)
    }
}
