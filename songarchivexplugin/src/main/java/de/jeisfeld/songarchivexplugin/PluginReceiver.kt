package de.jeisfeld.songarchivexplugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val PLUGIN_ACTION_VERIFY = "de.jeisfeld.songarchivexplugin.ACTION_VERIFY"
const val MAIN_PACKAGE_NAME = "de.jeisfeld.songarchive"
const val PLUGIN_ACTION_RESPONSE = "de.jeisfeld.songarchive.ACTION_PLUGIN_RESPONSE"
const val PLUGIN_VERIFICATION_RESPONSE = "PLUGIN_VERIFIED_12345"

class PluginReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == PLUGIN_ACTION_VERIFY) {
            val response = Intent().apply {
                action = PLUGIN_ACTION_RESPONSE
                `package` = MAIN_PACKAGE_NAME
                putExtra("verification_token", PLUGIN_VERIFICATION_RESPONSE)
            }
            context.sendBroadcast(response)
        }
    }
}