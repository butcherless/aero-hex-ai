package dev.cmartin.aerohex.infrastructure.security

// Same pattern as messaging-kafka's KafkaConfig: a default read directly from sys.env (there is
// no HOCON-parsing library anywhere in this project — bootstrap's application.conf documents the
// available env vars but nothing actually reads it, see plans/security/login.md's "discovered
// during implementation" note), mirrored into application.conf purely for that documentation
// purpose.
case class JwtConfig(secretKey: String, ttlSeconds: Int, issuer: String, audience: String)

object JwtConfig {
  val default: JwtConfig = JwtConfig(
    secretKey = sys.env.getOrElse("JWT_SECRET_KEY", "dev-only-change-me"),
    ttlSeconds = sys.env.get("JWT_TTL_SECONDS").flatMap(_.toIntOption).getOrElse(3600),
    issuer = sys.env.getOrElse("JWT_ISSUER", "aero-hex-ai"),
    audience = sys.env.getOrElse("JWT_AUDIENCE", "aero-hex-ai-api")
  )
}
