package github.aeonbtc.ibiswallet.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class AppUpdateServiceTest : FunSpec({
    test("picks the newest release including prereleases") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v3.2.1-beta",
                    "html_url": "https://example.com/beta",
                    "draft": false,
                    "prerelease": true
                  },
                  {
                    "tag_name": "v3.2.0",
                    "html_url": "https://example.com/320",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v3.2.1-beta"
        latest.htmlUrl shouldBe "https://example.com/beta"
    }

    test("ignores draft releases even when numerically newer") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v3.2.1",
                    "html_url": "https://example.com/draft",
                    "draft": true,
                    "prerelease": false
                  },
                  {
                    "tag_name": "v3.2.0",
                    "html_url": "https://example.com/live",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v3.2.0"
    }

    test("returns newest beta when only beta releases exist") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v3.1.3-beta",
                    "html_url": "https://example.com/beta1",
                    "draft": false,
                    "prerelease": true
                  },
                  {
                    "tag_name": "v3.1.2-beta",
                    "html_url": "https://example.com/beta2",
                    "draft": false,
                    "prerelease": true
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v3.1.3-beta"
        latest.htmlUrl shouldBe "https://example.com/beta1"
    }
})
