package leaderboard.model

import io.circe.{Codec, derivation}

final case class RankedProfile(
  name: String,
  description: String,
  rank: Int,
  score: Score,
)

object RankedProfile {
  implicit val codec: Codec.AsObject[RankedProfile] = derivation.deriveCodec
}
