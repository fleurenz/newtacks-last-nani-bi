package com.example.newtacks.client

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.newtacks.R
import java.util.Locale

class PaymentActivity : AppCompatActivity() {

    private var amount: Double = 0.0
    private var jobTitle: String = ""
    private var workerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        amount = intent.getDoubleExtra("AMOUNT", 0.0)
        jobTitle = intent.getStringExtra("JOB_TITLE") ?: "Job Service"
        workerName = intent.getStringExtra("WORKER_NAME") ?: "Worker"

        findViewById<TextView>(R.id.tvPaymentJobTitle).text = jobTitle
        findViewById<TextView>(R.id.tvPaymentWorkerName).text = "Worker: $workerName"
        
        val amountStr = String.format(Locale.getDefault(), "₱%.2f", amount)
        findViewById<TextView>(R.id.tvPaymentAmount).text = amountStr
        
        val btnPay = findViewById<Button>(R.id.btnPayNow)
        btnPay.text = "Pay $amountStr Now"

        val rgMethods = findViewById<RadioGroup>(R.id.rgPaymentMethods)
        val layoutCard = findViewById<View>(R.id.layoutCardDetails)

        rgMethods.setOnCheckedChangeListener { _, checkedId ->
            layoutCard.visibility = if (checkedId == R.id.rbCard) View.VISIBLE else View.GONE
        }

        btnPay.setOnClickListener {
            processMockPayment()
        }
    }

    private fun processMockPayment() {
        val loading = findViewById<View>(R.id.paymentLoadingOverlay)
        val success = findViewById<View>(R.id.layoutSuccess)
        
        loading.visibility = View.VISIBLE
        
        // Simulate network delay
        Handler(Looper.getMainLooper()).postDelayed({
            loading.visibility = View.GONE
            success.visibility = View.VISIBLE
            
            // Wait 2 seconds then finish with result
            Handler(Looper.getMainLooper()).postDelayed({
                setResult(RESULT_OK)
                finish()
            }, 2000)
            
        }, 2500)
    }
}