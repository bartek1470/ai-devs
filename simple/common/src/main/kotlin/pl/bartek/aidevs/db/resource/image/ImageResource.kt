package pl.bartek.aidevs.db.resource.image

import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import pl.bartek.aidevs.db.resource.BaseResource
import java.util.UUID

class ImageResource(
    id: EntityID<UUID>,
) : BaseResource(id, ImageResourceTable) {
    companion object : UUIDEntityClass<ImageResource>(ImageResourceTable)

    var path by ImageResourceTable.path
    var originalPath by ImageResourceTable.originalPath
    var description by ImageResourceTable.description
}
