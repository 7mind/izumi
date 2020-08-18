package leaderboard.repo

import distage.DIResource
import izumi.functional.bio.{BIOApplicative, BIOPrimitives, F}
import leaderboard.model.{QueryFailure, UserId, UserProfile}

trait Profiles[F[_, _]] {
  def setProfile(userId: UserId, profile: UserProfile): F[QueryFailure, Unit]
  def getProfile(userId: UserId): F[QueryFailure, Option[UserProfile]]
}

object Profiles {
  final class Dummy[F[+_, +_]: BIOApplicative: BIOPrimitives]
    extends DIResource.LiftF[F[Nothing, ?], Profiles[F]](for {
      state <- F.mkRef(Map.empty[UserId, UserProfile])
    } yield {
      new Profiles[F] {
        override def setProfile(userId: UserId, profile: UserProfile): F[Nothing, Unit] =
          state.update_(_ + (userId -> profile))

        override def getProfile(userId: UserId): F[Nothing, Option[UserProfile]] =
          state.get.map(_.get(userId))
      }
    })
}
