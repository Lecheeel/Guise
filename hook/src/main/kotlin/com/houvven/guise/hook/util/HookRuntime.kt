package com.houvven.guise.hook.util

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Small reflection layer over API 102 interceptors. No legacy Xposed bridge is used. */
internal class HookRuntime(
    private val module: XposedInterface,
    val classLoader: ClassLoader,
    val packageName: String,
) {
    private val installed = ConcurrentHashMap.newKeySet<Method>()

    fun findClass(name: String): Class<*>? = runCatching {
        Class.forName(name, false, classLoader)
    }.getOrNull()

    fun methods(type: String, vararg names: String): List<Method> {
        val clazz = findClass(type) ?: return emptyList()
        return generateSequence(clazz) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.name in names && !Modifier.isAbstract(it.modifiers) }
            .distinctBy { it.name to it.parameterTypes.toList() }
            .toList()
    }

    fun replace(type: String, name: String, value: (XposedInterface.Chain) -> Any?) {
        methods(type, name).forEach { method ->
            install(method) { chain -> value(chain) }
        }
    }

    fun after(type: String, vararg names: String, transform: (Any?, XposedInterface.Chain) -> Any?) {
        methods(type, *names).forEach { method ->
            install(method) { chain ->
                val original = chain.proceed()
                runCatching { transform(original, chain) }.getOrElse {
                    warn("${method.declaringClass.name}.${method.name}", it)
                    original
                }
            }
        }
    }

    fun before(method: Method, arguments: (XposedInterface.Chain) -> Array<Any?>) {
        install(method) { chain ->
            val args = runCatching { arguments(chain) }.getOrElse {
                warn(method.name, it)
                chain.args.toTypedArray()
            }
            chain.proceed(args)
        }
    }

    private fun install(method: Method, interceptor: XposedInterface.Hooker) {
        if (!installed.add(method)) return
        try {
            module.hook(method).intercept(interceptor)
        } catch (error: Throwable) {
            installed.remove(method)
            warn("Cannot hook $method", error)
        }
    }

    fun setField(target: Any?, type: Class<*>, name: String, value: Any?) {
        var current: Class<*>? = type
        while (current != null) {
            val owner = current
            val field = runCatching { owner.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                runCatching { field.isAccessible = true; field.set(target, value) }
                    .onFailure { warn("Cannot set ${type.name}.$name", it) }
                return
            }
            current = current.superclass
        }
        warn("Missing field ${type.name}.$name", null)
    }

    fun warn(message: String, error: Throwable?) = module.log(Log.WARN, "GuiseHook", message, error)
}
