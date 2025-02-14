package pl.bartek.aidevs.ai.openai.api.model

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateFineTuningJobResponse(
    @JsonProperty("object")
    val obj: String,
    val id: String,
    val model: String,
    @JsonProperty("created_at")
    val createdAt: Long,
    @JsonProperty("fine_tuned_model")
    val fineTunedModel: String?,
    @JsonProperty("organization_id")
    val organizationId: String,
    @JsonProperty("result_files")
    val resultFiles: List<String>,
    val status: String,
    @JsonProperty("validation_file")
    val validationFile: String?,
    @JsonProperty("training_file")
    val trainingFile: String,
)
