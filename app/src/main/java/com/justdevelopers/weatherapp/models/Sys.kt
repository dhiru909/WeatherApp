package com.justdevelopers.weatherapp.models

import java.io.Serializable

data class Sys(
    val country: String,
    val sunrise: Int,
    val sunset: Int
):Serializable