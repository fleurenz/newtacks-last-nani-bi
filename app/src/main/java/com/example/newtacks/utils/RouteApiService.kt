package com.example.newtacks.utils

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RouteApiService {
    @GET("route/v1/driving/{coordinates}")
    fun getRoute(
        @Path("coordinates") coordinates: String, // format: "lng1,lat1;lng2,lat2"
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "polyline"
    ): Call<OsrmResponse>
}

data class OsrmResponse(
    val routes: List<OsrmRoute>
)

data class OsrmRoute(
    val geometry: String, // Encoded polyline
    val distance: Double,
    val duration: Double
)