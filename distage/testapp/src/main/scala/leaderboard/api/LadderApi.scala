package leaderboard.api

import io.circe.syntax._
import izumi.functional.bio.BIO
import leaderboard.repo.Ladder
import izumi.functional.bio.catz._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._

final class LadderApi[F[+_, +_]: BIO](
  dsl: Http4sDsl[F[Throwable, ?]],
  ladder: Ladder[F],
) extends HttpApi[F] {
  import dsl._

  override def http: HttpRoutes[F[Throwable, ?]] = {
    HttpRoutes.of {
      case GET -> Root / "ladder" =>
        Ok(ladder.getScores.map(_.asJson))

      case POST -> Root / "ladder" / UUIDVar(userId) / LongVar(score) =>
        Ok(ladder.submitScore(userId, score))
    }
  }
}
