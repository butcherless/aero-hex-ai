package dev.cmartin.aerohex.application.auth

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, LoginUseCase, PasswordHasher, TokenService, UserRepository}
import zio.{IO, URLayer, ZIO, ZLayer}

final class LoginService(
    userRepo: UserRepository,
    passwordHasher: PasswordHasher,
    tokenService: TokenService
) extends LoginUseCase {

  // Unknown username and wrong password both collapse to the same InvalidCredentials — never
  // distinguished in the response, to avoid a user-enumeration oracle (plans/security/login.md
  // decision 6). They also take the same time: an unknown username still pays for one hash
  // check (simulateVerify), or the fast reply would give the username away.
  override def login(username: String, password: String): IO[DomainError, AccessToken] =
    (for {
      user  <- userRepo.findByUsername(username).flatMap {
                 case Some(u) => ZIO.succeed(u)
                 case None    =>
                   passwordHasher.simulateVerify(password) *> ZIO.fail(DomainError.InvalidCredentials)
               }
      valid <- passwordHasher.verify(password, user.passwordHash)
      _     <- if valid then ZIO.unit else ZIO.fail(DomainError.InvalidCredentials)
      token <- tokenService.generate(user.username)
    } yield token) @@ ServiceAspect.logged(s"LoginService.login($username)")
}

object LoginService {
  val layer: URLayer[UserRepository & PasswordHasher & TokenService, LoginUseCase] =
    ZLayer.fromFunction(new LoginService(_, _, _))
}
