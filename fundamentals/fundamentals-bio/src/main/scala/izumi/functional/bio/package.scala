package izumi.functional

import izumi.functional.bio.syntax.{BIO3Syntax, BIOSyntax}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}

package object bio extends BIOSyntax with BIO3Syntax {

  /**
    * A convenient dependent summoner for BIO* hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   def y[F[+_, +_]: BIOTemporal] = {
    *     F.timeout(5.seconds)(F.forever(F.unit))
    *   }
    * }}}
    *
    */
  @inline override final def F[F[+_, +_]](implicit F: BIOFunctor[F]): F.type = F
  @inline override final def FR[FR[-_, +_, +_]](implicit FR: BIOFunctor3[FR]): FR.type = FR

  /**
    * NOTE: The left type parameter is not forced to be covariant
    * because [[BIOFunctor]] does not yet expose any operations
    * on it.
    **/
  type BIOFunctor[F[_, +_]] = instances.BIOFunctor3[Lambda[(`-R`, `E`, `+A`) => F[E, A]]]
  type BIOFunctor3[F[-_, +_, +_]] = instances.BIOFunctor3[F]

  type BIOBifunctor[F[+_, +_]] = instances.BIOBifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOBifunctor3[F[-_, +_, +_]] = instances.BIOBifunctor3[F]

  type BIOApplicative[F[+_, +_]] = instances.BIOApplicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOApplicative3[F[-_, +_, +_]] = instances.BIOApplicative3[F]

  type BIOGuarantee[F[+_, +_]] = instances.BIOGuarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOGuarantee3[F[-_, +_, +_]] = instances.BIOGuarantee3[F]

  type BIOError[F[+_, +_]] = instances.BIOError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOError3[F[-_, +_, +_]] = instances.BIOError3[F]

  type BIOMonad[F[+_, +_]] = instances.BIOMonad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOMonad3[F[-_, +_, +_]] = instances.BIOMonad3[F]

  type BIOMonadError[F[+_, +_]] = instances.BIOMonadError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOMonadError3[F[-_, +_, +_]] = instances.BIOMonadError3[F]

  type BIOBracket[F[+_, +_]] = instances.BIOBracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOBracket3[F[-_, +_, +_]] = instances.BIOBracket3[F]

  type BIOPanic[F[+_, +_]] = instances.BIOPanic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOPanic3[F[-_, +_, +_]] = instances.BIOPanic3[F]

  type BIO[F[+_, +_]] = instances.BIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIO3[F[-_, +_, +_]] = instances.BIO3[F]

  type BIOAsync[F[+_, +_]] = instances.BIOAsync3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOAsync3[F[-_, +_, +_]] = instances.BIOAsync3[F]

  type BIOTemporal[F[+_, +_]] = instances.BIOTemporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOTemporal3[F[-_, +_, +_]] = instances.BIOTemporal3[F]

  type BIOFork[F[+_, +_]] = instances.BIOFork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOFork3[F[-_, +_, +_]] = instances.BIOFork3[F]

  type BIOLatch[F[+_, +_]] = BIOPromise[F, Nothing, Unit]
  type BIOFiber[F[+_, +_], E, A] = BIOFiber3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]], Any, E, A]

  type BlockingIO[F[_, _]] = BlockingIO3[Lambda[(R, E, A) => F[E, A]]]
  object BlockingIO {
    def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly
  }

  type BIOPrimitives[F[+_, +_]] = instances.BIOPrimitives3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOPrimitives3[F[-_, +_, +_]] = instances.BIOPrimitives3[F]

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe[F[Any, Nothing, ?]]
  object SyncSafe3 {
    def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
  object Clock2 {
    def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock[F[Any, Nothing, ?]]
  object Clock3 {
    def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
  object Entropy2 {
    def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy[F[Any, Nothing, ?]]
  object Entropy3 {
    def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }
}
