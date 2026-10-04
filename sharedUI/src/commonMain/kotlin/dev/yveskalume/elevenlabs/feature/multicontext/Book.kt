package dev.yveskalume.elevenlabs.feature.multicontext

/**
 * A book read aloud one paragraph per context. Lines are sentences: the unit that is highlighted
 * and that can be tapped to jump to.
 */
data class Book(
    val title: String,
    val author: String,
    val paragraphs: List<Paragraph>,
) {
    val lines: List<BookLine> = buildList {
        paragraphs.forEachIndexed { paragraph, content ->
            content.heading?.let { add(BookLine(size, paragraph, it, isHeading = true)) }
            content.sentences.forEach { add(BookLine(size, paragraph, it, isHeading = false)) }
        }
    }

    private val firstLines: List<Int> = paragraphs.indices.map { paragraph ->
        lines.first { it.paragraph == paragraph }.index
    }

    val lastParagraph: Int
        get() = paragraphs.lastIndex

    fun firstLineOf(paragraph: Int): Int = firstLines[paragraph]

    /** The lines of [paragraph], starting at [fromLine]. */
    fun linesOf(paragraph: Int, fromLine: Int): List<BookLine> =
        lines.subList(fromLine, lines.size).takeWhile { it.paragraph == paragraph }
}

/** A paragraph, optionally opened by a heading that is read too. */
data class Paragraph(val sentences: List<String>, val heading: String? = null)

data class BookLine(
    val index: Int,
    val paragraph: Int,
    val text: String,
    val isHeading: Boolean,
) {
    /** The text sent to ElevenLabs. Headings get a full stop so they are read with a pause. */
    val spoken: String
        get() = if (isHeading) "$text." else text
}

internal val AesopsFables = Book(
    title = "Aesop's Fables",
    author = "Aesop",
    paragraphs = listOf(
        Paragraph(
            heading = "The North Wind and the Sun",
            sentences = listOf(
                "The North Wind and the Sun were arguing about which of them was stronger.",
                "Just then, a traveler came down the road, wrapped in a warm cloak.",
                "They agreed that whoever could make him take off his cloak would be the winner.",
            ),
        ),
        Paragraph(
            listOf(
                "The North Wind went first, and blew as hard as he could.",
                "But the harder he blew, the tighter the traveler pulled his cloak around him.",
                "At last, the North Wind gave up.",
            ),
        ),
        Paragraph(
            listOf(
                "Then the Sun came out and shone warmly on the road.",
                "The traveler loosened his cloak, then took it off and sat down in the shade.",
                "Gentle persuasion had done what force could not.",
            ),
        ),
        Paragraph(
            heading = "The Lion and the Mouse",
            sentences = listOf(
                "A lion was sleeping in the forest when a little mouse ran across his nose.",
                "The lion woke, caught her under his paw, and opened his jaws.",
                "\"Let me go,\" begged the mouse, \"and one day I will repay you.\"",
                "The lion laughed at the idea, but he let her go.",
            ),
        ),
        Paragraph(
            listOf(
                "Some time later, the lion was caught in a hunter's net.",
                "His roars echoed through the forest, and the mouse came running.",
                "She gnawed through the ropes, one by one, until the lion was free.",
                "Even the smallest friend can be a great help.",
            ),
        ),
        Paragraph(
            heading = "The Tortoise and the Hare",
            sentences = listOf(
                "A hare was always boasting about how fast he could run.",
                "One day, a tortoise quietly challenged him to a race.",
                "The hare laughed, but he agreed, and the whole forest came to watch.",
            ),
        ),
        Paragraph(
            listOf(
                "The hare shot off and was soon far ahead.",
                "Sure of winning, he lay down under a tree for a short nap.",
                "The tortoise kept going, slowly and steadily, without ever stopping.",
            ),
        ),
        Paragraph(
            listOf(
                "When the hare woke up, he ran to the finish as fast as he could.",
                "But the tortoise was already there, waiting for him.",
                "Slow and steady wins the race.",
            ),
        ),
        Paragraph(
            heading = "The Ant and the Grasshopper",
            sentences = listOf(
                "All summer long, a grasshopper sang and danced in the fields.",
                "Nearby, an ant worked from morning to night, carrying grain to her nest.",
                "\"Why work so hard?\" asked the grasshopper. \"Come and sing with me.\"",
            ),
        ),
        Paragraph(
            listOf(
                "When winter came, the fields were covered in snow.",
                "The hungry grasshopper knocked on the ant's door and asked for something to eat.",
                "\"You sang all summer,\" said the ant. \"Now you can dance all winter.\"",
                "There is a time for work, and a time for play.",
            ),
        ),
    ),
)
