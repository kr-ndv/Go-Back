const fs = require('fs');
const path = require('path');
const { withAndroidManifest, withDangerousMod } = require('@expo/config-plugins');

const provider = `package com.goback.focus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class GoBackWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_widget)
      bindAction(context, views, R.id.widget_focus, "focus", 100)
      bindAction(context, views, R.id.widget_tasks, "tasks", 101)
      bindAction(context, views, R.id.widget_exams, "exams", 102)
      bindAction(context, views, R.id.widget_habits, "habits", 103)
      manager.updateAppWidget(widgetId, views)
    }
  }

  private fun bindAction(context: Context, views: RemoteViews, viewId: Int, route: String, requestCode: Int) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("goback:///" + route), context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(viewId, pendingIntent)
  }
}
`;

const layout = `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="14dp"
    android:background="@drawable/go_back_widget_bg">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="GO BACK"
        android:textColor="#B8F36B"
        android:textSize="10sp"
        android:textStyle="bold"
        android:letterSpacing="0.15" />
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:text="Make today count."
        android:textColor="#F5FAF2"
        android:textSize="18sp"
        android:textStyle="bold" />
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="10dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:weightSum="4">
        <TextView android:id="@+id/widget_focus" style="@style/GoBackWidgetAction" android:text="Focus" />
        <TextView android:id="@+id/widget_tasks" style="@style/GoBackWidgetAction" android:text="Tasks" />
        <TextView android:id="@+id/widget_exams" style="@style/GoBackWidgetAction" android:text="Exams" />
        <TextView android:id="@+id/widget_habits" style="@style/GoBackWidgetAction" android:text="Habits" />
    </LinearLayout>
</LinearLayout>
`;

const styles = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="GoBackWidgetAction">
        <item name="android:layout_width">0dp</item>
        <item name="android:layout_height">42dp</item>
        <item name="android:layout_weight">1</item>
        <item name="android:layout_marginEnd">5dp</item>
        <item name="android:gravity">center</item>
        <item name="android:background">@drawable/go_back_widget_action_bg</item>
        <item name="android:textColor">#F5FAF2</item>
        <item name="android:textSize">11sp</item>
        <item name="android:textStyle">bold</item>
    </style>
</resources>
`;

const widgetBackground = `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#17352A" />
    <corners android:radius="22dp" />
</shape>
`;

const actionBackground = `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#294638" />
    <corners android:radius="12dp" />
</shape>
`;

const widgetInfo = `<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="120dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/go_back_widget"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
`;

function withGoBackWidget(config) {
  config = withAndroidManifest(config, config => {
    const application = config.modResults.manifest.application?.[0];
    if (!application) throw new Error('Go Back widget: Android application node was not found.');
    application.receiver = application.receiver ?? [];
    const exists = application.receiver.some(receiver => receiver.$?.['android:name'] === '.GoBackWidgetProvider');
    if (!exists) {
      application.receiver.push({
        $: {
          'android:name': '.GoBackWidgetProvider',
          'android:label': 'Go Back Quick Actions',
          'android:exported': 'true',
        },
        'intent-filter': [{ action: [{ $: { 'android:name': 'android.appwidget.action.APPWIDGET_UPDATE' } }] }],
        'meta-data': [{ $: { 'android:name': 'android.appwidget.provider', 'android:resource': '@xml/go_back_widget_info' } }],
      });
    }
    return config;
  });

  config = withDangerousMod(config, ['android', async config => {
    const root = config.modRequest.platformProjectRoot;
    const javaRoot = path.join(root, 'app', 'src', 'main', 'java', 'com', 'goback', 'focus');
    const resRoot = path.join(root, 'app', 'src', 'main', 'res');
    const write = (file, value) => {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, value);
    };
    write(path.join(javaRoot, 'GoBackWidgetProvider.kt'), provider);
    write(path.join(resRoot, 'layout', 'go_back_widget.xml'), layout);
    write(path.join(resRoot, 'values', 'go_back_widget_styles.xml'), styles);
    write(path.join(resRoot, 'drawable', 'go_back_widget_bg.xml'), widgetBackground);
    write(path.join(resRoot, 'drawable', 'go_back_widget_action_bg.xml'), actionBackground);
    write(path.join(resRoot, 'xml', 'go_back_widget_info.xml'), widgetInfo);
    return config;
  }]);
  return config;
}

module.exports = withGoBackWidget;
