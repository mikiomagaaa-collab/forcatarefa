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

# Relatório de erro legível no próprio aparelho (a pessoa copia e manda): nomes originais de
# classes e métodos e as linhas. O código continua encolhido e otimizado; só não é renomeado.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
