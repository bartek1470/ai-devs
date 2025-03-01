package pl.bartek.aidevs.db.transformers

import org.jetbrains.exposed.sql.ColumnTransformer

object StringListToSetTransformer : ColumnTransformer<String, Set<String>> {
    override fun unwrap(value: Set<String>): String = value.joinToString(",")

    override fun wrap(value: String): Set<String> = value.split(",").filter { it.isNotBlank() }.toSortedSet()
}
