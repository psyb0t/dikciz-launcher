# LuaJ resolves library function classes by name and invokes their no-argument constructors.
-keep class org.luaj.vm2.lib.** { *; }

# Preserve Rhino built-in member names used by reflective registration.
-keep,allowshrinking class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.Interpreter { *; }

# Interpreter mode and JSON-only bindings exclude desktop compiler, JavaBeans and JSR-223 paths.
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn javax.script.**


# ML Kit discovers registrars from manifest metadata and constructs them reflectively.
-keep class com.google.mlkit.** implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
