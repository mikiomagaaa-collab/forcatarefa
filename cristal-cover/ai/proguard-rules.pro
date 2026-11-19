# Motores nativos: as classes são usadas pelo JNI e pela reflexão (ferramentas do LiteRT-LM).
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.**
# Serialização dos arquivos de conversas, memórias e modelos.
-keepclassmembers class app.lily.ai.** { *** Companion; }
-keepclasseswithmembers class app.lily.ai.** { kotlinx.serialization.KSerializer serializer(...); }

# ML Kit (texto, rótulos, idioma, tradução, recorte). O ML Kit cria os "registrars" por reflexão a
# partir do manifesto; no modo completo do R8 o construtor vazio deles era removido (os nove ficavam
# sem construtor no APK) e o ML Kit não iniciava. O mesmo conserto do Cristal.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.mlkit.**

# WorkManager (vem com o LiteRT) e Room: o banco interno do WorkManager (WorkDatabase_Impl) e os
# InputMergers são criados por reflexão pelo construtor vazio. No modo estrito do R8 esse construtor era
# removido e a L.I.L.Y fechava na partida (erro visto no Razr com o L.I.L.Y Diagnóstico).
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * extends androidx.work.InputMerger { public <init>(); }
-keepclassmembers class * extends androidx.startup.Initializer { public <init>(); }

# Relatório de erro legível no próprio aparelho (a pessoa copia e manda): nomes originais de
# classes e métodos e as linhas. O código continua encolhido e otimizado; só não é renomeado.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
