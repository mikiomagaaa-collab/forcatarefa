# ---------------------------------------------------------------------------------------------
# Regras do R8 para o Cristal Cover.
# Room, Hilt, Compose, DataStore e kotlinx.serialization já trazem as próprias regras.
# ---------------------------------------------------------------------------------------------

# O serviço de usuário do Shizuku é instanciado por reflexão em outro processo.
-keep class app.cristal.cover.data.shizuku.ShellService { *; }
-keep class app.cristal.cover.data.shizuku.IShellService { *; }
-keep class app.cristal.cover.data.shizuku.IShellService$* { *; }

# API do Shizuku (binder e provider).
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Modelos serializados no backup: mantém os serializers gerados.
-keepclassmembers class app.cristal.cover.** {
    *** Companion;
}
-keepclasseswithmembers class app.cristal.cover.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Mantém nomes de linhas nos stack traces para facilitar relatos de erro.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
