package com.matthewsuozzo.f2fauth

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class NfcService : Service() {
    // Binder given to clients
    private val mBinder = LocalBinder()

    // Random number generator
    private var cur = 0

    /** method for clients  */
    val randomNumber: Int
        get() = cur++

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): NfcService = this@NfcService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }
}
