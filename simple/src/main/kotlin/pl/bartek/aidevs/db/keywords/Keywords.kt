package pl.bartek.aidevs.db.keywords

import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Keywords(
    id: EntityID<String>,
) : Entity<String>(id) {
    companion object : EntityClass<String, Keywords>(KeywordsTable) {
        fun calculateHash(content: String): String = DigestUtils.sha256Hex(content)
    }

    var keywords by KeywordsTable.keywords
}
