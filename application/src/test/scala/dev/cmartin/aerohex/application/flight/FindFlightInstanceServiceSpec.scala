package dev.cmartin.aerohex.application.flight

import FlightInstanceRepositoryStub.{stubFlightInstanceRepo, unimplementedFlightInstanceRepo}
import dev.cmartin.aerohex.domain.aircraft.Registration
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{FindFlightInstanceUseCase, FlightCode, FlightInstance, FlightInstanceId}
import dev.cmartin.aerohex.shared.Pagination
import java.time.LocalDateTime
import java.util.UUID
import zio.test.*
import zio.{Scope, ZIO, ZLayer}

object FindFlightInstanceServiceSpec extends ZIOSpecDefault:

  private val instanceId = FlightInstanceId(UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f01234567890"))

  private val instance = FlightInstance(
    instanceId,
    LocalDateTime.of(2026, 7, 13, 7, 5),
    LocalDateTime.of(2026, 7, 13, 8, 55),
    FlightCode("UX9117"),
    Registration("EC-MIG")
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FindFlightInstanceService")(
      test("returns the flight instance when found by a valid id") {
        val repo = stubFlightInstanceRepo(onFindById = _ => ZIO.some(instance))
        for result <- new FindFlightInstanceService(repo).findById(instanceId.value.toString)
        yield assertTrue(result == instance)
      },
      test("fails with FlightInstanceNotFound when the repository has no match") {
        val repo = stubFlightInstanceRepo(onFindById = _ => ZIO.none)
        for error <- new FindFlightInstanceService(repo).findById(instanceId.value.toString).flip
        yield assertTrue(error == DomainError.FlightInstanceNotFound(instanceId.value.toString))
      },
      test("fails with FlightInstanceNotFound when the id is not a valid UUID") {
        val repo = stubFlightInstanceRepo()
        for error <- new FindFlightInstanceService(repo).findById("not-a-uuid").flip
        yield assertTrue(error == DomainError.FlightInstanceNotFound("not-a-uuid"))
      },
      test("findAll delegates to the repository unchanged") {
        val repo = stubFlightInstanceRepo(onFindAll = _ => ZIO.succeed(List(instance)))
        for result <- new FindFlightInstanceService(repo).findAll(Pagination(1, 20))
        yield assertTrue(result == List(instance))
      },
      test("FindFlightInstanceService.layer constructs a usable instance") {
        for _ <- ZIO
                   .service[FindFlightInstanceUseCase]
                   .provide(ZLayer.succeed(unimplementedFlightInstanceRepo), FindFlightInstanceService.layer)
        yield assertCompletes
      }
    )
