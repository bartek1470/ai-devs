package pl.bartek.aidevs.db.resource

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column

abstract class BaseResourceTable(
    tableName: String,
) : UUIDTable(tableName) {
    val hash: Column<String> = text("hash").uniqueIndex()
    val name: Column<String> = text("name")

    override fun toString(): String = "BaseResourceTable(hash=$hash, name=$name)"
}
