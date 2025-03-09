package pl.bartek.aidevs.db.resource.image

import pl.bartek.aidevs.db.resource.BaseResourceTable
import pl.bartek.aidevs.db.transformers.StringToPathTransformer

object ImageResourceTable : BaseResourceTable("image_resource") {
    val path = text("path").uniqueIndex().transform(StringToPathTransformer)
    val originalPath = text("original_path").uniqueIndex().transform(StringToPathTransformer)
    val description = text("description")
}
