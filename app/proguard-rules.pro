# Pingu Messages — R8 configuration.
#
# The app has no reflection-driven serialization layer, so the default AGP rules plus the
# consumer rules that ship with AndroidX cover almost everything. The entries below protect the
# pieces the platform instantiates by name and the AOSP-derived MMS PDU model.

# Components referenced from AndroidManifest.xml are kept by AGP automatically, but the MMS
# transport also resolves a few classes reflectively through the platform.
-keep class app.pingu.messages.data.mms.pdu.** { *; }

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Enum values used in Room type converters and saved-state handles.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelables created by the framework.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep line numbers for readable crash reports while still obfuscating names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coil decodes GIF/video frames through optional decoders resolved at runtime.
-dontwarn coil.decode.**
-dontwarn org.jetbrains.annotations.**
