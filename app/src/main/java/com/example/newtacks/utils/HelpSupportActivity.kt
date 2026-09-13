package com.example.newtacks.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.example.newtacks.R

class HelpSupportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_support)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        // Robust Inset Handling
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarSpacer.layoutParams.height = systemBars.top
            statusBarSpacer.requestLayout()
            insets
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val rvFaq = findViewById<RecyclerView>(R.id.rvFaq)
        val faqs = listOf(
            FaqItem(
                "How do I request a fix?",
                "Navigate to the 'Home' screen, select a service category (like Plumbing or Electrical), fill out the job details including location and photos, and tap 'Submit Request'. A worker will accept your request soon!"
            ),
            FaqItem(
                "How do I join as a worker?",
                "During signup, choose the 'Worker' role. You'll need to provide your skills, years of experience, and any NC certificates (NC1, NC2, or NC3) to get verified and start accepting jobs."
            ),
            FaqItem(
                "What if a worker doesn't show up?",
                "If a worker hasn't arrived after the scheduled time, you can contact them directly via the 'Message' button in your request. If they still don't respond, you can cancel the request and report the issue to us."
            ),
            FaqItem(
                "How do payments work?",
                "Currently, payments are handled directly between the client and the worker. You can see the estimated rate in the job details, but final payment is settled once the work is confirmed as 'Done'."
            ),
            FaqItem(
                "How do I update my profile?",
                "Go to the 'Account' tab and tap 'Edit Profile'. From there, you can change your name, phone number, address, and profile picture. Workers can also update their skills and experience."
            )
        )

        rvFaq.layoutManager = LinearLayoutManager(this)
        rvFaq.adapter = FaqAdapter(faqs)
    }

    data class FaqItem(val question: String, val answer: String, var isExpanded: Boolean = false)

    class FaqAdapter(private val items: List<FaqItem>) : RecyclerView.Adapter<FaqAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvQuestion: TextView = view.findViewById(R.id.tvQuestion)
            val tvAnswer: TextView = view.findViewById(R.id.tvAnswer)
            val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
            val layoutAnswer: LinearLayout = view.findViewById(R.id.layoutAnswer)
            val layoutQuestion: View = view.findViewById(R.id.layoutQuestion)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_faq, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvQuestion.text = item.question
            holder.tvAnswer.text = item.answer

            // Reflect current state without animating (covers scroll recycling / re-bind)
            holder.layoutAnswer.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            holder.ivArrow.rotation = if (item.isExpanded) 270f else 90f

            holder.layoutQuestion.setOnClickListener {
                item.isExpanded = !item.isExpanded

                // Animate only this card's own root, not the whole RecyclerView,
                // and do NOT call notifyItemChanged() here - that would force an
                // instant rebind and skip the animation entirely.
                val cardRoot = holder.itemView as ViewGroup
                TransitionManager.beginDelayedTransition(cardRoot)

                holder.layoutAnswer.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
                holder.ivArrow.animate()
                    .rotation(if (item.isExpanded) 270f else 90f)
                    .setDuration(200)
                    .start()
            }
        }

        override fun getItemCount() = items.size
    }
}