# Regras do R8 para o L.I.L.Y Keyboard.
-keepclassmembers class app.lily.keyboard.** {
    *** Companion;
}
-keepclasseswithmembers class app.lily.keyboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
