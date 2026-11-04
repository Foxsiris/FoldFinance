# SQLCipher использует JNI и рефлексию — классы нельзя переименовывать
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
