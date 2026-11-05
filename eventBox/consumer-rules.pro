# Applied automatically to every app that depends on Event Box.
# Only the public API is kept by name; everything else may be obfuscated and removed by the
# consuming app's R8.
-keep public class com.marocsbhai.eventbox.OGEventBoxLog {
    public *;
}
-keep public class com.marocsbhai.eventbox.model.OGEventBoxDebugEvent {
    public *;
}
-keep public class com.marocsbhai.eventbox.validator.OGEventBoxValidator {
    public *;
}

-keep public enum com.marocsbhai.eventbox.model.OGEventBoxBackgroundMode {
    *;
}

-keep public class com.marocsbhai.eventbox.OGEventBoxAnalytics {
    public *;
}
-keep public interface com.marocsbhai.eventbox.OGEventBoxAnalytics$EventSink {
    *;
}
-keep public interface com.marocsbhai.eventbox.OGEventBoxAnalytics$UserPropertySink {
    *;
}
