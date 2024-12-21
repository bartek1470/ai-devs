package pl.bartek.aidevs.task0305

import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Relationship

@Node("User")
data class User(
    @Id
    val id: Int,
    val username: String,
    @Relationship(value = "CONNECTED", direction = Relationship.Direction.INCOMING)
    val relatedUsers: MutableList<User> = mutableListOf(),
)
