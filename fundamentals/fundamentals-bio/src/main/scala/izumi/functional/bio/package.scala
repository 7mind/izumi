package izumi.functional

import izumi.functional.bio.BIOSyntax.FSummoner
import izumi.functional.mono.{Clock, Entropy, SyncSafe}

package object bio extends BIOSyntax {

  /**
   * A convenient dependent summoner for BIO* hierarchy.
   * Auto-narrows to the most powerful available class:
   *
   * {{{
   *   def y[F[+_, +_]: BIOAsync] = {
   *     F.timeout(5.seconds)(F.forever(F.unit))
   *   }
   * }}}
   *
   * */
//  @inline final def F[F[+_, +_]](implicit F: BIOFunctor[F]): F.type = F
  override final object F extends FSummoner

  /**
   * Automatic converters from BIO* hierarchy to equivalent cats & cats-effect classes.
   */
  override final object catz extends BIOCatsConversions

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _] : SyncSafe2]: SyncSafe2[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
  object Clock2 {
    def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
  object Entropy2 {
    def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
}

