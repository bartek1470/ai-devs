package pl.bartek.aidevs.task0305

import org.springframework.data.neo4j.repository.Neo4jRepository
import org.springframework.data.neo4j.repository.query.Query
import org.springframework.stereotype.Repository

@Repository
interface UserRepository: Neo4jRepository<User, Int> {

    @Query("MATCH p=shortestPath((b:User {username: \$startUsername})-[*]-(r:User {username: \$endUsername})) RETURN nodes(p) AS nodes")
    fun findShortestPath(startUsername: String, endUsername: String): List<User>
}
