package izumi.fundamentals.assertions

trait Assertions {
  inline def assert(inline condition: Boolean): Unit = ${ AssertionMacro.expand('condition, '{ AssertionContext.standard }, '{ this }) }
  inline def assert(inline condition: Boolean, inline context: AssertionContext): Unit = ${ AssertionMacro.expand('condition, 'context, '{ this }) }
  inline def assert1[F[_]](inline condition: Boolean)(implicit suspension: AssertionSuspension1[F]): F[Unit] =
    ${ AssertionMacro.unary('condition, '{ AssertionContext.standard }, '{ this }, 'suspension) }
  inline def assert1[F[_]](inline condition: Boolean, inline context: AssertionContext)(implicit suspension: AssertionSuspension1[F]): F[Unit] =
    ${ AssertionMacro.unary('condition, 'context, '{ this }, 'suspension) }
  inline def assert2[F[_, _]](inline condition: Boolean)(implicit suspension: AssertionSuspension2[F]): F[Nothing, Unit] =
    ${ AssertionMacro.binary('condition, '{ AssertionContext.standard }, '{ this }, 'suspension) }
  inline def assert2[F[_, _]](inline condition: Boolean, inline context: AssertionContext)(implicit suspension: AssertionSuspension2[F]): F[Nothing, Unit] =
    ${ AssertionMacro.binary('condition, 'context, '{ this }, 'suspension) }
}

object Assert extends Assertions
