package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppUpdateInstallerTest : FunSpec({
    test("higher versionCode is newer") {
        isNewerBuild(
            archiveVersionCode = 24,
            archiveVersionName = "5.1.0-beta",
            installedVersionCode = 23,
            installedVersionName = "5.0.1-beta",
        ) shouldBe true
    }

    test("lower versionCode is never newer even with a newer versionName") {
        isNewerBuild(
            archiveVersionCode = 23,
            archiveVersionName = "5.1.0-beta",
            installedVersionCode = 24,
            installedVersionName = "5.0.1-beta",
        ) shouldBe false
    }

    test("equal versionCode is newer when the versionName is newer") {
        isNewerBuild(
            archiveVersionCode = 24,
            archiveVersionName = "5.1.0-beta",
            installedVersionCode = 24,
            installedVersionName = "5.0.1-beta",
        ) shouldBe true
    }

    test("equal versionCode and equal versionName is not newer") {
        isNewerBuild(
            archiveVersionCode = 24,
            archiveVersionName = "5.1.0-beta",
            installedVersionCode = 24,
            installedVersionName = "5.1-beta",
        ) shouldBe false
    }

    test("equal versionCode and older versionName is not newer") {
        isNewerBuild(
            archiveVersionCode = 24,
            archiveVersionName = "5.0.1-beta",
            installedVersionCode = 24,
            installedVersionName = "5.1.0-beta",
        ) shouldBe false
    }

    test("equal versionCode with unparseable version names is not newer") {
        isNewerBuild(
            archiveVersionCode = 24,
            archiveVersionName = null,
            installedVersionCode = 24,
            installedVersionName = "dev",
        ) shouldBe false
    }
})
