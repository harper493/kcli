import AttributeMetadata
import Datatype

data class ClassMetadata(
    val name: String,
    val displayName: String,
    val jsonMetadata: String,
    val attributes: Array<AttributeMetadata>,
    val baseClasses: Array<ClassMetadata>
)
