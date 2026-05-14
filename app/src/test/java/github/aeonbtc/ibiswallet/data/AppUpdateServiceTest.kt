package github.aeonbtc.ibiswallet.data

import github.aeonbtc.ibiswallet.util.AppVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class AppUpdateServiceTest : FunSpec({
    test("allows beta releases when not marked prerelease") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v4.1.1-beta",
                    "html_url": "https://example.com/411beta",
                    "draft": false,
                    "prerelease": false
                  },
                  {
                    "tag_name": "v4.1.0",
                    "html_url": "https://example.com/410",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v4.1.1-beta"
        latest.htmlUrl shouldBe "https://example.com/411beta"
    }

    test("picks the newest stable release and ignores prereleases") {
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

        latest.versionName shouldBe "v3.2.0"
        latest.htmlUrl shouldBe "https://example.com/320"
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

    test("returns null when only prereleases exist") {
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
            )

        latest shouldBe null
    }

    test("ignores prerelease tags even if GitHub prerelease flag is false") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v3.2.1-rc.1",
                    "html_url": "https://example.com/rc",
                    "draft": false,
                    "prerelease": false
                  },
                  {
                    "tag_name": "v3.2.0",
                    "html_url": "https://example.com/stable",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v3.2.0"
        latest.htmlUrl shouldBe "https://example.com/stable"
    }

    test("ignores beta tags when GitHub marks them as prerelease") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v4.1.1-beta",
                    "html_url": "https://example.com/411beta",
                    "draft": false,
                    "prerelease": true
                  },
                  {
                    "tag_name": "v4.1.0",
                    "html_url": "https://example.com/410",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v4.1.0"
        latest.htmlUrl shouldBe "https://example.com/410"
    }

    test("prefers stable 1.x over newer legacy beta versions during migration") {
        val latest =
            AppUpdateService.parseLatestRelease(
                json =
                    """
                    [
                      {
                        "tag_name": "v4.4-beta",
                        "html_url": "https://example.com/44beta",
                        "draft": false,
                        "prerelease": false
                      },
                      {
                        "tag_name": "v1.0",
                        "html_url": "https://example.com/10",
                        "draft": false,
                        "prerelease": false
                      }
                    ]
                    """.trimIndent(),
                currentVersion = AppVersion.parse("v4.3-beta"),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v1.0"
        latest.htmlUrl shouldBe "https://example.com/10"
    }

    test("ignores legacy beta releases after migrating to stable 1.x") {
        val latest =
            AppUpdateService.parseLatestRelease(
                json =
                    """
                    [
                      {
                        "tag_name": "v4.4-beta",
                        "html_url": "https://example.com/44beta",
                        "draft": false,
                        "prerelease": false
                      }
                    ]
                    """.trimIndent(),
                currentVersion = AppVersion.parse("v1.0"),
            )

        latest shouldBe null
    }
})
