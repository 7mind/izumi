package com.github.pshirshov.izumi.functional

import com.github.pshirshov.izumi.functional.bio.BIOSyntax.FSummoner
import com.github.pshirshov.izumi.functional.mono.SyncSafe

package object bio extends BIOSyntax {
  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _] : SyncSafe2]: SyncSafe2[F] = implicitly
  }

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

}
