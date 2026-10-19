# Room, Hilt and Compose ship their own consumer rules. Nothing else here uses reflection.
-dontwarn org.slf4j.**
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
