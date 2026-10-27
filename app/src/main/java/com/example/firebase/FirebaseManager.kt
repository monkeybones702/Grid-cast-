package com.example.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val isAnonymous: Boolean = false
)

data class CastingSessionRecord(
    val id: String = "",
    val title: String = "",
    val hostIp: String = "",
    val role: String = "", // "HUB", "BROADCASTER", "RECEIVER"
    val deviceCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isLive: Boolean = true,
    val userId: String = ""
)

class FirebaseManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _sessions = MutableStateFlow<List<CastingSessionRecord>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable = _isFirebaseAvailable.asStateFlow()

    init {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            _isFirebaseAvailable.value = true

            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                updateUserState(user)
            }

            // Auto-sign in anonymously if not signed in to ensure identity
            if (auth?.currentUser == null) {
                signInAnonymously()
            } else {
                listenToSessions()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization warning: ${e.message}. Running in local mode.")
            _isFirebaseAvailable.value = false
            // Provide a guest user
            _currentUser.value = UserProfile(
                uid = "guest_${System.currentTimeMillis() % 10000}",
                displayName = "Guest User",
                isAnonymous = true
            )
        }
    }

    private fun updateUserState(user: FirebaseUser?) {
        if (user != null) {
            _currentUser.value = UserProfile(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName ?: if (user.isAnonymous) "Guest (${user.uid.take(5)})" else "Cast User",
                isAnonymous = user.isAnonymous
            )
            listenToSessions()
        } else {
            _currentUser.value = null
            _sessions.value = emptyList()
        }
    }

    fun signInAnonymously(onComplete: (Boolean) -> Unit = {}) {
        val a = auth ?: run {
            onComplete(false)
            return
        }
        scope.launch {
            try {
                a.signInAnonymously().await()
                Log.d(TAG, "Signed in anonymously: ${a.currentUser?.uid}")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Anonymous sign in failed", e)
                onComplete(false)
            }
        }
    }

    fun signInWithEmail(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        val a = auth ?: run {
            onComplete(false, "Firebase Auth not available")
            return
        }
        scope.launch {
            try {
                a.signInWithEmailAndPassword(email, pass).await()
                onComplete(true, null)
            } catch (e: Exception) {
                // Try create if doesn't exist
                try {
                    a.createUserWithEmailAndPassword(email, pass).await()
                    onComplete(true, null)
                } catch (e2: Exception) {
                    Log.e(TAG, "Sign in/up failed", e2)
                    onComplete(false, e2.localizedMessage ?: "Authentication failed")
                }
            }
        }
    }

    fun signOut() {
        auth?.signOut()
        signInAnonymously()
    }

    fun recordCastingSession(title: String, hostIp: String, role: String, deviceCount: Int): String {
        val sessionId = "session_${System.currentTimeMillis()}"
        val user = _currentUser.value
        val record = CastingSessionRecord(
            id = sessionId,
            title = title,
            hostIp = hostIp,
            role = role,
            deviceCount = deviceCount,
            timestamp = System.currentTimeMillis(),
            isLive = true,
            userId = user?.uid ?: "anonymous"
        )

        // Local state update immediately
        _sessions.value = listOf(record) + _sessions.value

        // Persist to Firestore if available
        val db = firestore
        if (db != null) {
            scope.launch {
                try {
                    val map = hashMapOf(
                        "id" to record.id,
                        "title" to record.title,
                        "hostIp" to record.hostIp,
                        "role" to record.role,
                        "deviceCount" to record.deviceCount,
                        "timestamp" to record.timestamp,
                        "isLive" to record.isLive,
                        "userId" to record.userId
                    )
                    db.collection("casting_sessions").document(sessionId).set(map).await()
                    Log.d(TAG, "Saved casting session to Firestore: $sessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore write warning: ${e.message}")
                }
            }
        }
        return sessionId
    }

    fun endCastingSession(sessionId: String) {
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) it.copy(isLive = false) else it
        }
        val db = firestore ?: return
        scope.launch {
            try {
                db.collection("casting_sessions").document(sessionId)
                    .update("isLive", false)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore update warning: ${e.message}")
            }
        }
    }

    private fun listenToSessions() {
        val db = firestore ?: return
        val uid = auth?.currentUser?.uid ?: return

        try {
            db.collection("casting_sessions")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore listen warning: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                CastingSessionRecord(
                                    id = doc.getString("id") ?: doc.id,
                                    title = doc.getString("title") ?: "Cast Session",
                                    hostIp = doc.getString("hostIp") ?: "",
                                    role = doc.getString("role") ?: "HUB",
                                    deviceCount = (doc.getLong("deviceCount") ?: 0).toInt(),
                                    timestamp = doc.getLong("timestamp") ?: 0L,
                                    isLive = doc.getBoolean("isLive") ?: false,
                                    userId = doc.getString("userId") ?: ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (list.isNotEmpty()) {
                            _sessions.value = list
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach Firestore listener: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FirebaseManager"
    }
}
