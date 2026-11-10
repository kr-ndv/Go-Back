# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Preserve Room Database, Daos, and Entities
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class com.goback.focus.data.entity.** { *; }
-keep class com.goback.focus.data.dao.** { *; }
-keep class com.goback.focus.data.db.** { *; }

# Preserve Glance AppWidget and Action Callbacks
-keep class com.goback.focus.widget.** { *; }
-keep class androidx.glance.appwidget.action.** { *; }

# Preserve WorkManager Workers
-keep class com.goback.focus.service.GoBackMaintenanceWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Preserve TileService and AccessibilityService
-keep class com.goback.focus.service.FocusTileService { *; }
-keep class com.goback.focus.service.GoBackBlockingService { *; }
-keep class com.goback.focus.service.FocusTimerService { *; }

# Preserve Broadcast Receivers
-keep class com.goback.focus.receiver.** { *; }
