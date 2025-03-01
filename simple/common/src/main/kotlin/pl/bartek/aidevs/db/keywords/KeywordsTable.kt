package pl.bartek.aidevs.db.keywords

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import pl.bartek.aidevs.db.transformers.StringListToSetTransformer

object KeywordsTable : IdTable<String>("keywords") {
    override val id: Column<EntityID<String>> = text("hash").entityId()
    val keywords = text("keywords").transform(StringListToSetTransformer)
}
