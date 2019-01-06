/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.matthewsuozzo.f2fauth.util


import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateException
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.security.auth.x500.X500Principal

class KeyStoreHelper constructor(val alias: String) {

    fun createKeyPair(): KeyPair? {
        try {
            val kp = createKeyPairInternal()
            Log.d(TAG, "Keys created")
            return kp
        } catch (e: NoSuchAlgorithmException) {
            Log.w(TAG, "RSA not supported", e)
        } catch (e: InvalidAlgorithmParameterException) {
            Log.w(TAG, "No such provider: AndroidKeyStore")
        } catch (e: NoSuchProviderException) {
            Log.w(TAG, "Invalid Algorithm Parameter Exception", e)
        }
        return null
    }

    /**
     * Creates a public and private key and stores it using the Android Key Store, so that only
     * this application will be able to access the keys.
     */
    @Throws(NoSuchProviderException::class, NoSuchAlgorithmException::class, InvalidAlgorithmParameterException::class)
    private fun createKeyPairInternal(): KeyPair? {
        // Create a start and end time, for the validity range of the key pair that's about to be
        // generated.
        val start = GregorianCalendar()
        val end = GregorianCalendar()
        end.add(Calendar.YEAR, 3)

        // Initialize a KeyPair generator using the the intended algorithm (in this example, RSA
        // and the KeyStore.  This example uses the AndroidKeyStore.
        val kpGenerator = KeyPairGenerator
                .getInstance("RSA","AndroidKeyStore")

        // The KeyPairGeneratorSpec object is how parameters for your key pair are passed
        // to the KeyPairGenerator.
        val spec: AlgorithmParameterSpec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setCertificateSubject(X500Principal("CN=$alias"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSerialNumber(BigInteger.valueOf(1337))
                    .setCertificateNotBefore(start.time)
                    .setCertificateNotAfter(end.time)
                    .build()

        kpGenerator.initialize(spec)

        val kp = kpGenerator.generateKeyPair()
        Log.d(TAG, "Public Key is: " + kp.public.toString())
        return kp
    }

    fun signData(inputStr: String): String? {
        try {
            val sig = signDataInternal(inputStr)
            Log.d(TAG, "Signature: $sig")
            return sig
        } catch (e: KeyStoreException) {
            Log.w(TAG, "KeyStore not Initialized", e)
        } catch (e: UnrecoverableEntryException) {
            Log.w(TAG, "KeyPair not recovered", e)
        } catch (e: NoSuchAlgorithmException) {
            Log.w(TAG, "RSA not supported", e)
        } catch (e: InvalidKeyException) {
            Log.w(TAG, "Invalid Key", e)
        } catch (e: SignatureException) {
            Log.w(TAG, "Invalid Signature", e)
        } catch (e: IOException) {
            Log.w(TAG, "IO Exception", e)
        } catch (e: CertificateException) {
            Log.w(TAG, "Error occurred while loading certificates", e)
        }
        return null
    }

    /**
     * Signs the data using the key pair stored in the Android Key Store.  This signature can be
     * used with the data later to verify it was signed by this application.
     * @return A string encoding of the data signature generated
     */
    @Throws(KeyStoreException::class, UnrecoverableEntryException::class, NoSuchAlgorithmException::class, InvalidKeyException::class, SignatureException::class, IOException::class, CertificateException::class)
    private fun signDataInternal(inputStr: String): String? {
        val data = inputStr.toByteArray()

        val ks = KeyStore.getInstance("AndroidKeyStore")

        // Weird artifact of Java API.  If you don't have an InputStream to load, you still need
        // to call "load", or it'll crash.
        ks.load(null)

        // Load the key pair from the Android Key Store
        val entry = ks.getEntry(alias, null)

        /* If the entry is null, keys were never stored under this alias.
         * Debug steps in this situation would be:
         * -Check the list of aliases by iterating over Keystore.aliases(), be sure the alias
         *   exists.
         * -If that's empty, verify they were both stored and pulled from the same keystore
         *   "AndroidKeyStore"
         */
        if (entry == null) {
            Log.w(TAG, "No key found under alias: $alias")
            Log.w(TAG, "Exiting signData()...")
            return null
        }

        /* If entry is not a KeyStore.PrivateKeyEntry, it might have gotten stored in a previous
         * iteration of your application that was using some other mechanism, or been overwritten
         * by something else using the same keystore with the same alias.
         * You can determine the type using entry.getClass() and debug from there.
         */
        if (entry !is KeyStore.PrivateKeyEntry) {
            Log.w(TAG, "Not an instance of a PrivateKeyEntry")
            Log.w(TAG, "Exiting signData()...")
            return null
        }

        // This class doesn't actually represent the signature,
        // just the engine for creating/verifying signatures, using
        // the specified algorithm.
        val s = Signature.getInstance("SHA256withRSA")

        // Initialize Signature using specified private key
        s.initSign(entry.privateKey)

        // Sign the data, store the result as a Base64 encoded String.
        s.update(data)
        val signature = s.sign()

        return Base64.encodeToString(signature, Base64.URL_SAFE)
    }

    fun verifyData(input: String, sig: String?): Boolean {
        var verified = false
        try {
            if (sig != null) {
                verified = verifyDataInternal(input, sig)
            }
        } catch (e: KeyStoreException) {
            Log.w(TAG, "KeyStore not Initialized", e)
        } catch (e: CertificateException) {
            Log.w(TAG, "Error occurred while loading certificates", e)
        } catch (e: NoSuchAlgorithmException) {
            Log.w(TAG, "RSA not supported", e)
        } catch (e: IOException) {
            Log.w(TAG, "IO Exception", e)
        } catch (e: UnrecoverableEntryException) {
            Log.w(TAG, "KeyPair not recovered", e)
        } catch (e: InvalidKeyException) {
            Log.w(TAG, "Invalid Key", e)
        } catch (e: SignatureException) {
            Log.w(TAG, "Invalid Signature", e)
        }

        if (verified) {
            Log.d(TAG, "Data Signature Verified")
        } else {
            Log.d(TAG, "Data not verified.")
        }
        return verified
    }

    /**
     * Given some data and a signature, uses the key pair stored in the Android Key Store to verify
     * that the data was signed by this application, using that key pair.
     * @param input The data to be verified.
     * @param signatureStr The signature provided for the data.
     * @return A boolean value telling you whether the signature is valid or not.
     */
    @Throws(KeyStoreException::class, CertificateException::class, NoSuchAlgorithmException::class, IOException::class, UnrecoverableEntryException::class, InvalidKeyException::class, SignatureException::class)
    private fun verifyDataInternal(input: String, signatureStr: String?): Boolean {
        val data = input.toByteArray()
        val signature: ByteArray
        // BEGIN_INCLUDE(decode_signature)

        // Make sure the signature string exists.  If not, bail out, nothing to do.

        if (signatureStr == null) {
            Log.w(TAG, "Invalid signature.")
            Log.w(TAG, "Exiting verifyData()...")
            return false
        }

        try {
            // The signature is going to be examined as a byte array,
            // not as a base64 encoded string.
            signature = Base64.decode(signatureStr, Base64.URL_SAFE)
        } catch (e: IllegalArgumentException) {
            // signatureStr wasn't null, but might not have been encoded properly.
            // It's not a valid Base64 string.
            return false
        }

        // END_INCLUDE(decode_signature)

        val ks = KeyStore.getInstance("AndroidKeyStore")

        // Weird artifact of Java API.  If you don't have an InputStream to load, you still need
        // to call "load", or it'll crash.
        ks.load(null)

        // Load the key pair from the Android Key Store
        val entry = ks.getEntry(alias, null)

        if (entry == null) {
            Log.w(TAG, "No key found under alias: $alias")
            Log.w(TAG, "Exiting verifyData()...")
            return false
        }

        if (entry !is KeyStore.PrivateKeyEntry) {
            Log.w(TAG, "Not an instance of a PrivateKeyEntry")
            return false
        }

        // This class doesn't actually represent the signature,
        // just the engine for creating/verifying signatures, using
        // the specified algorithm.
        val s = Signature.getInstance("SHA256withRSA")

        // BEGIN_INCLUDE(verify_data)
        // Verify the data.
        s.initVerify(entry.certificate)
        s.update(data)
        return s.verify(signature)
        // END_INCLUDE(verify_data)
    }

    companion object {
        const val TAG = "KeyStoreHelper"
    }
}
