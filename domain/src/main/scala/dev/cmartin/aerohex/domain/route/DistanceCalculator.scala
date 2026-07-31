package dev.cmartin.aerohex.domain.route

// Pure, zero-I/O great-circle distance between two points, used to populate Route.distanceKm from
// each endpoint airport's stored latitude/longitude (see RouteSync in master-data-sync).
object DistanceCalculator:
  private val EarthRadiusKm = 6371.0

  def haversineKm(originLat: Double, originLon: Double, destLat: Double, destLon: Double): Double =
    val dLat = Math.toRadians(destLat - originLat)
    val dLon = Math.toRadians(destLon - originLon)
    val a    =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(originLat)) * Math.cos(Math.toRadians(destLat)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    EarthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
