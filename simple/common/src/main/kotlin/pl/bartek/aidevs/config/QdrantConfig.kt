package pl.bartek.aidevs.config

import org.springframework.ai.autoconfigure.vectorstore.qdrant.QdrantVectorStoreAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("!qdrant")
@Configuration
@EnableAutoConfiguration(exclude = [QdrantVectorStoreAutoConfiguration::class])
class QdrantConfig
