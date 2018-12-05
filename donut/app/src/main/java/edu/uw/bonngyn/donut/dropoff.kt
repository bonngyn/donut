package edu.uw.bonngyn.donut

import java.util.Date

data class GeoPoint (
    var latitude: Number?,
    var longitude: Number?
)

data class Dropoff (
    var title: String,
    var description: String?,
    var location: GeoPoint,
    var delivered: Boolean,
    var timestamp: Date
)
