package github.aeonbtc.ibiswallet.data.lightning

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient

class TlsCertMaterialTest :
    StringSpec(
        {
            "skips hostname verification for IP and onion" {
                TlsCertMaterial.shouldSkipHostnameVerification("192.168.1.10") shouldBe true
                TlsCertMaterial.shouldSkipHostnameVerification("abc.onion") shouldBe true
                TlsCertMaterial.shouldSkipHostnameVerification("localhost") shouldBe true
                TlsCertMaterial.shouldSkipHostnameVerification("node.example.com") shouldBe false
            }

            "rejects non-blank unparsable cert material" {
                shouldThrow<IllegalArgumentException> {
                    TlsCertMaterial.applyToOkHttp(
                        OkHttpClient.Builder(),
                        "not-a-certificate",
                        "node.example.com",
                    )
                }
            }
        },
    )
