package com.sked.sked_app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules dynamic widget update alarms after device reboot or app update.
 */
class WidgetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i("WidgetBootReceiver", "Boot or package update detected ($action). Rescheduling widget alarms...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WidgetScheduleManager.scheduleNextUpdate(context)
                    TimetableRefreshWorker.updateWidget(context)
                } catch (e: Exception) {
                    Log.e("WidgetBootReceiver", "Failed on boot reschedule: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
