package pl.bartek.aidevs.db.audio

import org.jetbrains.exposed.sql.Column
import pl.bartek.aidevs.db.resource.BaseResourceTable
import pl.bartek.aidevs.db.transformers.StringToPathTransformer
import java.nio.file.Path

object AudioResourceTable : BaseResourceTable("audio_resource") {
    val path: Column<Path> = text("path").uniqueIndex().transform(StringToPathTransformer)
    val transcription: Column<String> = text("transcription")
}
