package com.kevinbevan.rivals.domain

/** Documents held in memory, keyed by path. Values are plain: String, Long, List, Map, null, timestamps. */
typealias Docs = Map<DocPath, Map<String, Any?>>

/**
 * Applies [WritePlan]s to in-memory documents the way a Firestore `WriteBatch` would: all or
 * nothing, with dotted update paths, increments and deletes. Backs guest games kept on the
 * phone, and the tests' fake store.
 */
object PlanApplier {

    /**
     * Returns [docs] with [plan] applied. Server timestamps become [timestamp].
     * @throws IllegalStateException if the plan updates or deletes a missing doc; nothing is applied.
     */
    fun apply(docs: Docs, plan: WritePlan, timestamp: Any): Docs {
        val out = LinkedHashMap(docs)
        for (write in plan) {
            when (write) {
                is Write.Set -> {
                    check(write.fields.keys.none { '.' in it }) { "Dotted key in Set: ${write.fields.keys}" }
                    out[write.doc] = write.fields.mapValues { resolve(null, it.value, timestamp) }
                }
                is Write.Update -> {
                    val doc = checkNotNull(out[write.doc]) { "Update of missing doc ${write.doc}" }.toMutableMap()
                    write.fields.forEach { (path, value) -> updatePath(doc, path.split('.'), value, timestamp) }
                    out[write.doc] = doc
                }
                is Write.Delete -> {
                    check(write.doc in out) { "Delete of missing doc ${write.doc}" }
                    out.remove(write.doc)
                }
            }
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun updatePath(doc: MutableMap<String, Any?>, path: List<String>, value: Any?, timestamp: Any) {
        if (path.size == 1) {
            if (value == FieldOp.Delete) doc.remove(path[0]) else doc[path[0]] = resolve(doc[path[0]], value, timestamp)
            return
        }
        val child = (doc[path[0]] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        updatePath(child, path.drop(1), value, timestamp)
        doc[path[0]] = child
    }

    private fun resolve(current: Any?, value: Any?, timestamp: Any): Any? = when (value) {
        is FieldOp.Increment -> ((current as? Number)?.toLong() ?: 0L) + value.by
        FieldOp.ServerTimestamp -> timestamp
        FieldOp.Delete -> error("FieldOp.Delete is only valid in an update")
        is Int -> value.toLong()
        is Map<*, *> -> value.mapValues { resolve(null, it.value, timestamp) }
        else -> value
    }
}
