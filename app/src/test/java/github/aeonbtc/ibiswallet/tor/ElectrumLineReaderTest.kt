package github.aeonbtc.ibiswallet.tor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader

class ElectrumLineReaderTest : FunSpec({

    test("returns every line buffered in the same socket chunk") {
        val reader = BufferedReader(StringReader("one\ntwo\nthree\n"))
        val state = BoundedLineState()

        readBoundedLine(reader, state, 100) shouldBe "one"
        readBoundedLine(reader, state, 100) shouldBe "two"
        readBoundedLine(reader, state, 100) shouldBe "three"
        readBoundedLine(reader, state, 100) shouldBe null
    }

    test("handles CRLF line endings") {
        val reader = BufferedReader(StringReader("one\r\ntwo\r\n"))
        val state = BoundedLineState()

        readBoundedLine(reader, state, 100) shouldBe "one"
        readBoundedLine(reader, state, 100) shouldBe "two"
    }

    test("reassembles a line spanning multiple chunk reads") {
        val longLine = "a".repeat(9_000)
        val reader = BufferedReader(StringReader("$longLine\nnext\n"))
        val state = BoundedLineState()

        readBoundedLine(reader, state, 20_000) shouldBe longLine
        readBoundedLine(reader, state, 20_000) shouldBe "next"
    }

    test("rejects a line beyond the configured cap") {
        val reader = BufferedReader(StringReader("${"a".repeat(20)}\n"))
        val state = BoundedLineState()

        shouldThrow<IOException> {
            readBoundedLine(reader, state, 10)
        }
    }
})
