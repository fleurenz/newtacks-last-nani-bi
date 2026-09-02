package com.example.newtacks.models

sealed class FeedOpportunity {
    data class ClientJob(val job: Job) : FeedOpportunity()
    data class CompanyHiring(val post: HiringPost) : FeedOpportunity()
    data class ActiveJob(val job: Job) : FeedOpportunity()

    val title: String
        get() = when (this) {
            is ClientJob -> job.jobTitle
            is CompanyHiring -> post.jobTitle
            is ActiveJob -> job.jobTitle
        }

    val amount: String
        get() = when (this) {
            is ClientJob -> "₱${job.offeredAmount}"
            is CompanyHiring -> "₱${post.dailyRate}/day"
            is ActiveJob -> "₱${job.offeredAmount} (Active)"
        }

    val location: String
        get() = when (this) {
            is ClientJob -> job.clientAddress
            is CompanyHiring -> post.companyAddress
            is ActiveJob -> job.clientAddress
        }
        
    val id: String
        get() = when (this) {
            is ClientJob -> job.jobId
            is CompanyHiring -> post.hiringId
            is ActiveJob -> job.jobId
        }

    val latitude: Double
        get() = when (this) {
            is ClientJob -> job.latitude
            is CompanyHiring -> post.latitude
            is ActiveJob -> job.latitude
        }

    val longitude: Double
        get() = when (this) {
            is ClientJob -> job.longitude
            is CompanyHiring -> post.longitude
            is ActiveJob -> job.longitude
        }
}
