package dev.cmartin.aerohex.infrastructure.security

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, TokenService}
import java.time.Clock
import java.util.UUID
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtOptions}
import zio.{IO, UIO, URLayer, ZIO, ZLayer}

/** Implements `TokenService` via `jwt-scala`/`jwt-circe`, HS256 (see
  * `plans/security/login.md` decision 9 for why HS256 over RS256 at this
  * stage). `clock` is a constructor parameter, not `Clock.systemUTC` baked in
  * directly, so `JwtServiceSpec` can inject a fixed clock for deterministic
  * expiry assertions.
  */
final class JwtService(config: JwtConfig, clock: Clock = Clock.systemUTC) extends TokenService {

  private given Clock = clock

  // RFC 7519 registered claims only (plans/security/login.md decision 8) — iss/sub/aud/exp/
  // nbf/iat/jti, no private claims yet.
  override def generate(username: String): UIO[AccessToken] =
    ZIO.succeed {
      val claim = pdi.jwt.JwtClaim()
        .by(config.issuer)
        .to(config.audience)
        .about(username)
        .withId(UUID.randomUUID().toString)
        .issuedNow
        .startsNow
        .expiresIn(config.ttlSeconds.toLong)
      AccessToken(JwtCirce.encode(claim, config.secretKey, JwtAlgorithm.HS256), config.ttlSeconds)
    }

  // Decodes with exp/nbf checks disabled so a genuinely expired-but-validly-signed token can
  // still be inspected, then checks time bounds and iss/aud manually against `clock` — this
  // avoids depending on jwt-scala's internal exception hierarchy to distinguish "expired" from
  // "otherwise invalid" (plans/security/login.md's "gap to flag for later" note on iss/aud not
  // being auto-checked applies here too, handled by the explicit checks below).
  override def validate(token: String): IO[DomainError, String] =
    ZIO
      .fromTry(
        JwtCirce.decode(
          token,
          config.secretKey,
          Seq(JwtAlgorithm.HS256),
          JwtOptions(signature = true, expiration = false, notBefore = false)
        )
      )
      .orElseFail(DomainError.InvalidToken("malformed token or bad signature"))
      .flatMap { claim =>
        val now = clock.instant().getEpochSecond
        if claim.expiration.exists(_ < now) then ZIO.fail(DomainError.TokenExpired)
        else if claim.notBefore.exists(_ > now) then ZIO.fail(DomainError.InvalidToken("token not yet valid"))
        else if !claim.issuer.contains(config.issuer) || !claim.audience.exists(_.contains(config.audience))
        then ZIO.fail(DomainError.InvalidToken("issuer or audience mismatch"))
        else ZIO.fromOption(claim.subject).orElseFail(DomainError.InvalidToken("missing subject claim"))
      }
}

object JwtService {
  val layer: URLayer[JwtConfig, TokenService] =
    ZLayer.fromFunction((config: JwtConfig) => new JwtService(config))
}
