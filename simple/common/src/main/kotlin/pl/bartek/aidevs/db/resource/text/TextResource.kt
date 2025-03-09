package pl.bartek.aidevs.db.resource.text

import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import pl.bartek.aidevs.db.resource.BaseResource
import java.util.UUID

class TextResource(
    id: EntityID<UUID>,
) : BaseResource(id, TextResourceTable) {
    companion object : UUIDEntityClass<TextResource>(TextResourceTable)

    var content by TextResourceTable.content
}
