package pl.bartek.aidevs.db.resource

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

abstract class BaseResource(
    id: EntityID<UUID>,
    table: BaseResourceTable,
) : UUIDEntity(id) {
    var hash by table.hash
    var name by table.name

    override fun toString(): String = "BaseResource(id='$id', hash='$hash', name='$name')"
}
