package dev.cmartin.aerohex.infrastructure.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import zio.{IO, ZIO}

// Same pattern as messaging-kafka's KafkaConfig: values read directly from the environment (there
// is no HOCON-parsing library anywhere in this project — bootstrap's application.conf documents the
// available env vars but nothing actually reads it, see plans/security/login.md's "discovered
// during implementation" note).
case class JwtConfig(secretKey: String, ttlSeconds: Int, issuer: String, audience: String)

object JwtConfig {

  // RFC 7518 §3.2: an HS256 key must be at least as long as the hash output, 256 bits.
  val MinSecretBytes: Int = 32

  /** Reads the config from the environment (`env` is a parameter so tests can
    * pass their own). There is no hardcoded fallback secret, because anyone who
    * knows it could forge tokens:
    *   - `JWT_SECRET_KEY` unset: a random secret is generated for this process
    *     only, and a warning is logged. Tokens are invalidated on restart,
    *     which is fine for local development.
    *   - `JWT_SECRET_KEY` set but shorter than [[MinSecretBytes]] bytes: fails,
    *     so startup aborts.
    */
  def fromEnv(env: String => Option[String] = sys.env.get): IO[IllegalArgumentException, JwtConfig] =
    for {
      secret <- env("JWT_SECRET_KEY") match {
                  case None                                                                  =>
                    ZIO.logWarning(
                      "JWT_SECRET_KEY is not set: using a random per-process secret. Tokens will not " +
                        "survive a restart or work across instances."
                    ) *> ZIO.succeed(randomSecret())
                  case Some(s) if s.getBytes(StandardCharsets.UTF_8).length < MinSecretBytes =>
                    ZIO.fail(
                      new IllegalArgumentException(
                        s"JWT_SECRET_KEY must be at least $MinSecretBytes bytes for HS256."
                      )
                    )
                  case Some(s)                                                               => ZIO.succeed(s)
                }
    } yield JwtConfig(
      secretKey = secret,
      ttlSeconds = env("JWT_TTL_SECONDS").flatMap(_.toIntOption).getOrElse(3600),
      issuer = env("JWT_ISSUER").getOrElse("aero-hex-ai"),
      audience = env("JWT_AUDIENCE").getOrElse("aero-hex-ai-api")
    )

  private def randomSecret(): String = {
    val bytes = new Array[Byte](MinSecretBytes)
    new SecureRandom().nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }
}
