# The framework loads this entry class by name from META-INF/xposed/java_init.list.
-keep class com.houvven.guise.hook.HookEntry { public <init>(); }
-dontwarn io.github.libxposed.annotation.**
