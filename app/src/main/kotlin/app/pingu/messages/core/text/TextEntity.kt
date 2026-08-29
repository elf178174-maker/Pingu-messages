package app.pingu.messages.core.text

/** A recognised span of text inside a message body. */
data class TextEntity(
    val type: Type,
    val start: Int,
    val endExclusive: Int,
    /** The raw matched text. */
    val text: String,
) {
    enum class Type { URL, EMAIL, PHONE, ADDRESS }

    val length: Int get() = endExclusive - start
}
