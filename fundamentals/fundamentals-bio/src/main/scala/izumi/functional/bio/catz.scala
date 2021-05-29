package izumi.functional.bio

/**
  * Automatic converters from BIO* hierarchy to equivalent cats & cats-effect classes.
  *
  * {{{
  *   import izumi.functional.bio.IO2
  *   import izumi.functional.bio.catz._
  *   import cats.effect.Sync
  *
  *   def divideByZero[F[+_, +_]: IO2]: F[Throwable, Int] = {
  *     Sync[F[Throwable, `?`]].delay(10 / 0)
  *   }
  * }}}
  */
object catz extends CatsConversions
