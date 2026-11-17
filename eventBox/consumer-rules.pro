# Applied automatically to every app that depends on Event Box.
# Only the public API is kept by name; everything else may be obfuscated and removed by the
# consuming app's R8.
-keep public class com.marcosbhai.eventbox.OGEventBoxLog {
    public *;
}
-keep public class com.marcosbhai.eventbox.model.OGEventBoxDebugEvent {
    public *;
}
-keep public class com.marcosbhai.eventbox.validator.OGEventBoxValidator {
    public *;
}

-keep public enum com.marcosbhai.eventbox.model.OGEventBoxBackgroundMode {
    *;
}

-keep public class com.marcosbhai.eventbox.OGEventBoxAnalytics {
    public *;
}
-keep public interface com.marcosbhai.eventbox.OGEventBoxAnalytics$EventSink {
    *;
}
-keep public interface com.marcosbhai.eventbox.OGEventBoxAnalytics$UserPropertySink {
    *;
}

-keep public enum com.marcosbhai.eventbox.model.OGEventBoxFilter {
    *;
}
