package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveHolding_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = PortfolioRepository(firestore)

        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addHolding(
                symbol = "AAPL",
                companyName = "Apple Inc.",
                shares = 12.5,
                avgBuyPrice = 185.0,
                sector = "Technology",
                notes = "Long term core"
            )
        }
        assertTrue("Expected addHolding to succeed, got: ${addResult.exceptionOrNull()}", addResult.isSuccess)
        val holdingId = addResult.getOrThrow()

        val emittedHoldings = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHoldings(aliceUid).first { list -> list.any { it.id == holdingId } }
        }
        val holding = emittedHoldings.first { it.id == holdingId }
        assertEquals("AAPL", holding.symbol)
        assertEquals(12.5, holding.shares, 0.001)

        // Update holding
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateHolding(holdingId, shares = 20.0, avgBuyPrice = 190.0, notes = "Added 7.5 shares")
        }
        assertTrue(updateResult.isSuccess)

        // Delete holding
        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteHolding(holdingId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun saveEmiPlanAndWatchlist_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = PortfolioRepository(firestore)

        val watchResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addWatchlistItem("NVDA", "NVIDIA Corp.", 140.0)
        }
        assertTrue(watchResult.isSuccess)

        val emiResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveEmiPlan(
                title = "Home Mortgage",
                loanType = "Home Loan",
                principal = 350000.0,
                annualRate = 6.75,
                tenureMonths = 360,
                monthlyPrepayment = 250.0,
                monthlyEmi = 2270.09,
                totalInterest = 467232.4
            )
        }
        assertTrue(emiResult.isSuccess)
        val planId = emiResult.getOrThrow()

        val plans = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeEmiPlans(aliceUid).first { list -> list.any { it.id == planId } }
        }
        assertTrue(plans.any { it.id == planId && it.title == "Home Mortgage" })
    }

    @Test
    fun getHoldingById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PortfolioRepository(firestore)
        val holdingId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addHolding("MSFT", "Microsoft Corp.", 5.0, 415.0, "Technology").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = PortfolioRepository(firestore)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getHoldingById(aliceUid, holdingId)
        }
        assertTrue("Expected cross-user access to fail", crossReadResult.isFailure)
        val firestoreEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeHoldings_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = PortfolioRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeHoldings(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Expected exception when unauthenticated", caughtException)
        val firestoreException = checkNotNull(caughtException?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caughtException"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@pulsefolio.test"
        const val BOB_EMAIL = "bob@pulsefolio.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
