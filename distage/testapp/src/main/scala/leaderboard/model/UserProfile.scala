package leaderboard.model

import io.circe.{Codec, derivation}

final case class UserProfile(
  name: String,
  description: String,
)

object UserProfile {
  implicit val codec: Codec.AsObject[UserProfile] = derivation.deriveCodec
}
