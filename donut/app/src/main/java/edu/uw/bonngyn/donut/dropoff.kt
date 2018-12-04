package edu.uw.bonngyn.donut

data class GeoPoint (
    var latitude: Number?,
    var longitude: Number?
)

data class Dropoff (
    var title: String,
    var description: String?,
    var location: GeoPoint,
    var delivered: Boolean
)
