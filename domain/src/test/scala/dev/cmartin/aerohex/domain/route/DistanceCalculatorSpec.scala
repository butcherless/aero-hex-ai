package dev.cmartin.aerohex.domain.route

import zio.test.*

object DistanceCalculatorSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment, Any] =
    suite("DistanceCalculator")(
      test("computes a known distance within tolerance (Madrid <-> Barcelona, ~483 km)") {
        val distance = DistanceCalculator.haversineKm(40.4719, -3.5626, 41.2971, 2.0785)
        assertTrue(Math.abs(distance - 483.0) <= 5.0)
      },
      test("returns 0 when origin and destination are the same point") {
        val distance = DistanceCalculator.haversineKm(40.4719, -3.5626, 40.4719, -3.5626)
        assertTrue(distance == 0.0)
      }
    )
