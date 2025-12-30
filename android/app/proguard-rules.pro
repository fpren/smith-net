# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep BLE mesh message classes
-keep class com.guildofsmiths.trademesh.data.** { *; }

# Keep AI assistant classes
-keep class com.guildofsmiths.trademesh.ai.** { *; }

# Keep native JNI methods for llama.cpp
-keep class com.guildofsmiths.trademesh.ai.LlamaInference {
    native <methods>;
}

# Keep serialization classes
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.reflect.** { *; }
