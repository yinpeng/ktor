package io.ktor.util

actual fun Attributes(concurrent: Boolean): Attributes = AttributesIos()

private class AttributesIos : Attributes {
    private val map = mutableMapOf<AttributeKey<*>, Any?>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    override operator fun contains(key: AttributeKey<*>): Boolean = map.containsKey(key)

    override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        map[key]?.let { return it as T }
        return block().also { result ->
            map[key] = result
        }
    }

    override val allKeys: List<AttributeKey<*>>
        get() = map.keys.toList()
}
