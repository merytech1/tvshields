# Project-specific R8 rules.

# The engine registers its natives by class and method name in JNI_OnLoad
# (engine/src/jni_bridge.rs, NATIVE_CLASS): R8 must not rename or remove them.
-keep class io.github.merytech1.tvshields.engine.NativeEngine {
    native <methods>;
}
