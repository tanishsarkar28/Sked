package com.sked.sked_app.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver that ties the AppWidget metadata declared in
 * res/xml/timetable_widget_info.xml to our GlanceAppWidget implementation.
 *
 * Register in AndroidManifest.xml:
 *
 *   <receiver
 *       android:name=".widget.TimetableWidgetReceiver"
 *       android:exported="true">
 *     <intent-filter>
 *       <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
 *     </intent-filter>
 *     <meta-data
 *         android:name="android.appwidget.provider"
 *         android:resource="@xml/timetable_widget_info" />
 *   </receiver>
 */
class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TimetableWidget()
}
