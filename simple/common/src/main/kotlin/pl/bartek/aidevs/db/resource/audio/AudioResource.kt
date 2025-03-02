package pl.bartek.aidevs.db.resource.audio

import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import pl.bartek.aidevs.db.audio.AudioResourceTable
import pl.bartek.aidevs.db.resource.BaseResource
import java.util.UUID

class AudioResource(
    id: EntityID<UUID>,
) : BaseResource(id, AudioResourceTable) {
    companion object : UUIDEntityClass<AudioResource>(AudioResourceTable)

    var path by AudioResourceTable.path
    var transcription by AudioResourceTable.transcription
}
