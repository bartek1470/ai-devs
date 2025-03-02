package pl.bartek.aidevs.db.transformers

import org.jetbrains.exposed.sql.ColumnTransformer
import java.nio.file.Path
import kotlin.io.path.Path

object StringToPathTransformer : ColumnTransformer<String, Path> {
    override fun unwrap(value: Path): String = value.toAbsolutePath().toString()

    override fun wrap(value: String): Path = Path(value)
}
