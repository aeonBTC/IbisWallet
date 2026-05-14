package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class AppVersionTest : FunSpec({
    test("parses a stable version with leading v") {
        val version = AppVersion.parse("v3.2.0").shouldNotBeNull()

        version.isStable shouldBe true
        version.isStableOrBeta shouldBe true
    }

    test("treats beta releases as supported updater targets") {
        val version = AppVersion.parse("4.1.1-beta").shouldNotBeNull()

        version.isStable shouldBe false
        version.isBeta shouldBe true
        version.isStableOrBeta shouldBe true
    }

    test("does not treat rc releases as supported updater targets") {
        val version = AppVersion.parse("4.1.1-rc.1").shouldNotBeNull()

        version.isBeta shouldBe false
        version.isStableOrBeta shouldBe false
    }

    test("stable release outranks matching beta version") {
        val beta = AppVersion.parse("3.1.3-beta").shouldNotBeNull()
        val stable = AppVersion.parse("3.1.3").shouldNotBeNull()

        stable shouldBeGreaterThan beta
    }

    test("higher numeric version outranks lower numeric version") {
        val older = AppVersion.parse("3.1.9").shouldNotBeNull()
        val newer = AppVersion.parse("3.2.0").shouldNotBeNull()

        newer shouldBeGreaterThan older
    }

    test("prerelease identifiers compare in semver order") {
        val beta = AppVersion.parse("3.2.0-beta").shouldNotBeNull()
        val rc = AppVersion.parse("3.2.0-rc.1").shouldNotBeNull()

        beta shouldBeLessThan rc
    }

    test("build metadata does not change precedence") {
        val plain = AppVersion.parse("3.2.0").shouldNotBeNull()
        val withBuild = AppVersion.parse("3.2.0+45").shouldNotBeNull()

        plain shouldBe withBuild
    }
})
