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
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/411beta",
                    "draft": false,
                    "prerelease": false
                  },
                  {
                    "tag_name": "v4.1.0",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/410",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v4.1.1-beta"
        latest.htmlUrl shouldBe "https://github.com/aeonBTC/IbisWallet/releases/tag/411beta"
    }

    test("picks the newest stable release and ignores prereleases") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v3.2.1-beta",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/beta",
                    "draft": false,
                    "prerelease": true
                  },
                  {
                    "tag_name": "v3.2.0",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/320",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v3.2.0"
        latest.htmlUrl shouldBe "https://github.com/aeonBTC/IbisWallet/releases/tag/320"
    }

    test("ignores draft releases even when numerically newer") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v3.2.1",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/draft",
                    "draft": true,
                    "prerelease": false
                  },
                  {
                    "tag_name": "v3.2.0",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/live",
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
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/beta1",
                    "draft": false,
                    "prerelease": true
                  },
                  {
                    "tag_name": "v3.1.2-beta",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/beta2",
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
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/rc",
                    "draft": false,
                    "prerelease": false
                  },
                  {
                    "tag_name": "v3.2.0",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/stable",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v3.2.0"
        latest.htmlUrl shouldBe "https://github.com/aeonBTC/IbisWallet/releases/tag/stable"
    }

    test("ignores beta tags when GitHub marks them as prerelease") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v4.1.1-beta",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/411beta",
                    "draft": false,
                    "prerelease": true
                  },
                  {
                    "tag_name": "v4.1.0",
                    "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/410",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v4.1.0"
        latest.htmlUrl shouldBe "https://github.com/aeonBTC/IbisWallet/releases/tag/410"
    }

    test("prefers stable 1.x over newer legacy beta versions during migration") {
        val latest =
            AppUpdateService.parseLatestRelease(
                json =
                    """
                    [
                      {
                        "tag_name": "v4.4-beta",
                        "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/44beta",
                        "draft": false,
                        "prerelease": false
                      },
                      {
                        "tag_name": "v1.0",
                        "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/10",
                        "draft": false,
                        "prerelease": false
                      }
                    ]
                    """.trimIndent(),
                currentVersion = AppVersion.parse("v4.3-beta"),
            ).shouldNotBeNull()

        latest.versionName shouldBe "v1.0"
        latest.htmlUrl shouldBe "https://github.com/aeonBTC/IbisWallet/releases/tag/10"
    }

    test("ignores legacy beta releases after migrating to stable 1.x") {
        val latest =
            AppUpdateService.parseLatestRelease(
                json =
                    """
                    [
                      {
                        "tag_name": "v4.4-beta",
                        "html_url": "https://github.com/aeonBTC/IbisWallet/releases/tag/44beta",
                        "draft": false,
                        "prerelease": false
                      }
                    ]
                    """.trimIndent(),
                currentVersion = AppVersion.parse("v1.0"),
            )

        latest shouldBe null
    }

    test("falls back to the hardcoded releases page for non-github URLs") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v4.1.0",
                    "html_url": "https://phishing.example.com/fake-release",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.htmlUrl shouldBe "https://github.com/aeonBTC/IbisWallet/releases"
    }

    test("falls back to the hardcoded releases page for non-https URLs") {
        val latest =
            AppUpdateService.parseLatestRelease(
                """
                [
                  {
                    "tag_name": "v4.1.0",
                    "html_url": "http://github.com/aeonBTC/IbisWallet/releases/tag/v4.1.0",
                    "draft": false,
                    "prerelease": false
                  }
                ]
                """.trimIndent(),
            ).shouldNotBeNull()

        latest.htmlUrl shouldBe "https://github.com/aeonBTC/IbisWallet/releases"
    }
})
