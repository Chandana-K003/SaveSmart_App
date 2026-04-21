package com.example.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FriendsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tvMyStreak: TextView
    private lateinit var tvMyCode: TextView
    private lateinit var btnShare: Button
    private lateinit var etFriendCode: EditText
    private lateinit var btnAddFriend: Button
    private lateinit var tvStatus: TextView
    private lateinit var rankingLayout: LinearLayout
    private lateinit var requestsLayout: LinearLayout
    private lateinit var requestsCard: LinearLayout
    private lateinit var progressBar: ProgressBar

    private var friendsListener: ListenerRegistration? = null

    private val myUid get() = auth.currentUser?.uid ?: ""
    private val myEmail get() =
        auth.currentUser?.email ?: "Unknown"
    private val myName get() =
        myEmail.substringBefore("@")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        setupUI()
        lifecycleScope.launch {
            uploadMyProfileSuspend()
            processAcceptedRequestsSuspend()
            loadPendingRequests()
            loadRankingsLive()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        friendsListener?.remove()
    }

    // ── Friend code ──────────────────────────────────────────────

    private fun generateFriendCode(uid: String): String {
        if (uid.isEmpty()) return "XXXXXX"
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder()
        var hash = uid.hashCode().toLong()
        if (hash < 0) hash = -hash
        repeat(6) {
            sb.append(chars[(hash % chars.length).toInt()])
            hash /= chars.length
        }
        return sb.toString()
    }

    // ── Upload my latest streak to Firestore ─────────────────────

    private suspend fun uploadMyProfileSuspend() {
        if (myUid.isEmpty()) return
        val pts = StreakManager.getStreakPoints(this)
        val code = generateFriendCode(myUid)
        val (used, saved, expired) = StreakManager.getStats(this)
        try {
            withContext(Dispatchers.IO) {
                db.collection("users").document(myUid)
                    .set(mapOf(
                        "uid"              to myUid,
                        "email"            to myEmail,
                        "displayName"      to myName,
                        "friendCode"       to code,
                        "streakPoints"     to pts,
                        "usedBeforeExpiry" to used,
                        "savedFromAlert"   to saved,
                        "expiredBeforeUse" to expired,
                        "updatedAt"        to
                                System.currentTimeMillis()
                    )).await()

                db.collection("friendCodes").document(code)
                    .set(mapOf(
                        "uid"         to myUid,
                        "displayName" to myName,
                        "updatedAt"   to
                                System.currentTimeMillis()
                    )).await()
            }
            android.util.Log.d("Friends",
                "Uploaded profile: $myName pts=$pts")
        } catch (e: Exception) {
            android.util.Log.e("Friends",
                "uploadProfile FAILED: ${e.message}")
        }
    }

    // ── Load rankings — always fetch LIVE /users/{uid} ───────────

    private fun loadRankingsLive() {
        if (myUid.isEmpty()) {
            progressBar.visibility = View.GONE
            return
        }

        friendsListener?.remove()
        friendsListener = db.collection("users")
            .document(myUid)
            .collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Friends",
                        "friendsListener: ${error.message}")
                    progressBar.visibility = View.GONE
                    return@addSnapshotListener
                }

                val friendUids = snapshot?.documents
                    ?.mapNotNull { it.getString("uid") }
                    ?: emptyList()

                android.util.Log.d("Friends",
                    "Friends list: $friendUids")

                lifecycleScope.launch {
                    val users = mutableListOf<UserStreak>()

                    withContext(Dispatchers.IO) {
                        for (uid in friendUids) {
                            try {
                                val doc = db
                                    .collection("users")
                                    .document(uid)
                                    .get().await()

                                val name = doc.getString(
                                    "displayName") ?: "Friend"
                                val pts = doc.getLong(
                                    "streakPoints")
                                    ?.toInt() ?: 0

                                android.util.Log.d("Friends",
                                    "Friend $uid " +
                                            "name=$name pts=$pts " +
                                            "exists=${doc.exists()}")

                                if (doc.exists()) {
                                    users.add(UserStreak(
                                        uid, name, pts,
                                        isMe = false))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("Friends",
                                    "fetch $uid FAILED: " +
                                            "${e.message}")
                            }
                        }
                    }

                    // Add myself with local streak
                    val myPts = StreakManager
                        .getStreakPoints(this@FriendsActivity)
                    users.add(UserStreak(
                        myUid, myName, myPts, isMe = true))

                    android.util.Log.d("Friends",
                        "Final rankings: " +
                                users.map { "${it.name}=${it.points}" })

                    progressBar.visibility = View.GONE
                    showRankings(
                        users.sortedByDescending { it.points })
                }
            }
    }

    // ── Add friend ───────────────────────────────────────────────

    private fun addFriend() {
        val code = etFriendCode.text.toString()
            .trim().uppercase()

        if (code.length != 6) {
            showStatus("❌ Enter a valid 6-character code",
                "#C62828"); return
        }
        if (code == generateFriendCode(myUid)) {
            showStatus("❌ That's your own code!", "#C62828")
            return
        }

        btnAddFriend.isEnabled = false
        showStatus("🔍 Looking up friend...", "#1565C0")

        lifecycleScope.launch {
            try {
                val codeDoc = withContext(Dispatchers.IO) {
                    db.collection("friendCodes")
                        .document(code).get().await()
                }

                if (!codeDoc.exists()) {
                    showStatus(
                        "❌ No user found with code: $code",
                        "#C62828")
                    btnAddFriend.isEnabled = true
                    return@launch
                }

                val friendUid = codeDoc.getString("uid")
                    ?: run {
                        showStatus("❌ Invalid code", "#C62828")
                        btnAddFriend.isEnabled = true
                        return@launch
                    }
                val friendName =
                    codeDoc.getString("displayName") ?: "Friend"

                val alreadyFriend = withContext(Dispatchers.IO) {
                    db.collection("users").document(myUid)
                        .collection("friends")
                        .document(friendUid)
                        .get().await().exists()
                }
                if (alreadyFriend) {
                    showStatus(
                        "ℹ️ $friendName is already your friend!",
                        "#1565C0")
                    btnAddFriend.isEnabled = true
                    return@launch
                }

                val requestId = "${friendUid}_${myUid}"
                val alreadySent = withContext(Dispatchers.IO) {
                    db.collection("friendRequests")
                        .document(requestId)
                        .get().await().exists()
                }
                if (alreadySent) {
                    showStatus(
                        "ℹ️ Request already sent to $friendName",
                        "#1565C0")
                    btnAddFriend.isEnabled = true
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    db.collection("friendRequests")
                        .document(requestId)
                        .set(mapOf(
                            "toUid"    to friendUid,
                            "fromUid"  to myUid,
                            "fromName" to myName,
                            "fromCode" to
                                    generateFriendCode(myUid),
                            "status"   to "pending",
                            "sentAt"   to
                                    System.currentTimeMillis()
                        )).await()
                }

                showStatus(
                    "✅ Request sent to $friendName!\n" +
                            "Ask them to open Friends screen to accept.",
                    "#2E7D32")
                etFriendCode.setText("")
                btnAddFriend.isEnabled = true

            } catch (e: Exception) {
                showStatus(
                    "❌ Error: ${e.message?.take(60)}",
                    "#C62828")
                btnAddFriend.isEnabled = true
            }
        }
    }

    // ── Pending requests ─────────────────────────────────────────

    private fun loadPendingRequests() {
        if (myUid.isEmpty()) return
        lifecycleScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) {
                    db.collection("friendRequests")
                        .whereEqualTo("toUid", myUid)
                        .whereEqualTo("status", "pending")
                        .get().await()
                }
                val pending = snap.documents.mapNotNull { doc ->
                    val fromUid = doc.getString("fromUid")
                        ?: return@mapNotNull null
                    FriendRequest(
                        fromUid,
                        doc.getString("fromName") ?: "Friend",
                        doc.id)
                }
                if (pending.isEmpty()) {
                    requestsCard.visibility = View.GONE
                } else {
                    requestsCard.visibility = View.VISIBLE
                    showPendingRequests(pending)
                }
            } catch (e: Exception) {
                android.util.Log.e("Friends",
                    "loadRequests: ${e.message}")
            }
        }
    }

    private fun showPendingRequests(
        requests: List<FriendRequest>
    ) {
        requestsLayout.removeAllViews()
        requests.forEach { req ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 10, 0, 10)
            }
            row.addView(TextView(this).apply {
                text = "👤 ${req.name} wants to be friends"
                textSize = 14f
                setTextColor(android.graphics.Color
                    .parseColor("#212121"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f)
            })
            row.addView(Button(this).apply {
                text = "✅"
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable
                    .GradientDrawable().apply {
                        setColor(android.graphics.Color
                            .parseColor("#2E7D32"))
                        cornerRadius = 32f
                    }
                layoutParams =
                    LinearLayout.LayoutParams(100, 90)
                        .also { it.setMargins(8, 0, 4, 0) }
                setOnClickListener { acceptRequest(req) }
            })
            row.addView(Button(this).apply {
                text = "❌"
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable
                    .GradientDrawable().apply {
                        setColor(android.graphics.Color
                            .parseColor("#C62828"))
                        cornerRadius = 32f
                    }
                layoutParams =
                    LinearLayout.LayoutParams(100, 90)
                        .also { it.setMargins(4, 0, 0, 0) }
                setOnClickListener { declineRequest(req) }
            })
            requestsLayout.addView(row)
            requestsLayout.addView(View(this).apply {
                setBackgroundColor(android.graphics.Color
                    .parseColor("#E0E0E0"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1)
            })
        }
    }

    // ── Accept request ───────────────────────────────────────────

    private fun acceptRequest(req: FriendRequest) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val batch = db.batch()

                    batch.set(
                        db.collection("users")
                            .document(myUid)
                            .collection("friends")
                            .document(req.fromUid),
                        mapOf(
                            "uid"     to req.fromUid,
                            "addedAt" to
                                    System.currentTimeMillis()
                        )
                    )

                    batch.update(
                        db.collection("friendRequests")
                            .document(req.docId),
                        mapOf("status" to "accepted")
                    )

                    val reverseId =
                        "${req.fromUid}_${myUid}_accepted"
                    batch.set(
                        db.collection("friendRequests")
                            .document(reverseId),
                        mapOf(
                            "toUid"      to req.fromUid,
                            "fromUid"    to myUid,
                            "fromName"   to myName,
                            "status"     to "accepted",
                            "acceptedAt" to
                                    System.currentTimeMillis()
                        )
                    )

                    batch.commit().await()
                }

                Toast.makeText(this@FriendsActivity,
                    "✅ You and ${req.name} are now friends!",
                    Toast.LENGTH_SHORT).show()
                loadPendingRequests()

            } catch (e: Exception) {
                Toast.makeText(this@FriendsActivity,
                    "Error: ${e.message?.take(40)}",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun declineRequest(req: FriendRequest) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db.collection("friendRequests")
                        .document(req.docId)
                        .update(mapOf("status" to "declined"))
                        .await()
                }
                loadPendingRequests()
            } catch (e: Exception) { }
        }
    }

    // ── Process accepted notices ─────────────────────────────────

    private suspend fun processAcceptedRequestsSuspend() {
        if (myUid.isEmpty()) return
        try {
            val snap = withContext(Dispatchers.IO) {
                db.collection("friendRequests")
                    .whereEqualTo("toUid", myUid)
                    .whereEqualTo("status", "accepted")
                    .get().await()
            }
            if (snap.isEmpty) return

            withContext(Dispatchers.IO) {
                val batch = db.batch()
                for (doc in snap.documents) {
                    val friendUid =
                        doc.getString("fromUid") ?: continue

                    batch.set(
                        db.collection("users")
                            .document(myUid)
                            .collection("friends")
                            .document(friendUid),
                        mapOf(
                            "uid"     to friendUid,
                            "addedAt" to
                                    System.currentTimeMillis()
                        )
                    )

                    batch.delete(
                        db.collection("friendRequests")
                            .document(doc.id)
                    )
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            android.util.Log.e("Friends",
                "processAccepted: ${e.message}")
        }
    }

    // ── Show rankings ────────────────────────────────────────────

    private fun showRankings(users: List<UserStreak>) {
        rankingLayout.removeAllViews()

        if (users.isEmpty()) {
            rankingLayout.addView(TextView(this).apply {
                text = "No friends yet!\nShare your code 👆"
                textSize = 14f
                setTextColor(android.graphics.Color
                    .parseColor("#888888"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 16)
            })
            return
        }

        val medals = listOf("🥇", "🥈", "🥉")
        users.forEachIndexed { idx, user ->
            val medal =
                medals.getOrNull(idx) ?: "  ${idx + 1}."
            val emoji =
                StreakManager.getStreakEmoji(user.points)
            val label =
                StreakManager.getStreakLabel(user.points)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.setMargins(0, 0, 0, 8) }
                background = android.graphics.drawable
                    .GradientDrawable().apply {
                        setColor(if (user.isMe)
                            android.graphics.Color
                                .parseColor("#E8F5E9")
                        else android.graphics.Color
                            .parseColor("#F9F9F9"))
                        cornerRadius = 12f
                        setStroke(2, if (user.isMe)
                            android.graphics.Color
                                .parseColor("#4CAF50")
                        else android.graphics.Color
                            .parseColor("#E0E0E0"))
                    }
                elevation = if (user.isMe) 3f else 1f
            }

            row.addView(TextView(this).apply {
                text = medal; textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams =
                    LinearLayout.LayoutParams(56, 56)
            })

            val nameCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f)
                setPadding(12, 0, 0, 0)
            }
            nameCol.addView(TextView(this).apply {
                text = if (user.isMe)
                    "${user.name} (You)" else user.name
                textSize = 15f
                setTypeface(null, if (user.isMe)
                    android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL)
                setTextColor(android.graphics.Color.parseColor(
                    if (user.isMe) "#1B5E20" else "#212121"))
            })
            nameCol.addView(TextView(this).apply {
                text = "$emoji $label"; textSize = 12f
                setTextColor(android.graphics.Color
                    .parseColor("#757575"))
            })
            row.addView(nameCol)

            row.addView(TextView(this).apply {
                text = "🔥 ${user.points}"; textSize = 18f
                setTypeface(null,
                    android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(
                    if (user.points >= 0) "#E65100"
                    else "#C62828"))
                gravity = android.view.Gravity.CENTER
            })

            rankingLayout.addView(row)
        }

        rankingLayout.addView(Button(this).apply {
            text = "🔄 Refresh Rankings"
            textSize = 13f
            setTextColor(android.graphics.Color
                .parseColor("#2E7D32"))
            setBackgroundColor(
                android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(0, 8, 0, 0) }
            setOnClickListener {
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    uploadMyProfileSuspend()
                    processAcceptedRequestsSuspend()
                    loadPendingRequests()
                    loadRankingsLive()
                }
            }
        })
    }

    // ── Share code ───────────────────────────────────────────────

    private fun shareCode() {
        val code = generateFriendCode(myUid)
        val text = "Join me on SaveSmart! 🌿\n\n" +
                "Add me as a friend using my code: $code\n\n" +
                "SaveSmart helps track product expiry dates " +
                "and compete on streaks! 🔥"
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT,
                    "Join me on SaveSmart!")
            }, "Share Friend Code"))
    }

    private fun showStatus(msg: String, color: String) {
        tvStatus.text = msg
        tvStatus.setTextColor(
            android.graphics.Color.parseColor(color))
    }

    private fun makeCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 28, 32, 28)
        elevation = 3f
        background = android.graphics.drawable
            .GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 20f
            }
    }

    private fun sectionTitle(title: String) =
        TextView(this).apply {
            text = title; textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color
                .parseColor("#2E7D32"))
            setPadding(0, 0, 0, 12)
        }

    private fun setupUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color
                .parseColor("#F5F5F5"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 48)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color
                .parseColor("#2E7D32"))
            setPadding(32, 56, 32, 32)
        }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(TextView(this@FriendsActivity).apply {
                text = "←"; textSize = 22f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(0, 0, 16, 0)
                setOnClickListener { finish() }
            })
            addView(TextView(this@FriendsActivity).apply {
                text = "👥 Friends & Rankings"
                textSize = 20f
                setTypeface(null,
                    android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
            })
        }.also { header.addView(it) }

        tvMyStreak = TextView(this).apply {
            textSize = 16f
            setTextColor(android.graphics.Color
                .parseColor("#A5D6A7"))
            setPadding(0, 16, 0, 0)
        }
        header.addView(tvMyStreak)

        val myCodeCard = makeCard()
        myCodeCard.addView(sectionTitle("🔗 My Friend Code"))
        myCodeCard.addView(TextView(this).apply {
            text = "Share this code with friends to connect"
            textSize = 13f
            setTextColor(android.graphics.Color
                .parseColor("#666666"))
            setPadding(0, 0, 0, 12)
        })
        tvMyCode = TextView(this).apply {
            text = generateFriendCode(myUid)
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color
                .parseColor("#1B5E20"))
            gravity = android.view.Gravity.CENTER
            setPadding(24, 16, 24, 16)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#E8F5E9"))
                    cornerRadius = 12f
                    setStroke(2, android.graphics.Color
                        .parseColor("#4CAF50"))
                }
            letterSpacing = 0.3f
        }
        myCodeCard.addView(tvMyCode)
        btnShare = Button(this).apply {
            text = "📤 Share Code with Friends"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#1565C0"))
                    cornerRadius = 32f
                }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 110)
                .also { it.setMargins(0, 16, 0, 0) }
            setOnClickListener { shareCode() }
        }
        myCodeCard.addView(btnShare)

        requestsCard = makeCard()
        requestsCard.addView(
            sectionTitle("🤝 Friend Requests"))
        requestsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        requestsCard.addView(requestsLayout)
        requestsCard.visibility = View.GONE

        val addCard = makeCard()
        addCard.addView(sectionTitle("➕ Add Friend"))
        addCard.addView(TextView(this).apply {
            text = "Enter friend's code — they'll get a " +
                    "request to accept"
            textSize = 13f
            setTextColor(android.graphics.Color
                .parseColor("#666666"))
            setPadding(0, 0, 0, 12)
        })
        etFriendCode = EditText(this).apply {
            hint = "Enter friend code (e.g. ABC123)"
            textSize = 18f
            setTextColor(android.graphics.Color.BLACK)
            setHintTextColor(android.graphics.Color
                .parseColor("#AAAAAA"))
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 12f
                    setStroke(2, android.graphics.Color
                        .parseColor("#CCCCCC"))
                }
            setPadding(24, 20, 24, 20)
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType
                            .TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        addCard.addView(etFriendCode)
        btnAddFriend = Button(this).apply {
            text = "➕ Send Friend Request"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#2E7D32"))
                    cornerRadius = 32f
                }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 110)
                .also { it.setMargins(0, 12, 0, 0) }
            setOnClickListener { addFriend() }
        }
        addCard.addView(btnAddFriend)
        tvStatus = TextView(this).apply {
            text = ""; textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }
        addCard.addView(tvStatus)

        val rankCard = makeCard()
        rankCard.addView(sectionTitle("🏆 Streak Rankings"))
        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
                .also {
                    it.gravity =
                        android.view.Gravity.CENTER_HORIZONTAL
                }
        }
        rankCard.addView(progressBar)
        rankingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rankCard.addView(rankingLayout)

        root.addView(header)
        listOf(
            myCodeCard to 20,
            requestsCard to 16,
            addCard to 16,
            rankCard to 16
        ).forEach { (card, margin) ->
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(24, margin, 24, 0) }
            root.addView(card)
        }

        scroll.addView(root)
        setContentView(scroll)

        val pts = StreakManager.getStreakPoints(this)
        val emoji = StreakManager.getStreakEmoji(pts)
        tvMyStreak.text = "Your streak: $emoji $pts pts • " +
                StreakManager.getStreakLabel(pts)
    }

    data class UserStreak(
        val uid: String, val name: String,
        val points: Int, val isMe: Boolean
    )

    data class FriendRequest(
        val fromUid: String,
        val name: String,
        val docId: String
    )
}