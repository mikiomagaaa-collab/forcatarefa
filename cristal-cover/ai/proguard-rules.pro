# Motores nativos: as classes são usadas pelo JNI e pela reflexão (ferramentas do LiteRT-LM).
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.**
# Serialização dos arquivos de conversas, memórias e modelos.
-keepclassmembers class app.lily.ai.** { *** Companion; }
-keepclasseswithmembers class app.lily.ai.** { kotlinx.serialization.KSerializer serializer(...); }
