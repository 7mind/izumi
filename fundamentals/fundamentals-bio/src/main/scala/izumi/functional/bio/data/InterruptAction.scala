package izumi.functional.bio.data

/**
  * @param interrupt May *semantically* block until the target computation either finishes completely or finishes running
  *                  its finalizers, depending on the underlying effect type.
  *                  Will not block if the effect type does not support semantic (async) blocking.
  */
final case class InterruptAction[F[_, _]](interrupt: F[Nothing, Unit]) extends AnyVal
