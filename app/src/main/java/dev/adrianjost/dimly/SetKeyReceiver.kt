package dev.adrianjost.dimly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Optional: replaces the generated network key with your own, e.g. to keep the key your
 * home automation already uses after reinstalling.
 *
 *     adb shell am broadcast -a dev.adrianjost.dimly.SET_KEY -n dev.adrianjost.dimly/.SetKeyReceiver --es key <your-key>
 *
 * The manifest guards it with the DUMP permission, which the adb shell has and normal apps
 * can't get, so other apps on the device can't change the key.
 */
class SetKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key")?.trim().orEmpty()
        if (key.length < MIN_LENGTH || key.any { it.isWhitespace() }) {
            Log.w(TAG, "Key not changed: it needs at least $MIN_LENGTH characters and no spaces")
            resultCode = 1
            return
        }
        Prefs(context).key = key
        Log.i(TAG, "Network key replaced")
        resultCode = 0
    }

    private companion object {
        const val TAG = "Dimly"
        const val MIN_LENGTH = 12
    }
}
