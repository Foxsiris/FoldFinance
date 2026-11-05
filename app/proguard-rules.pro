# Release: в логах не должно быть текстов сообщений и сумм. Timber подключается только в debug,
# поэтому дополнительно вырезаем вызовы android.util.Log уровня v/d/i.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ML Kit (GenAI Prompt, beta) находит регистраторы компонентов рефлексией (ComponentDiscovery):
# без конструктора по умолчанию R8 ломает инициализацию, и локальная модель в release недоступна.
-keep class * implements com.google.firebase.components.ComponentRegistrar { public <init>(); }
