package pl.bartek.aidevs.task0305

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jline.terminal.Terminal
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.println
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrNull

@Service
class Task0305Service(
    @Value("\${aidevs.task.0303.api-url}") private val apiUrl: String,
    @Value("\${aidevs.task.0303.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    private val restClient: RestClient,
    private val userRepository: UserRepository,
    private val aiDevsApiClient: AiDevsApiClient,
) {
    fun run(terminal: Terminal) {
        userRepository.deleteAll()

        Executors
            .newFixedThreadPool(3)
            .asCoroutineDispatcher()
            .use { dispatcher ->
                runBlocking {
                    val usersWithConnections =
                        findUsers().associateWith {
                            withContext(dispatcher) {
                                async { findConnections(it.id) }
                            }
                        }

                    terminal.println("Found ${usersWithConnections.keys.size} users")
                    userRepository.saveAll(usersWithConnections.keys.map { User(it.id, it.username) })
                    terminal.println("Saved users")

                    usersWithConnections.mapValues {
                        val dbConnections = it.value.await()

                        dbConnections.forEach {
                            try {
                                val user = userRepository.findById(it.user1Id).getOrNull()!!
                                val user2 = userRepository.findById(it.user2Id).getOrNull()!!
                                user.relatedUsers.add(user2)
                                userRepository.save(user)
                            } catch (ex: Exception) {
                                log.error(ex) { "Cannot create connection ${it.user1Id} -> ${it.user2Id}" }
                            }
                        }
                    }
                    terminal.println("Finished downloading")
                }
            }

        terminal.println("Waiting for submitting answer")
        val shortestPath = userRepository.findShortestPath("Rafał", "Barbara").map { it.username }.joinToString(", ")
        val answer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.CONNECTIONS, shortestPath))
        terminal.println(answer)
    }

    suspend fun findUsers(): List<DbUser> =
        coroutineScope {
            val countResponse =
                restClient
                    .post()
                    .uri(apiUrl)
                    .body(DbRequest("select count(*) as user_count from users;", apiKey))
                    .retrieve()
                    .body(object : ParameterizedTypeReference<DbApiResponse<JsonNode>>() {})
                    ?: throw IllegalStateException("Cannot count users")

            val count = countResponse.reply[0]["user_count"].asInt()
            val responses = mutableListOf<Deferred<DbApiResponse<DbUser>>>()
            for (i in 0..count step 10) {
                val users =
                    async {
                        restClient
                            .post()
                            .uri(apiUrl)
                            .body(DbRequest("select * from users limit 10 offset $i;", apiKey))
                            .retrieve()
                            .body(object : ParameterizedTypeReference<DbApiResponse<DbUser>>() {})
                            ?: throw IllegalStateException("Cannot find users")
                    }
                responses.add(users)
            }

            responses.awaitAll().flatMap { it.reply }
        }

    suspend fun findConnections(userId: Int): List<DbConnection> {
        val connections =
            restClient
                .post()
                .uri(apiUrl)
                .body(DbRequest("select * from connections where user1_id = $userId;", apiKey))
                .retrieve()
                .body(object : ParameterizedTypeReference<DbApiResponse<DbConnection>>() {})
                ?: throw IllegalStateException("Cannot find connections for user $userId")
        return connections.reply
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
