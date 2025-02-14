package pl.bartek.aidevs.db

import org.jetbrains.exposed.sql.ColumnTransformer

object NullableStringListToNullableSetTransformer : ColumnTransformer<String?, Set<String>?> {
    override fun unwrap(value: Set<String>?): String? = value?.joinToString(",")

    override fun wrap(value: String?): Set<String>? = value?.split(",")?.filter { it.isNotBlank() }?.toSortedSet()
}
