import ShowLevel
import Datatype
import AttributeMetadata

class CollectionMetadata(
    name: String,
    owner: ClassMetadata,
    level: ShowLevel,
    type: Datatype,
    nature: String,
    explanation: String,
    relative_url: String
) : AttributeMetadata(name, owner, level, type, nature, explanation) {
    lateinit var classMetadata: ClassMetadata
}
