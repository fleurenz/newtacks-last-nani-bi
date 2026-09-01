package com.example.newtacks.models

data class HiringPost(
    val hiringId: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val companyAddress: String = "",
    
    val jobTitle: String = "",
    val serviceCategories: List<String> = emptyList(),
    val employmentType: String = "", // PART_TIME, FULL_TIME, PROJECT
    
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    
    val dailyRate: Double = 0.0,
    
    val status: String = "OPEN",
    val applicants: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0
)