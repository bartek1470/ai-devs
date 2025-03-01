package pl.bartek.aidevs.db.transformers

import org.jetbrains.exposed.sql.ColumnTransformer

object StringListToIntSetTransformer : ColumnTransformer<String, Set<Int>> {
    override fun unwrap(value: Set<Int>): String = value.joinToString(",")

    override fun wrap(value: String): Set<Int> = value.split(",").map { it.toInt() }.toSortedSet()
}
