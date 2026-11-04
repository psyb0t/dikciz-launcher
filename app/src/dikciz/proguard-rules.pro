# LuaJ resolves library function classes by name and invokes their no-argument constructors.
-keep class org.luaj.vm2.lib.** { *; }


# ML Kit discovers registrars from manifest metadata and constructs them reflectively.
-keep class com.google.mlkit.** implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
