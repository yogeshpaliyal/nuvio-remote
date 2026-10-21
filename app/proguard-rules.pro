# Skiff's R8 rules, on top of proguard-android-optimize.txt and the libraries' own (Firebase, Coil, Compose, okio).

# Crashlytics: stack traces with line numbers (the mapping is uploaded with each release build).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Settings keep the connection choice by its enum name (Connection.Via.valueOf): names must stay the same across
# versions, or an update would forget the choice.
-keepclassmembers enum dev.skiff.Connection$Via { *; }

# dadb (ADB to the TV) has no rules of its own; it's small, keep it as it is.
-keep class dadb.** { *; }
