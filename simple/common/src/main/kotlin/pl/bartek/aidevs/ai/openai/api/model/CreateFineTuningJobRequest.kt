package pl.bartek.aidevs.ai.openai.api.model

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateFineTuningJobRequest(
    @JsonProperty("training_file")
    val trainingFile: String,
    val model: String,
)
