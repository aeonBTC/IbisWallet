package github.aeonbtc.ibiswallet.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

class TofuTrustManagerTest : FunSpec({

    // Helper to create a mock X509Certificate with specific DER encoding
    fun mockCert(
        derBytes: ByteArray = "test-cert-data".toByteArray(),
        subject: String = "CN=Test",
        issuer: String = "CN=Issuer",
    ): X509Certificate {
        val cert = mockk<X509Certificate>(relaxed = true)
        every { cert.encoded } returns derBytes
        every { cert.subjectX500Principal } returns X500Principal(subject)
        every { cert.issuerX500Principal } returns X500Principal(issuer)
        return cert
    }

    fun expectedFingerprint(derBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(derBytes)
        return hash.joinToString(":") { "%02X".format(it) }
    }

    // ── computeFingerprint ──

    context("computeFingerprint") {
        test("produces colon-separated SHA-256 hex") {
            val derBytes = "test-cert-data".toByteArray()
            val cert = mockCert(derBytes)
            val fp = TofuTrustManager.computeFingerprint(cert)
            fp shouldBe expectedFingerprint(derBytes)
        }

        test("fingerprint has 64 hex chars + 31 colons = 95 chars") {
            val cert = mockCert()
            val fp = TofuTrustManager.computeFingerprint(cert)
            fp.replace(":", "").length shouldBe 64
            fp.count { it == ':' } shouldBe 31
        }

        test("different certs produce different fingerprints") {
            val cert1 = mockCert(derBytes = "cert-one".toByteArray())
            val cert2 = mockCert(derBytes = "cert-two".toByteArray())
            TofuTrustManager.computeFingerprint(cert1) shouldNotBe
                TofuTrustManager.computeFingerprint(cert2)
        }
    }

    // ── .onion bypass ──

    context("onion host bypass") {
        test("accepts any certificate for .onion host") {
            val cert = mockCert()
            val tm = TofuTrustManager(
                host = "abc123def456.onion",
                port = 50002,
                storedFingerprint = null,
            )
            // Should not throw
            tm.checkServerTrusted(arrayOf(cert), "RSA")
            tm.presentedCertInfo shouldNotBe null
            tm.presentedCertInfo?.host shouldBe "abc123def456.onion"
        }

        test("accepts mismatched fingerprint for .onion host") {
            val cert = mockCert()
            val tm = TofuTrustManager(
                host = "abc123def456.onion",
                port = 50002,
                storedFingerprint = "AA:BB:CC:DD", // wrong fingerprint
            )
            // Should not throw — Tor provides transport auth
            tm.checkServerTrusted(arrayOf(cert), "RSA")
        }
    }

    // ── First use (no stored fingerprint) ──

    context("first use") {
        test("throws CertificateFirstUseException when no stored fingerprint") {
            val derBytes = "first-use-cert".toByteArray()
            val cert = mockCert(derBytes)
            val tm = TofuTrustManager(
                host = "electrum.example.com",
                port = 50002,
                storedFingerprint = null,
            )

            val ex = shouldThrow<CertificateFirstUseException> {
                tm.checkServerTrusted(arrayOf(cert), "RSA")
            }
            ex.certInfo.host shouldBe "electrum.example.com"
            ex.certInfo.port shouldBe 50002
            ex.certInfo.sha256Fingerprint shouldBe expectedFingerprint(derBytes)
        }
    }

    // ── Fingerprint match ──

    context("fingerprint match") {
        test("accepts silently when fingerprint matches") {
            val derBytes = "matching-cert".toByteArray()
            val cert = mockCert(derBytes)
            val fp = expectedFingerprint(derBytes)
            val tm = TofuTrustManager(
                host = "electrum.example.com",
                port = 50002,
                storedFingerprint = fp,
            )

            // Should not throw
            tm.checkServerTrusted(arrayOf(cert), "RSA")
            tm.presentedCertInfo shouldNotBe null
        }
    }

    // ── Fingerprint mismatch ──

    context("fingerprint mismatch") {
        test("throws CertificateMismatchException when fingerprint differs") {
            val derBytes = "new-cert".toByteArray()
            val cert = mockCert(derBytes)
            val oldFingerprint = "AA:BB:CC:DD:EE:FF"
            val tm = TofuTrustManager(
                host = "electrum.example.com",
                port = 50002,
                storedFingerprint = oldFingerprint,
            )

            val ex = shouldThrow<CertificateMismatchException> {
                tm.checkServerTrusted(arrayOf(cert), "RSA")
            }
            ex.host shouldBe "electrum.example.com"
            ex.port shouldBe 50002
            ex.storedFingerprint shouldBe oldFingerprint
            ex.certInfo.sha256Fingerprint shouldBe expectedFingerprint(derBytes)
        }
    }

    // ── Empty chain ──

    context("empty certificate chain") {
        test("throws CertificateException for empty chain") {
            val tm = TofuTrustManager(
                host = "example.com",
                port = 50002,
                storedFingerprint = null,
            )
            shouldThrow<CertificateException> {
                tm.checkServerTrusted(emptyArray(), "RSA")
            }
        }
    }

    // ── CertificateInfo population ──

    context("presentedCertInfo") {
        test("stores cert info after check") {
            val derBytes = "test-cert".toByteArray()
            val cert = mockCert(
                derBytes = derBytes,
                subject = "CN=MyServer",
                issuer = "CN=MyCA",
            )
            val fp = expectedFingerprint(derBytes)
            val tm = TofuTrustManager(
                host = "server.com",
                port = 443,
                storedFingerprint = fp,
            )

            tm.checkServerTrusted(arrayOf(cert), "RSA")

            val info = tm.presentedCertInfo!!
            info.host shouldBe "server.com"
            info.port shouldBe 443
            info.sha256Fingerprint shouldBe fp
            info.subject shouldBe "CN=MyServer"
            info.issuer shouldBe "CN=MyCA"
        }
    }

    // ── getAcceptedIssuers ──

    context("getAcceptedIssuers") {
        test("returns empty array") {
            val tm = TofuTrustManager("host", 443, null)
            tm.acceptedIssuers.size shouldBe 0
        }
    }
})
