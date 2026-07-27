-- Seed data: one dev user, for manual login testing (no registration endpoint exists yet — see
-- plans/security/login.md decision 5). Matches the V16 schema (users.username UNIQUE NOT NULL,
-- password_hash CHAR(60) NOT NULL).
--
-- Credentials: username "admin", password "ChangeMe123!" — dev-only, change before any shared
-- or production use. The hash below was generated with jbcrypt itself (BCrypt.hashpw, cost 10)
-- via `sbt security/console`, not a different bcrypt implementation, to guarantee
-- BcryptPasswordHasher.verify accepts it.
--
-- Idempotent: ON CONFLICT (username) DO NOTHING, safe to re-run.

INSERT INTO users (username, password_hash) VALUES
  ('admin', '$2a$10$EIX.wpA4zhI8M4woo0rJf.6qNWUcdm7SIv2bcgpxW5BZrzw8cCEy.')
ON CONFLICT (username) DO NOTHING;
