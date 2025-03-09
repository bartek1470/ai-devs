package pl.bartek.aidevs.db.resource.text

import org.jetbrains.exposed.sql.Column
import pl.bartek.aidevs.db.resource.BaseResourceTable

object TextResourceTable : BaseResourceTable("text_resource") {
    val content: Column<String> = text("content")
}
