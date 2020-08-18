package leaderboard.model

final case class QueryFailure(queryName: String, cause: Throwable)
  extends RuntimeException(
    s"""Query "$queryName" failed with ${cause.getMessage}""",
    cause,
  )
