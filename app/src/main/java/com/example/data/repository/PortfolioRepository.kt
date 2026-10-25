package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.EmiPlan
import com.example.data.model.PortfolioHolding
import com.example.data.model.WatchlistItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PortfolioRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- HOLDINGS CRUD & REALTIME OBSERVATION ---

    fun observeHoldings(userId: String? = null): Flow<List<PortfolioHolding>> = flow {
        val uid = userId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/holdings"
        emitAll(
            db.collection("users").document(uid).collection("holdings")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            PortfolioHolding::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedBy { it.symbol }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getHoldingById(targetUserId: String, holdingId: String): Result<PortfolioHolding?> {
        val docRef = db.collection("users").document(targetUserId)
            .collection("holdings").document(holdingId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    PortfolioHolding::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addHolding(
        symbol: String,
        companyName: String,
        shares: Double,
        avgBuyPrice: Double,
        sector: String,
        notes: String = ""
    ): Result<String> {
        val uid = requireUserId()
        val cleanSymbol = symbol.trim().uppercase()
        val colRef = db.collection("users").document(uid).collection("holdings")
        return try {
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "symbol" to cleanSymbol,
                "companyName" to companyName.trim().ifEmpty { cleanSymbol },
                "shares" to shares,
                "avgBuyPrice" to avgBuyPrice,
                "sector" to sector.trim().ifEmpty { "Technology" },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (notes.isNotBlank()) {
                payload["notes"] = notes.trim().take(300)
            }
            val docRef = colRef.add(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateHolding(
        holdingId: String,
        shares: Double,
        avgBuyPrice: Double,
        notes: String = ""
    ): Result<Unit> {
        val uid = requireUserId()
        require(holdingId.isNotBlank()) { "Holding ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("holdings").document(holdingId)
        return try {
            val updates = mutableMapOf<String, Any>(
                "shares" to shares,
                "avgBuyPrice" to avgBuyPrice,
                "notes" to notes.trim().take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteHolding(holdingId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("holdings").document(holdingId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- WATCHLIST CRUD & REALTIME OBSERVATION ---

    fun observeWatchlist(userId: String? = null): Flow<List<WatchlistItem>> = flow {
        val uid = userId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/watchlist"
        emitAll(
            db.collection("users").document(uid).collection("watchlist")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            WatchlistItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedBy { it.symbol }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addWatchlistItem(
        symbol: String,
        companyName: String,
        targetAlertPrice: Double = 0.0
    ): Result<String> {
        val uid = requireUserId()
        val cleanSymbol = symbol.trim().uppercase()
        val docId = "watch_${cleanSymbol.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
        val docRef = db.collection("users").document(uid).collection("watchlist").document(docId)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "symbol" to cleanSymbol,
                "companyName" to companyName.trim().ifEmpty { cleanSymbol },
                "targetAlertPrice" to targetAlertPrice.coerceAtLeast(0.0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeWatchlistItem(itemId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("watchlist").document(itemId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- EMI PLANS CRUD & REALTIME OBSERVATION ---

    fun observeEmiPlans(userId: String? = null): Flow<List<EmiPlan>> = flow {
        val uid = userId ?: auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/emi_plans"
        emitAll(
            db.collection("users").document(uid).collection("emi_plans")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            EmiPlan::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveEmiPlan(
        title: String,
        loanType: String,
        principal: Double,
        annualRate: Double,
        tenureMonths: Int,
        monthlyPrepayment: Double,
        monthlyEmi: Double,
        totalInterest: Double
    ): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("emi_plans")
        return try {
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().take(100).ifEmpty { "$loanType Plan" },
                "loanType" to loanType,
                "principal" to principal,
                "annualRate" to annualRate,
                "tenureMonths" to tenureMonths.toLong(),
                "monthlyPrepayment" to monthlyPrepayment.coerceAtLeast(0.0),
                "monthlyEmi" to monthlyEmi,
                "totalInterest" to totalInterest.coerceAtLeast(0.0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val docRef = colRef.add(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteEmiPlan(planId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("emi_plans").document(planId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
