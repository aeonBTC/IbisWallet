package github.aeonbtc.ibiswallet.ui.screens

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SettingsSectionSessionStateTest : FunSpec({
    test("recreating Settings after Back retains an expanded section") {
        val session = SettingsSectionSessionState()
        session.expandedState("layer1_display").value = true

        // A new screen composition obtains its state from the same app-session owner.
        session.expandedState("layer1_display").value shouldBe true
    }

    test("explicitly collapsing a section stays collapsed on returning") {
        val session = SettingsSectionSessionState()
        session.expandedState("layer1_display").value = true
        session.expandedState("layer1_display").value = false

        session.expandedState("layer1_display").value shouldBe false
    }

    test("different section ids have independent state") {
        val session = SettingsSectionSessionState()
        session.expandedState("section_a").value = true

        session.expandedState("section_b").value shouldBe false
    }

    test("a new app session starts with collapsed sections") {
        val previousSession = SettingsSectionSessionState()
        previousSession.expandedState("layer1_display").value = true

        SettingsSectionSessionState().expandedState("layer1_display").value shouldBe false
    }
})
