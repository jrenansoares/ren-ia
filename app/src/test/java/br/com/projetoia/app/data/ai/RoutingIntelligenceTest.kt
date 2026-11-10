package br.com.projetoia.app.data.ai

import br.com.projetoia.app.data.datastore.FreeRouterSettings
import br.com.projetoia.app.data.datastore.Settings
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test

class RoutingIntelligenceTest {
    private val model = Model(modelId = "test-model", chatSupported = true)
    private val provider = ProviderSetting.OpenAI(name = "Test", baseUrl = "https://example.test/v1", apiKey = "private-test-key", models = listOf(model))
    private fun initial() = Settings(providers = listOf(provider), freeRouter = FreeRouterSettings(automaticManagement = true, approvedModelIds = listOf(model.id)))
    private fun Settings.checked(at: Long = 1) = recordModelCheck(ModelCheck(provider.id, model.id, modelCheckFingerprint(provider, model), at, 100, ModelCheckStatus.OK))

    @Test fun catalogRetiresRoutesWithoutDeletingModelsOrChangingTheirIds() {
        val start = initial().authorizeProviderRouting(provider).checked()
        val retired = start.mergeProviderCatalog(provider, listOf(Model(modelId = "new-model")), 10)
        assertEquals(model.id, retired.providers.single().models.first().id)
        assertFalse(retired.catalogAllows(retired.providers.single(), retired.providers.single().models.first()))
        assertTrue(retired.automaticRoutes("").isEmpty())
        assertEquals(listOf(model.id), retired.freeRouter.approvedModelIds)
        val restored = retired.mergeProviderCatalog(retired.providers.single(), listOf(model), 20)
        assertEquals(listOf(model.id), restored.automaticRoutes("").map { it.model.id })
    }

    @Test fun failedCatalogRefreshKeepsLastKnownModelsAndAvailability() {
        val start = initial().authorizeProviderRouting(provider).checked().mergeProviderCatalog(provider, listOf(model), 10)
        val failed = start.recordCatalogFailure(start.providers.single(), java.io.IOException("network"), 20)
        assertEquals(start.providers, failed.providers)
        assertEquals(10L, failed.catalogState(failed.providers.single())!!.checkedAt)
        assertTrue(failed.catalogAllows(failed.providers.single(), model))
        assertEquals(listOf(model.id), failed.automaticRoutes("").map { it.model.id })
    }

    @Test fun keyRotationInvalidatesConsentAndLateResults() {
        val authorized = initial().authorizeProviderRouting(provider)
        val rotatedProvider = provider.copy(apiKey = "replacement")
        val rotated = authorized.copy(providers = listOf(rotatedProvider))
        assertFalse(rotated.hasProviderRoutingAuthorization(rotatedProvider))
        assertEquals(rotated, rotated.mergeProviderCatalog(provider, emptyList(), 10))
        assertEquals(rotated, rotated.recordCatalogFailure(provider, java.io.IOException(), 10))
        assertTrue(rotated.automaticProbeCandidates(rotatedProvider, true, 100).isEmpty())
    }

    @Test fun automaticProbesNeedConsentAndRemainBoundedWithLargeCatalogs() {
        val many = (1..1000).map { Model(modelId = "model-$it", chatSupported = true) }
        val p = provider.copy(models = many)
        val start = initial().copy(providers = listOf(p))
        assertTrue(start.automaticProbeCandidates(p, true, 100).isEmpty())
        val allowed = start.authorizeProviderRouting(p)
        assertEquals(4, allowed.automaticProbeCandidates(p, true, 100).size)
        val excluded = allowed.automaticProbeCandidates(p, true, 100).first().id
        val disabled = allowed.copy(freeRouter = allowed.freeRouter.copy(excludedModelIds = listOf(excluded)))
        assertFalse(disabled.automaticProbeCandidates(p, true, 100).any { it.id == excluded })
    }

    @Test fun recentSuccessfulChecksAreReusedWithoutMoreInference() {
        val checked = initial().authorizeProviderRouting(provider).checked(at = 100)
        val p = checked.providers.single()
        assertTrue(checked.automaticProbeCandidates(p, false, 101).isEmpty())
        assertEquals(1, checked.automaticProbeCandidates(p, true, 101).size)
    }

    @Test fun runtimeObservationsSurviveRestartAndQuotaTemporarilyRemovesTheRoute() {
        val start = initial().authorizeProviderRouting(provider).checked()
        val limited = start.recordRouteObservation(FreeRoute(provider, model), RouteObservation(false, 20, 429, 120_000), now = System.currentTimeMillis())
        val restored = Json.decodeFromString<FreeRouterSettings>(Json.encodeToString(limited.freeRouter))
        val settings = limited.copy(freeRouter = restored)
        assertTrue(settings.automaticRoutes("").isEmpty())
        assertEquals(1, restored.routeHealth.single().failures)
        val recovered = settings.recordRouteObservation(FreeRoute(provider, model), RouteObservation(true, 80))
        assertEquals(1, recovered.automaticRoutes("").size)
        assertEquals(0, recovered.freeRouter.routeHealth.single().consecutiveFailures)
    }

    @Test fun learningPrefersSuccessfulFastModelsWithoutClaimingQualityBenchmarks() {
        val base = ModelRouteHealth(provider.id, model.id, "fp")
        assertTrue(routeLearningScore(base.copy(successes = 10, responseMs = 500)) > routeLearningScore(base.copy(failures = 10, responseMs = 5000, consecutiveFailures = 3)))
    }

    @Test fun providerConsentDoesNotBecomePermanentIndividualConsentAfterAProbe() {
        val start = initial().authorizeProviderRouting(provider)
        val checked = start.recordModelCheck(ModelCheck(provider.id, model.id, modelCheckFingerprint(provider, model), 1, 1, ModelCheckStatus.OK, toolsVerified = true))
        assertTrue(checked.freeRouter.costAcknowledgements.isEmpty())
        val revoked = checked.copy(freeRouter = checked.freeRouter.copy(providerAuthorizations = emptyList()))
        val p = revoked.providers.single()
        assertFalse(revoked.hasCostAcknowledged(p, p.models.single()))
        assertTrue(revoked.automaticRoutes("").isEmpty())
    }

    @Test fun migratedSettingsDefaultToNoAutomaticInferenceOrProviderConsent() {
        val settings = Json.decodeFromString<FreeRouterSettings>("{}")
        assertFalse(settings.automaticManagement)
        assertTrue(settings.catalogs.isEmpty())
        assertTrue(settings.providerAuthorizations.isEmpty())
    }

    @Test fun telemetryStoresNeitherKeysNorConversationContent() {
        val settings = initial().recordRouteObservation(FreeRoute(provider, model), RouteObservation(true, 20))
        val json = Json.encodeToString(settings.freeRouter)
        assertFalse(json.contains(provider.apiKey))
    }
    @Test fun rejectedCredentialsBlockOtherPreviouslyVerifiedModelsUntilRechecked() {
        val start = initial().authorizeProviderRouting(provider).checked()
        val rejected = start.recordModelCheck(ModelCheck(provider.id, model.id, modelCheckFingerprint(provider, model),
            System.currentTimeMillis(), 20, ModelCheckStatus.AUTH_ERROR, httpStatus = 401))
        assertFalse(rejected.providerReady(rejected.providers.single()))
        assertTrue(rejected.automaticRoutes("").isEmpty())
        val recovered = rejected.checked(System.currentTimeMillis())
        assertTrue(recovered.providerReady(recovered.providers.single()))
    }

}
