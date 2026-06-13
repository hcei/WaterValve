package com.hgu.watervalve.shared

import com.hgu.watervalve.shared.data.local.WaterValveDb
import com.hgu.watervalve.shared.data.remote.api.BannedException
import com.hgu.watervalve.shared.data.remote.api.RemoteDeviceDto
import com.hgu.watervalve.shared.data.remote.api.SyncApi
import com.hgu.watervalve.shared.data.repository.AuthRepository
import com.hgu.watervalve.shared.data.repository.DeviceRepository
import com.hgu.watervalve.shared.domain.model.Device
import com.hgu.watervalve.shared.platform.KeychainWrapper
import com.hgu.watervalve.shared.platform.UserDefaultsWrapper
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_SESSION_COOKIE
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_UIS_JWT
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_USER_ID
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_UWC_TOKEN
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeviceRepositoryTest {
    @Test
    fun addRenameStarDeleteAndRecordFlowUpdatesLocalState() {
        runBlocking {
            val fixture = createFixture(remoteDevices = emptyList())

            val added = fixture.repository.addDevice("https://example.com/device/1").getOrThrow()
            assertEquals(1, fixture.repository.devices.value.size)
            assertEquals(added.id, fixture.repository.devices.value.first().id)
            assertEquals(1, fixture.capturedPushPayloads.size)

            fixture.repository.renameDevice(added.id, "Dorm Valve").getOrThrow()
            assertEquals("Dorm Valve", fixture.repository.devices.value.first().name)

            fixture.repository.starDevice(added.id, true).getOrThrow()
            assertTrue(fixture.repository.devices.value.first().starred)

            fixture.repository.addRecord("Dorm Valve").getOrThrow()
            assertEquals(1, fixture.repository.records.value.size)

            val recordId = fixture.repository.records.value.first().id
            fixture.repository.deleteRecord(recordId).getOrThrow()
            assertTrue(fixture.repository.records.value.isEmpty())

            fixture.repository.deleteDevice(added.id).getOrThrow()
            assertTrue(fixture.repository.devices.value.isEmpty())
            assertEquals(4, fixture.capturedPushPayloads.size)
        }
    }

    @Test
    fun pullFromCloudReplacesLocalRowsAndKeepsSortOrder() {
        runBlocking {
            val remote = listOf(
                RemoteDeviceDto(
                    id = "remote-1",
                    customName = "Second",
                    qrContent = "qr-second",
                    isFavorite = false,
                    lastUsedAt = 20,
                ),
                RemoteDeviceDto(
                    id = "remote-2",
                    customName = "First",
                    qrContent = "qr-first",
                    isFavorite = true,
                    lastUsedAt = 10,
                ),
            )
            val fixture = createFixture(remoteDevices = remote)

            fixture.repository.addDevice("https://example.com/local").getOrThrow()
            assertEquals(1, fixture.repository.devices.value.size)

            fixture.repository.pullFromCloud().getOrThrow()

            val devices = fixture.repository.devices.value
            assertEquals(listOf("remote-2", "remote-1"), devices.map(Device::id))
            assertEquals(listOf("First", "Second"), devices.map(Device::name))
            assertFalse(fixture.authRepository.isBanned.value)
        }
    }

    @Test
    fun pushToCloudMarksBannedWhenServerRejectsUser() {
        runBlocking {
            val fixture = createFixture(
                remoteDevices = emptyList(),
                pushStatus = HttpStatusCode.Forbidden,
            )

            val result = fixture.repository.addDevice("https://example.com/device/ban")

            assertTrue(result.isFailure)
            assertIs<BannedException>(result.exceptionOrNull())
            assertTrue(fixture.authRepository.isBanned.value)
        }
    }

    @Test
    fun pullFromCloudMarksBannedWhenServerRejectsUser() {
        runBlocking {
            val fixture = createFixture(
                remoteDevices = emptyList(),
                pullStatus = HttpStatusCode.Forbidden,
            )

            val result = fixture.repository.pullFromCloud()

            assertTrue(result.isFailure)
            assertIs<BannedException>(result.exceptionOrNull())
            assertTrue(fixture.authRepository.isBanned.value)
        }
    }

    private fun createFixture(
        remoteDevices: List<RemoteDeviceDto>,
        pullStatus: HttpStatusCode = HttpStatusCode.OK,
        pushStatus: HttpStatusCode = HttpStatusCode.OK,
    ): RepositoryFixture {
        val json = Json { ignoreUnknownKeys = true }
        val capturedPushPayloads = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                when {
                    request.method == HttpMethod.Get && request.url.encodedPath.contains("/api/devices/") -> {
                        if (pullStatus == HttpStatusCode.Forbidden) {
                            respond(
                                content = """{"error":"banned"}""",
                                status = pullStatus,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        } else {
                            respond(
                                content = json.encodeToString(ListSerializer(RemoteDeviceDto.serializer()), remoteDevices),
                                status = pullStatus,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }

                    request.method == HttpMethod.Post && request.url.encodedPath.contains("/api/devices/") -> {
                        capturedPushPayloads += "push:${request.url.encodedPath}"
                        respond(
                            content = if (pushStatus == HttpStatusCode.Forbidden) """{"error":"banned"}""" else """{"ok":true}""",
                            status = pushStatus,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    else -> error("Unexpected request: ${request.method.value} ${request.url}")
                }
            }
        )

        val syncApi = SyncApi(client)
        val keychain = KeychainWrapper().apply {
            set(KEYCHAIN_KEY_USER_ID, "u-1001")
            set(KEYCHAIN_KEY_UWC_TOKEN, "uwc-token")
            set(KEYCHAIN_KEY_UIS_JWT, "uis-jwt")
            set(KEYCHAIN_KEY_SESSION_COOKIE, "session-cookie")
        }
        val userDefaults = UserDefaultsWrapper()
        val authRepository = AuthRepository(
            uwcApi = createAuthApi(),
            keychain = keychain,
            userDefaults = userDefaults,
        )

        val driver = createTestDatabaseDriver()
        val database = WaterValveDb(driver)
        WaterValveDb.Schema.create(driver)

        val repository = DeviceRepository(
            syncApi = syncApi,
            database = database,
            authRepository = authRepository,
        )

        return RepositoryFixture(
            repository = repository,
            authRepository = authRepository,
            capturedPushPayloads = capturedPushPayloads,
        )
    }

    private data class RepositoryFixture(
        val repository: DeviceRepository,
        val authRepository: AuthRepository,
        val capturedPushPayloads: MutableList<String>,
    )

    private fun createAuthApi() = AuthRepositoryTestFixtures.createApi()
}
