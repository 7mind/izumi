package leaderboard.api

import io.circe.syntax._
import izumi.functional.bio.BIO
import izumi.functional.bio.catz._
import leaderboard.model.UserProfile
import leaderboard.repo.{Profiles, Ranks}
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

final class ProfileApi[F[+_, +_]: BIO](
  dsl: Http4sDsl[F[Throwable, ?]],
  profiles: Profiles[F],
  ranks: Ranks[F],
) extends HttpApi[F] {
  import dsl._

  override def http: HttpRoutes[F[Throwable, ?]] = {
    HttpRoutes.of {
      case GET -> Root / "profile" / UUIDVar(userId) =>
        Ok(ranks.getRank(userId).map(_.asJson))

      case rq @ POST -> Root / "profile" / UUIDVar(userId) =>
        Ok(for {
          profile <- rq.decodeJson[UserProfile]
          _ <- profiles.setProfile(userId, profile)
        } yield ())
    }
  }
}
