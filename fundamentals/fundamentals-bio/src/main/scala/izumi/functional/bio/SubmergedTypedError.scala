package izumi.functional.bio

import izumi.reflect.TagK
import izumi.reflect.macrortti.LightTypeTag

/** Throwable wrapper used to submerge a typed error of arbitrary payload type into a
  * monofunctor `F[_]`'s Throwable channel. Discriminated by `TagK[F]` of the source
  * monofunctor so that handlers for the same `F` are mutually compatible, but handlers
  * for different `F`s cannot intercept each other's submerged errors.
  *
  * Unlike cats-mtl's `Handle.Submarine` (which uses a per-region `AnyRef` marker for
  * algebraic-effects-style scoping), this discriminator is structural: any
  * `SubmergedTypedError[F]` produced anywhere can be caught by any handler matching on
  * `TagK[F]` for the same `F`. That is the intended semantics for bifunctorization.
  *
  * `writableStackTrace = false` keeps construction cheap (~150ns on JDK 21, dominated by
  * the cause-field assignment when payload is a Throwable). Same trick as
  * `cats.mtl.Handle.Submarine`'s `NoStackTrace` and `izumi.functional.bio.TypedError`.
  */
final class SubmergedTypedError[F[_]] private[bio] (
  val tag: LightTypeTag,
  val payload: Any,
) extends RuntimeException(
      s"Submerged typed error of class=${payload.getClass.getName}: $payload",
      payload match { case t: Throwable => t; case _ => null },
      /* enableSuppression = */ true,
      /* writableStackTrace = */ false,
    )

object SubmergedTypedError {

  /** Construct a `SubmergedTypedError[F]` carrying `payload`. Idempotent: if `payload`
    * is already a `SubmergedTypedError[F]` with the same `TagK[F].tag`, returns it
    * unchanged (no double wrapping). A `SubmergedTypedError` of a *different* `F` is
    * NOT collapsed — that's the discriminator working as intended.
    */
  def apply[F[_]](payload: Any)(implicit tag: TagK[F]): SubmergedTypedError[F] =
    payload match {
      case existing: SubmergedTypedError[_] if existing.tag == tag.tag =>
        existing.asInstanceOf[SubmergedTypedError[F]]
      case _ =>
        new SubmergedTypedError[F](tag.tag, payload)
    }

  /** Extract the payload of a `SubmergedTypedError[F]` for *this* `F`. Returns `None`
    * for any other `Throwable`, including `SubmergedTypedError[G]` for a different `G`.
    */
  def unapply[F[_]](t: Throwable)(implicit tag: TagK[F]): Option[Any] =
    t match {
      case s: SubmergedTypedError[_] if s.tag == tag.tag => Some(s.payload)
      case _ => None
    }

}
