package pl.bartek.aidevs.task3

import com.fasterxml.jackson.annotation.JsonProperty

data class IndustrialRobotCalibrationFile(
    @JsonProperty("apikey")
    val apiKey: String,
    val description: String,
    val copyright: String,
    @JsonProperty("test-data")
    val testData: List<TestData>,
)
