package pl.bartek.aidevs.db.resource

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.core.io.Resource
import java.nio.file.Files
import java.nio.file.Path

fun Path.calculateContentHash(): String = DigestUtils.sha256Hex(Files.newInputStream(this))

fun Resource.calculateContentHash(): String = DigestUtils.sha256Hex(this.inputStream)

fun String.calculateContentHash(): String = DigestUtils.sha256Hex(this)
