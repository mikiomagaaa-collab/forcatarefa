# Serialização dos arquivos de conversas, memórias e modelos.
-keepclassmembers class app.lily.gallery.** { *** Companion; }
-keepclasseswithmembers class app.lily.gallery.** { kotlinx.serialization.KSerializer serializer(...); }
