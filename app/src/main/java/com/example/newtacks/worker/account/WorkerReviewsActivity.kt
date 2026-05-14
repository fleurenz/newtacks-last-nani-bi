package com.example.newtacks.worker.account

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.newtacks.R
import com.example.newtacks.models.Review
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkerReviewsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var reviewContainer: LinearLayout
    private lateinit var tvSeeAllReviews: TextView
    private lateinit var filterAll: TextView
    private lateinit var filter5: TextView
    private lateinit var filter4: TextView
    private lateinit var filter3: TextView
    private lateinit var filter2: TextView
    private lateinit var filter1: TextView

    private var isShowingAllReviews = false
    private var currentRatingFilter: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_reviews)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        reviewContainer  = findViewById(R.id.reviewContainer)
        tvSeeAllReviews  = findViewById(R.id.tvSeeAllReviews)
        filterAll        = findViewById(R.id.filterAll)
        filter5          = findViewById(R.id.filter5)
        filter4          = findViewById(R.id.filter4)
        filter3          = findViewById(R.id.filter3)
        filter2          = findViewById(R.id.filter2)
        filter1          = findViewById(R.id.filter1)

        setupFilters()
        setupSeeAll()
        loadReviews()
    }

    private fun setupFilters() {
        val filters = listOf(filterAll, filter5, filter4, filter3, filter2, filter1)
        val ratings = listOf(null, 5, 4, 3, 2, 1)

        filters.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                currentRatingFilter = ratings[index]
                isShowingAllReviews = false
                tvSeeAllReviews.text = "See all"

                filters.forEach {
                    it.setBackgroundResource(R.drawable.bg_badge_white)
                    it.setTextColor(android.graphics.Color.parseColor("#64748B"))
                    it.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                textView.setBackgroundResource(R.drawable.bg_badge_blue)
                textView.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                textView.setTypeface(null, android.graphics.Typeface.BOLD)

                loadReviews()
            }
        }
    }

    private fun setupSeeAll() {
        tvSeeAllReviews.setOnClickListener {
            isShowingAllReviews = !isShowingAllReviews
            tvSeeAllReviews.text = if (isShowingAllReviews) "Show less" else "See all"
            loadReviews()
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun loadReviews() {
        val uid = auth.currentUser?.uid ?: return

        var query = firestore.collection("reviews")
            .whereEqualTo("workerId", uid)

        currentRatingFilter?.let {
            query = query.whereEqualTo("rating", it.toDouble())
        }

        query.get().addOnSuccessListener { snapshot ->
            reviewContainer.removeAllViews()

            if (snapshot.isEmpty) {
                val empty = TextView(this)
                empty.text = if (currentRatingFilter == null)
                    "No reviews yet."
                else
                    "No $currentRatingFilter star reviews yet."
                empty.textSize = 13f
                empty.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                reviewContainer.addView(empty)
                tvSeeAllReviews.visibility = View.GONE
                return@addOnSuccessListener
            }

            val allReviews    = snapshot.documents
            val displayReviews = if (isShowingAllReviews) allReviews else allReviews.take(10)

            tvSeeAllReviews.visibility = if (allReviews.size > 10) View.VISIBLE else View.GONE

            for (doc in displayReviews) {
                val review     = doc.toObject(Review::class.java) ?: continue
                val clientName = if (review.isAnonymous) "Anonymous User" else review.clientName
                val comment    = review.comment
                val rating     = review.rating

                val card = layoutInflater.inflate(
                    R.layout.item_review_card,
                    reviewContainer,
                    false
                )
                card.findViewById<TextView>(R.id.tvReviewRating).text  = "⭐ %.1f".format(rating)
                card.findViewById<TextView>(R.id.tvReviewClient).text  = clientName
                card.findViewById<TextView>(R.id.tvReviewComment).text = comment
                reviewContainer.addView(card)
            }
        }
    }
}