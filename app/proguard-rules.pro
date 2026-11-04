# Release: в логах не должно быть текстов сообщений и сумм. Timber подключается только в debug,
# поэтому дополнительно вырезаем вызовы android.util.Log уровня v/d/i.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
