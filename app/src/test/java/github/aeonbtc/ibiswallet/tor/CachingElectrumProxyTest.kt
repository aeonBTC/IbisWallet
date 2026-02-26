package github.aeonbtc.ibiswallet.tor

import github.aeonbtc.ibiswallet.data.local.ElectrumCache
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Unit tests for CachingElectrumProxy.
 *
 * Strategy:
 * - Pure data classes (ElectrumNotification, TxDetails, AddressTxInfo) — direct instantiation
 * - Cache interception logic (tryServFromCache, trackTxGetRequest, tryCacheServerResponse) —
 *   exercised indirectly via a real loopback server socket that echoes/responds to requests
 * - isServerPushNotification / dispatchPushNotification — exercised via the subscription
 *   socket listener using a controlled loopback server
 * - parseVerboseTxDetails / getAddressTxInfo — tested by pre-populating a mocked cache
 *   with verbose JSON so no network is needed
 * - DEFAULT_MIN_FEE_RATE constant
 * - start() / stop() lifecycle — real ServerSocket on localhost:0
 */
class CachingElectrumProxyTest : FunSpec({

    // ─── Mock android.util.Log before any test runs ───────────────────────────
    beforeSpec {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
    }

    afterEach {
        clearAllMocks(answers = false) // keep stubbing, clear recorded calls
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. ElectrumNotification sealed class
    // ═══════════════════════════════════════════════════════════════════════════
    context("ElectrumNotification data classes") {

        test("ScriptHashChanged holds scriptHash and status") {
            val n = ElectrumNotification.ScriptHashChanged("abc123", "deadbeef")
            n.scriptHash shouldBe "abc123"
            n.status shouldBe "deadbeef"
        }

        test("ScriptHashChanged with null status") {
            val n = ElectrumNotification.ScriptHashChanged("hash", null)
            n.status.shouldBeNull()
        }

        test("ScriptHashChanged equality") {
            val a = ElectrumNotification.ScriptHashChanged("h", "s")
            val b = ElectrumNotification.ScriptHashChanged("h", "s")
            a shouldBe b
        }

        test("ScriptHashChanged inequality when scriptHash differs") {
            val a = ElectrumNotification.ScriptHashChanged("h1", "s")
            val b = ElectrumNotification.ScriptHashChanged("h2", "s")
            (a == b).shouldBeFalse()
        }

        test("NewBlockHeader holds height and hexHeader") {
            val n = ElectrumNotification.NewBlockHeader(800_000, "deadbeef00")
            n.height shouldBe 800_000
            n.hexHeader shouldBe "deadbeef00"
        }

        test("NewBlockHeader equality") {
            val a = ElectrumNotification.NewBlockHeader(1, "ff")
            val b = ElectrumNotification.NewBlockHeader(1, "ff")
            a shouldBe b
        }

        test("ConnectionLost is a singleton data object") {
            val a: ElectrumNotification = ElectrumNotification.ConnectionLost
            val b: ElectrumNotification = ElectrumNotification.ConnectionLost
            (a === b).shouldBeTrue()
        }

        test("Sealed class subtypes are distinct") {
            val changed: ElectrumNotification = ElectrumNotification.ScriptHashChanged("h", null)
            val block: ElectrumNotification = ElectrumNotification.NewBlockHeader(1, "")
            val lost: ElectrumNotification = ElectrumNotification.ConnectionLost
            (changed is ElectrumNotification.ScriptHashChanged).shouldBeTrue()
            (block is ElectrumNotification.NewBlockHeader).shouldBeTrue()
            (lost is ElectrumNotification.ConnectionLost).shouldBeTrue()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. TxDetails data class
    // ═══════════════════════════════════════════════════════════════════════════
    context("TxDetails data class") {

        test("holds txid, size, vsize, weight") {
            val td = CachingElectrumProxy.TxDetails("txid1", 300, 150, 600)
            td.txid shouldBe "txid1"
            td.size shouldBe 300
            td.vsize shouldBe 150
            td.weight shouldBe 600
        }

        test("equality") {
            val a = CachingElectrumProxy.TxDetails("t", 1, 1, 4)
            val b = CachingElectrumProxy.TxDetails("t", 1, 1, 4)
            a shouldBe b
        }

        test("copy produces independent instance") {
            val original = CachingElectrumProxy.TxDetails("orig", 200, 100, 400)
            val copy = original.copy(vsize = 99)
            copy.vsize shouldBe 99
            original.vsize shouldBe 100
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. AddressTxInfo data class
    // ═══════════════════════════════════════════════════════════════════════════
    context("AddressTxInfo data class") {

        test("positive netAmountSats means received") {
            val info = CachingElectrumProxy.AddressTxInfo(100_000L, 1_700_000_000L)
            info.netAmountSats shouldBe 100_000L
            info.timestamp shouldBe 1_700_000_000L
        }

        test("negative netAmountSats means spent") {
            val info = CachingElectrumProxy.AddressTxInfo(-50_000L, null)
            info.netAmountSats shouldBe -50_000L
            info.timestamp.shouldBeNull()
        }

        test("counterpartyAddress defaults to null") {
            val info = CachingElectrumProxy.AddressTxInfo(0L, null)
            info.counterpartyAddress.shouldBeNull()
        }

        test("feeSats defaults to null") {
            val info = CachingElectrumProxy.AddressTxInfo(0L, null)
            info.feeSats.shouldBeNull()
        }

        test("full construction") {
            val info = CachingElectrumProxy.AddressTxInfo(
                netAmountSats = -10_000L,
                timestamp = 1_700_000_001L,
                counterpartyAddress = "bc1qrecipient",
                feeSats = 500L,
            )
            info.counterpartyAddress shouldBe "bc1qrecipient"
            info.feeSats shouldBe 500L
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. DEFAULT_MIN_FEE_RATE constant
    // ═══════════════════════════════════════════════════════════════════════════
    context("Constants") {

        test("DEFAULT_MIN_FEE_RATE is 1.0 sat/vByte") {
            CachingElectrumProxy.DEFAULT_MIN_FEE_RATE shouldBe 1.0
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. Cache interception — tryServFromCache via black-box approach
    //    We exercise it through the proxy's bridge by injecting a mock cache.
    // ═══════════════════════════════════════════════════════════════════════════
    context("Cache interception logic (tryServFromCache / tryCacheServerResponse)") {

        /**
         * Helper: build a proxy backed by a mock cache with no real network target.
         * We don't call start() — we test the cache logic by calling parseVerboseTxDetails
         * and getAddressTxInfo which use the verbose cache path.
         */
        fun proxyWithCache(cache: ElectrumCache) = CachingElectrumProxy(
            targetHost = "127.0.0.1",
            targetPort = 1,      // invalid port — never actually connected in these tests
            cache = cache,
        )

        test("getTransactionDetails returns null when cache miss and no network") {
            val cache = mockk<ElectrumCache>()
            every { cache.getVerboseTx(any()) } returns null
            val proxy = proxyWithCache(cache)
            // No direct connection so getVerboseTxJson will fail fast
            val result = proxy.getTransactionDetails("nonexistenttxid")
            result.shouldBeNull()
        }

        test("getTransactionDetails uses cached verbose JSON (cache hit)") {
            val cache = mockk<ElectrumCache>()
            val verboseJson = """{"size":300,"vsize":150,"weight":600}"""
            every { cache.getVerboseTx("txABC") } returns verboseJson
            val proxy = proxyWithCache(cache)
            val result = proxy.getTransactionDetails("txABC")
            result.shouldNotBeNull()
            result!!.txid shouldBe "txABC"
            result.size shouldBe 300
            result.vsize shouldBe 150
            result.weight shouldBe 600
        }

        test("getTransactionDetails: vsize derived from weight when vsize absent") {
            val cache = mockk<ElectrumCache>()
            // weight=400, no vsize → calculatedVsize = (400+3)/4 = 100
            val verboseJson = """{"size":200,"weight":400}"""
            every { cache.getVerboseTx("txW") } returns verboseJson
            val proxy = proxyWithCache(cache)
            val result = proxy.getTransactionDetails("txW")
            result.shouldNotBeNull()
            result!!.vsize shouldBe 100          // (400+3)/4
            result.weight shouldBe 400
        }

        test("getTransactionDetails: vsize derived from size when weight and vsize absent") {
            val cache = mockk<ElectrumCache>()
            val verboseJson = """{"size":250}"""
            every { cache.getVerboseTx("txS") } returns verboseJson
            val proxy = proxyWithCache(cache)
            val result = proxy.getTransactionDetails("txS")
            result.shouldNotBeNull()
            result!!.vsize shouldBe 250
            result.size shouldBe 250
        }

        test("getTransactionDetails returns null when JSON has no size/vsize/weight") {
            val cache = mockk<ElectrumCache>()
            every { cache.getVerboseTx("txEmpty") } returns """{"txid":"txEmpty"}"""
            val proxy = proxyWithCache(cache)
            val result = proxy.getTransactionDetails("txEmpty")
            result.shouldBeNull()
        }

        test("getTransactionDetails: vsize takes priority over weight") {
            val cache = mockk<ElectrumCache>()
            val verboseJson = """{"size":400,"vsize":180,"weight":720}"""
            every { cache.getVerboseTx("txPriority") } returns verboseJson
            val proxy = proxyWithCache(cache)
            val result = proxy.getTransactionDetails("txPriority")
            result.shouldNotBeNull()
            result!!.vsize shouldBe 180   // vsize present → used directly
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. getAddressTxInfo — pure JSON calculation logic via mocked verbose cache
    // ═══════════════════════════════════════════════════════════════════════════
    context("getAddressTxInfo — address amount calculations") {

        val myAddress = "bc1qmyaddress"

        fun proxyWithVerboseCache(txid: String, verboseJson: String): CachingElectrumProxy {
            val cache = mockk<ElectrumCache>()
            every { cache.getVerboseTx(txid) } returns verboseJson
            // any other txid → null (for prevout lookups)
            every { cache.getVerboseTx(neq(txid)) } returns null
            return CachingElectrumProxy("127.0.0.1", 1, cache = cache)
        }

        test("simple receive: one output to my address") {
            val verboseJson = """
            {
              "blocktime": 1700000000,
              "vout": [
                {
                  "value": 0.001,
                  "scriptPubKey": { "address": "$myAddress" }
                }
              ],
              "vin": []
            }
            """.trimIndent()
            val proxy = proxyWithVerboseCache("txRecv", verboseJson)
            val info = proxy.getAddressTxInfo("txRecv", myAddress)
            info.shouldNotBeNull()
            info!!.netAmountSats shouldBe 100_000L   // 0.001 BTC = 100,000 sats
            info.timestamp shouldBe 1700000000L
            info.counterpartyAddress.shouldBeNull()  // receive → no counterparty
        }

        test("receive: multiple outputs, only some to my address") {
            val verboseJson = """
            {
              "blocktime": 1700000001,
              "vout": [
                { "value": 0.005, "scriptPubKey": { "address": "$myAddress" } },
                { "value": 0.095, "scriptPubKey": { "address": "bc1qother" } }
              ],
              "vin": []
            }
            """.trimIndent()
            val proxy = proxyWithVerboseCache("txMultiOut", verboseJson)
            val info = proxy.getAddressTxInfo("txMultiOut", myAddress)
            info.shouldNotBeNull()
            info!!.netAmountSats shouldBe 500_000L   // only 0.005 BTC credited
        }

        test("send: input from my address via prevout") {
            val verboseJson = """
            {
              "time": 1700000002,
              "vout": [
                { "value": 0.009, "scriptPubKey": { "address": "bc1qrecipient" } },
                { "value": 0.0005, "scriptPubKey": { "address": "$myAddress" } }
              ],
              "vin": [
                {
                  "prevout": {
                    "value": 0.01,
                    "scriptPubKey": { "address": "$myAddress" }
                  }
                }
              ]
            }
            """.trimIndent()
            val proxy = proxyWithVerboseCache("txSend", verboseJson)
            val info = proxy.getAddressTxInfo("txSend", myAddress)
            info.shouldNotBeNull()
            // received 0.0005, spent 0.01 → net = 0.0005 - 0.01 = -0.0095 BTC = -950,000 sats
            info!!.netAmountSats shouldBe -950_000L
            info.timestamp shouldBe 1700000002L
            // counterparty: first vout NOT to my address = bc1qrecipient
            info.counterpartyAddress shouldBe "bc1qrecipient"
        }

        test("timestamp: blocktime preferred over time") {
            val verboseJson = """
            {
              "blocktime": 1700000100,
              "time": 1700000050,
              "vout": [
                { "value": 0.001, "scriptPubKey": { "address": "$myAddress" } }
              ],
              "vin": []
            }
            """.trimIndent()
            val proxy = proxyWithVerboseCache("txTs", verboseJson)
            val info = proxy.getAddressTxInfo("txTs", myAddress)
            info.shouldNotBeNull()
            info!!.timestamp shouldBe 1700000100L   // blocktime wins
        }

        test("timestamp: falls back to time when blocktime is 0") {
            val verboseJson = """
            {
              "blocktime": 0,
              "time": 1700000099,
              "vout": [
                { "value": 0.001, "scriptPubKey": { "address": "$myAddress" } }
              ],
              "vin": []
            }
            """.trimIndent()
            val proxy = proxyWithVerboseCache("txTs2", verboseJson)
            val info = proxy.getAddressTxInfo("txTs2", myAddress)
            info.shouldNotBeNull()
            info!!.timestamp shouldBe 1700000099L
        }

        test("timestamp is null when both blocktime and time are absent") {
            val verboseJson = """
            {
              "vout": [
                { "value": 0.001, "scriptPubKey": { "address": "$myAddress" } }
              ],
              "vin": []
            }
            """.trimIndent()
            val proxy = proxyWithVerboseCache("txNoTs", verboseJson)
            val info = proxy.getAddressTxInfo("txNoTs", myAddress)
            info.shouldNotBeNull()
            info!!.timestamp.shouldBeNull()
        }

        test("coinbase input is ignored for spend calculation") {
            val verboseJson = """
            {
              "blocktime": 1700000000,
              "vout": [
                { "value": 6.25, "scriptPubKey": { "address": "$myAddress" } }
              ],
              "vin": [
                { "coinbase": "0400000000" }
              ]
            }
            """.trimIndent()
            val proxy = proxyWithVerboseCache("txCoinbase", verboseJson)
            val info = proxy.getAddressTxInfo("txCoinbase", myAddress)
            info.shouldNotBeNull()
            info!!.netAmountSats shouldBe 625_000_000L   // block reward
        }

        test("getAddressTxInfo returns null when txid not in cache and no network") {
            val cache = mockk<ElectrumCache>()
            every { cache.getVerboseTx(any()) } returns null
            val proxy = CachingElectrumProxy("127.0.0.1", 1, cache = cache)
            val info = proxy.getAddressTxInfo("missingtxid", myAddress)
            info.shouldBeNull()
        }

        test("fee calculation: inputs sum - outputs sum (prevout path)") {
            val verboseJson = """
            {
              "blocktime": 1700000000,
              "vout": [
                { "value": 0.009, "scriptPubKey": { "address": "bc1qrecipient" } },
                { "value": 0.0009, "scriptPubKey": { "address": "$myAddress" } }
              ],
              "vin": [
                {
                  "prevout": {
                    "value": 0.01,
                    "scriptPubKey": { "address": "$myAddress" }
                  }
                }
              ]
            }
            """.trimIndent()
            // total vin = 0.01 BTC; total vout = 0.0099 BTC; fee = 0.0001 BTC = 10,000 sats
            val proxy = proxyWithVerboseCache("txFee", verboseJson)
            val info = proxy.getAddressTxInfo("txFee", myAddress)
            info.shouldNotBeNull()
            info!!.feeSats shouldBe 10_000L
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 7. isServerPushNotification — tested via loopback proxy interaction
    //    We test this indirectly by verifying dispatchPushNotification emits
    //    the correct ElectrumNotification variants.
    // ═══════════════════════════════════════════════════════════════════════════
    context("isServerPushNotification JSON detection") {

        /**
         * Directly exercise the push notification dispatch path by calling
         * the notifications SharedFlow. We spin up a minimal fake server that
         * completes the handshake and then sends a push notification.
         *
         * Uses a CountDownLatch to ensure the fake server waits until the
         * proxy's notification listener coroutine is running before sending
         * the push notification.
         */
        test("ScriptHashChanged push notification emitted from loopback server") {
            val latchServerReadyForPush = java.util.concurrent.CountDownLatch(1)

            val serverSocket = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            val fakeServerPort = serverSocket.localPort

            val serverThread = Thread {
                try {
                    val client = serverSocket.accept()
                    client.soTimeout = 8_000
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val writer = PrintWriter(client.getOutputStream(), true)

                    // 1. Version handshake
                    val versionReq = reader.readLine() ?: return@Thread
                    val versionId = JSONObject(versionReq).optInt("id", 200000)
                    writer.println("""{"jsonrpc":"2.0","id":$versionId,"result":["ElectrumX 1.16.0","1.4"]}""")

                    // 2. headers.subscribe response
                    val headersReq = reader.readLine() ?: return@Thread
                    val headersId = JSONObject(headersReq).optInt("id", 200001)
                    writer.println("""{"jsonrpc":"2.0","id":$headersId,"result":{"height":850000,"hex":"deadbeef"}}""")

                    // Wait for test to signal the listener is running
                    latchServerReadyForPush.await(5, java.util.concurrent.TimeUnit.SECONDS)

                    // 3. Send ScriptHashChanged push notification
                    writer.println("""{"method":"blockchain.scripthash.subscribe","params":["abc123hash","newstatus456"],"jsonrpc":"2.0"}""")

                    Thread.sleep(3000) // Keep connection alive while test reads
                    client.close()
                } catch (_: Exception) {
                } finally {
                    serverSocket.close()
                }
            }
            serverThread.isDaemon = true
            serverThread.start()

            val proxy = CachingElectrumProxy(
                targetHost = "127.0.0.1",
                targetPort = fakeServerPort,
                useSsl = false,
            )

            // Collect notifications in background thread before starting subscriptions
            var receivedNotification: ElectrumNotification? = null
            val collectorThread = Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                            proxy.notifications.collect { n ->
                                if (n is ElectrumNotification.ScriptHashChanged) {
                                    receivedNotification = n
                                    throw kotlinx.coroutines.CancellationException("found")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            collectorThread.isDaemon = true
            collectorThread.start()
            Thread.sleep(150) // Let collector subscribe to the SharedFlow

            // start() sets isRunning=true and opens the proxy server socket.
            // isRunning MUST be true for the notification listener while-loop to run.
            proxy.start()

            try {
                // startSubscriptions blocks until handshake + header response are done,
                // then launches the listener coroutine and returns
                proxy.startSubscriptions(emptyList())

                // Give the listener coroutine time to start and call readLine()
                Thread.sleep(500)

                // Signal fake server: listener is now running, send the push
                latchServerReadyForPush.countDown()

                // Wait for collector to receive the push notification
                collectorThread.join(8_000)

                receivedNotification.shouldNotBeNull()
                val changed = receivedNotification as ElectrumNotification.ScriptHashChanged
                changed.scriptHash shouldBe "abc123hash"
                changed.status shouldBe "newstatus456"
            } finally {
                latchServerReadyForPush.countDown() // unblock server if test failed early
                proxy.stop()
                serverThread.join(2_000)
            }
        }

        test("NewBlockHeader push notification emitted from loopback server") {
            val latchServerReadyForPush = java.util.concurrent.CountDownLatch(1)

            val serverSocket = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            val fakeServerPort = serverSocket.localPort

            val serverThread = Thread {
                try {
                    val client = serverSocket.accept()
                    client.soTimeout = 8_000
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val writer = PrintWriter(client.getOutputStream(), true)

                    // 1. Version handshake
                    val versionReq = reader.readLine() ?: return@Thread
                    val versionId = JSONObject(versionReq).optInt("id", 200000)
                    writer.println("""{"jsonrpc":"2.0","id":$versionId,"result":["ElectrumX 1.16.0","1.4"]}""")

                    // 2. headers.subscribe response
                    val headersReq = reader.readLine() ?: return@Thread
                    val headersId = JSONObject(headersReq).optInt("id", 200001)
                    writer.println("""{"jsonrpc":"2.0","id":$headersId,"result":{"height":850000,"hex":"aabb"}}""")

                    // Wait for test to signal the listener is running
                    latchServerReadyForPush.await(5, java.util.concurrent.TimeUnit.SECONDS)

                    // 3. Send NewBlockHeader push notification
                    writer.println("""{"method":"blockchain.headers.subscribe","params":[{"height":850001,"hex":"ccdd"}],"jsonrpc":"2.0"}""")

                    Thread.sleep(3000)
                    client.close()
                } catch (_: Exception) {
                } finally {
                    serverSocket.close()
                }
            }
            serverThread.isDaemon = true
            serverThread.start()

            val proxy = CachingElectrumProxy(
                targetHost = "127.0.0.1",
                targetPort = fakeServerPort,
                useSsl = false,
            )

            // Collect in background, looking only for height=850001 (not the initial 850000)
            var receivedBlock: ElectrumNotification.NewBlockHeader? = null
            val collectorThread = Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                            proxy.notifications.collect { n ->
                                if (n is ElectrumNotification.NewBlockHeader && n.height == 850001) {
                                    receivedBlock = n
                                    throw kotlinx.coroutines.CancellationException("found")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            collectorThread.isDaemon = true
            collectorThread.start()
            Thread.sleep(150) // Let collector subscribe

            // start() is required to set isRunning=true for the notification listener loop
            proxy.start()

            try {
                proxy.startSubscriptions(emptyList())

                Thread.sleep(500) // Give listener coroutine time to start and block on readLine()
                latchServerReadyForPush.countDown()

                collectorThread.join(8_000)

                receivedBlock.shouldNotBeNull()
                receivedBlock!!.height shouldBe 850001
                receivedBlock!!.hexHeader shouldBe "ccdd"
            } finally {
                latchServerReadyForPush.countDown()
                proxy.stop()
                serverThread.join(2_000)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 8. Lifecycle — start() and stop()
    // ═══════════════════════════════════════════════════════════════════════════
    context("Lifecycle: start() and stop()") {

        test("start() returns a positive local port") {
            val proxy = CachingElectrumProxy("127.0.0.1", 9999)
            val port = proxy.start()
            try {
                (port > 0).shouldBeTrue()
                (port <= 65535).shouldBeTrue()
            } finally {
                proxy.stop()
            }
        }

        test("start() twice returns the same port (idempotent)") {
            val proxy = CachingElectrumProxy("127.0.0.1", 9999)
            val port1 = proxy.start()
            val port2 = proxy.start()   // already running
            try {
                port1 shouldBe port2
            } finally {
                proxy.stop()
            }
        }

        test("stop() can be called without start()") {
            val proxy = CachingElectrumProxy("127.0.0.1", 9999)
            // Should not throw
            proxy.stop()
        }

        test("stop() followed by start() works (restart scenario)") {
            val proxy = CachingElectrumProxy("127.0.0.1", 9999)
            val port1 = proxy.start()
            proxy.stop()
            val port2 = proxy.start()
            try {
                // Both should be valid OS-assigned ports; they may differ
                (port2 > 0).shouldBeTrue()
            } finally {
                proxy.stop()
            }
        }

        test("server socket is accessible after start()") {
            val proxy = CachingElectrumProxy("127.0.0.1", 9999)
            val port = proxy.start()
            try {
                // We should be able to connect a raw socket to the proxy port
                val socket = Socket("127.0.0.1", port)
                socket.isConnected.shouldBeTrue()
                socket.close()
            } finally {
                proxy.stop()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 9. checkForScriptHashChanges — pure logic (no network needed for edge cases)
    // ═══════════════════════════════════════════════════════════════════════════
    context("checkForScriptHashChanges edge cases") {

        test("returns true immediately for empty cachedStatuses") {
            // Empty map → no script hashes to check → returns true (conservative)
            val proxy = CachingElectrumProxy("127.0.0.1", 1)
            val result = proxy.checkForScriptHashChanges(emptyMap())
            result.shouldBeTrue()
        }

        test("returns true when subscribeScriptHashes fails (no network)") {
            // No server running → subscribeScriptHashes returns empty → returns true
            val proxy = CachingElectrumProxy("127.0.0.1", 1, connectionTimeoutMs = 100, soTimeoutMs = 100)
            val result = proxy.checkForScriptHashChanges(mapOf("abc" to "status"))
            result.shouldBeTrue()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 10. Cache mock interactions via getTransactionDetails
    // ═══════════════════════════════════════════════════════════════════════════
    context("ElectrumCache mock interactions") {

        test("getTransactionDetails calls getVerboseTx on the cache") {
            val cache = mockk<ElectrumCache>()
            every { cache.getVerboseTx("myTxId") } returns """{"size":100,"vsize":50,"weight":200}"""
            val proxy = CachingElectrumProxy("127.0.0.1", 1, cache = cache)
            proxy.getTransactionDetails("myTxId")
            verify(exactly = 1) { cache.getVerboseTx("myTxId") }
        }

        test("getTransactionDetails does NOT call putVerboseTx when cache hit") {
            val cache = mockk<ElectrumCache>()
            every { cache.getVerboseTx(any()) } returns """{"size":100,"vsize":50,"weight":200}"""
            val proxy = CachingElectrumProxy("127.0.0.1", 1, cache = cache)
            proxy.getTransactionDetails("cachedTx")
            // No network call happened → no put
            verify(exactly = 0) { cache.putVerboseTx(any(), any(), any()) }
        }

        test("getAddressTxInfo returns null when cache returns invalid JSON") {
            val cache = mockk<ElectrumCache>()
            every { cache.getVerboseTx(any()) } returns "not-json"
            val proxy = CachingElectrumProxy("127.0.0.1", 1, cache = cache)
            val result = proxy.getAddressTxInfo("badJson", "bc1qaddr")
            result.shouldBeNull()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 11. Bridge cache interception — loopback integration test
    //     Verifies that cache HITS are served without forwarding to the server,
    //     and cache MISSes are forwarded and the response is stored.
    // ═══════════════════════════════════════════════════════════════════════════
    context("Bridge cache interception (loopback integration)") {

        /**
         * Spins up:
         * 1. A fake Electrum server that records received requests and replies
         * 2. The CachingElectrumProxy (with a mock cache) bridging to the fake server
         * Returns (fakeServerRequests, proxy, proxyPort).
         */
        fun buildBridgedProxy(cache: ElectrumCache): Triple<MutableList<String>, CachingElectrumProxy, Int> {
            val receivedByFakeServer = mutableListOf<String>()

            // Fake Electrum server
            val fakeServer = ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
            val fakePort = fakeServer.localPort

            val serverThread = Thread {
                try {
                    val client = fakeServer.accept()
                    client.soTimeout = 3_000
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val writer = PrintWriter(client.getOutputStream(), true)
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        synchronized(receivedByFakeServer) { receivedByFakeServer.add(line) }
                        val json = JSONObject(line)
                        val id = json.optInt("id", 0)
                        val method = json.optString("method", "")
                        when {
                            method == "blockchain.transaction.get" -> {
                                val txid = json.optJSONArray("params")?.optString(0) ?: "unknown"
                                writer.println("""{"jsonrpc":"2.0","id":$id,"result":"deadbeef$txid"}""")
                            }
                            else -> writer.println("""{"jsonrpc":"2.0","id":$id,"result":null}""")
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try { fakeServer.close() } catch (_: Exception) {}
                }
            }
            serverThread.isDaemon = true
            serverThread.start()

            val proxy = CachingElectrumProxy(
                targetHost = "127.0.0.1",
                targetPort = fakePort,
                cache = cache,
            )
            val proxyPort = proxy.start()
            return Triple(receivedByFakeServer, proxy, proxyPort)
        }

        test("cache miss: blockchain.transaction.get forwarded to server") {
            val cache = mockk<ElectrumCache>()
            every { cache.getRawTx(any()) } returns null
            every { cache.putRawTx(any(), any()) } returns Unit

            val (serverRequests, proxy, proxyPort) = buildBridgedProxy(cache)
            try {
                val client = Socket("127.0.0.1", proxyPort)
                client.soTimeout = 3_000
                val writer = PrintWriter(client.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))

                val request = """{"id":1,"method":"blockchain.transaction.get","params":["aabbccddeeff"]}"""
                writer.println(request)

                val response = reader.readLine()
                response.shouldNotBeNull()
                response!! shouldContain "\"result\""

                client.close()
                Thread.sleep(100)

                // The fake server should have received the request
                synchronized(serverRequests) {
                    serverRequests.any { it.contains("blockchain.transaction.get") }.shouldBeTrue()
                }
            } finally {
                proxy.stop()
            }
        }

        test("cache hit: blockchain.transaction.get NOT forwarded to server") {
            val cache = mockk<ElectrumCache>()
            val cachedHex = "cafebabe01020304"
            every { cache.getRawTx("txCacheHit") } returns cachedHex

            val (serverRequests, proxy, proxyPort) = buildBridgedProxy(cache)
            try {
                val client = Socket("127.0.0.1", proxyPort)
                client.soTimeout = 3_000
                val writer = PrintWriter(client.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))

                val request = """{"id":42,"method":"blockchain.transaction.get","params":["txCacheHit"]}"""
                writer.println(request)

                val response = reader.readLine()
                response.shouldNotBeNull()
                val responseJson = JSONObject(response!!)
                responseJson.optString("result") shouldBe cachedHex
                responseJson.optInt("id") shouldBe 42

                client.close()
                Thread.sleep(100)

                // The fake server should NOT have received this request
                synchronized(serverRequests) {
                    serverRequests.none { line ->
                        JSONObject(line).optJSONArray("params")?.optString(0) == "txCacheHit"
                    }.shouldBeTrue()
                }
            } finally {
                proxy.stop()
            }
        }

        test("verbose=true request is NOT intercepted by cache") {
            val cache = mockk<ElectrumCache>()
            // Even if a raw tx exists, verbose requests skip the cache
            every { cache.getRawTx("txVerbose") } returns "someHex"
            every { cache.putRawTx(any(), any()) } returns Unit

            val (serverRequests, proxy, proxyPort) = buildBridgedProxy(cache)
            try {
                val client = Socket("127.0.0.1", proxyPort)
                client.soTimeout = 3_000
                val writer = PrintWriter(client.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))

                // Verbose=true → proxy should NOT serve from cache
                val request = """{"id":7,"method":"blockchain.transaction.get","params":["txVerbose",true]}"""
                writer.println(request)

                val response = reader.readLine()
                response.shouldNotBeNull()

                client.close()
                Thread.sleep(100)

                // Server should have received it (not intercepted)
                synchronized(serverRequests) {
                    serverRequests.any { line ->
                        val j = JSONObject(line)
                        j.optString("method") == "blockchain.transaction.get" &&
                            j.optJSONArray("params")?.optString(0) == "txVerbose"
                    }.shouldBeTrue()
                }
            } finally {
                proxy.stop()
            }
        }
    }
})
