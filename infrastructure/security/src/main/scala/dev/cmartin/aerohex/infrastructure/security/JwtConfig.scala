package dev.cmartin.aerohex.infrastructure.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import zio.{Chunk, Config, IO, ZIO}

case class JwtConfig(secretKey: String, ttlSeconds: Int, issuer: String, audience: String)

object JwtConfig {

  // RFC 7518 §3.2: an HS256 key must be at least as long as the hash output, 256 bits.
  val MinSecretBytes: Int = 32

  // Keys under `jwt` in application.conf (plans/configuration-zio-config.md). secret-key has no
  // default on purpose: there must be no hardcoded fallback secret.
  private val raw: Config[(Option[Config.Secret], Int, String, String)] =
    (Config.secret("secret-key").optional ++
      Config.int("ttl-seconds").withDefault(3600) ++
      Config.string("issuer").withDefault("aero-hex-ai") ++
      Config.string("audience").withDefault("aero-hex-ai-api"))
      .nested("jwt")

  /** Loads the config from the current `ConfigProvider`. Anyone who knows a
    * fixed fallback secret could forge tokens, so there is none:
    *   - `jwt.secret-key` unset: a random secret is generated for this process
    *     only, and a warning is logged. Tokens are invalidated on restart,
    *     which is fine for local development.
    *   - `jwt.secret-key` shorter than [[MinSecretBytes]] bytes: fails, so
    *     startup aborts.
    */
  val load: IO[Config.Error, JwtConfig] =
    ZIO.config(raw).flatMap { case (secret, ttlSeconds, issuer, audience) =>
      val key = secret.map(s => new String(s.value.toArray))
      key match {
        case None                                                                  =>
          ZIO.logWarning(
            "JWT_SECRET_KEY is not set: using a random per-process secret. Tokens will not " +
              "survive a restart or work across instances."
          ) *> ZIO.succeed(JwtConfig(randomSecret(), ttlSeconds, issuer, audience))
        case Some(s) if s.getBytes(StandardCharsets.UTF_8).length < MinSecretBytes =>
          ZIO.fail(
            Config.Error.InvalidData(Chunk("jwt", "secret-key"), s"must be at least $MinSecretBytes bytes for HS256")
          )
        case Some(s)                                                               =>
          ZIO.succeed(JwtConfig(s, ttlSeconds, issuer, audience))
      }
    }

  private def randomSecret(): String = {
    val bytes = new Array[Byte](MinSecretBytes)
    new SecureRandom().nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }
}
