package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ReleaseNotesFormatterTest : FunSpec({
    test("strips heading markers and formats bullets") {
        val formatted =
            ReleaseNotesFormatter.toAnnotatedString(
                """
                ## What's Changed
                - Fix Tor reconnect
                - Add update changelog
                ### Notes
                1. First
                2. Second
                """.trimIndent(),
            )

        formatted.text shouldContain "What's Changed"
        formatted.text shouldContain "• Fix Tor reconnect"
        formatted.text shouldContain "• Add update changelog"
        formatted.text shouldContain "Notes"
        formatted.text shouldContain "1. First"
        formatted.text shouldContain "2. Second"
        formatted.text shouldNotContain "##"
        formatted.text shouldNotContain "###"
        formatted.text shouldNotContain "- Fix"
    }

    test("renders bold italic code and links without markup") {
        val formatted =
            ReleaseNotesFormatter.toAnnotatedString(
                "Use **bold**, *italic*, `code`, and [label](https://example.com).",
            )

        formatted.text shouldBe "Use bold, italic, code, and label."
    }

    test("strips html comments and fenced code language tags") {
        val formatted =
            ReleaseNotesFormatter.toAnnotatedString(
                """
                <!-- release bot -->
                ```kotlin
                val x = 1
                ```
                ---
                Done
                """.trimIndent(),
            )

        formatted.text shouldNotContain "release bot"
        formatted.text shouldNotContain "```"
        formatted.text shouldContain "val x = 1"
        formatted.text shouldContain "────────"
        formatted.text shouldContain "Done"
    }
})
