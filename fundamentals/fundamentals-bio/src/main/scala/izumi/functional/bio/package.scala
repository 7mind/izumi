package izumi.functional

import izumi.functional.bio.syntax.{BIO3Syntax, BIOSyntax}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}

import scala.annotation.implicitAmbiguous

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
  @inline override final def F[FR[-_, +_, +_]](implicit FR: bio.BIOFunctor3[FR]): FR.type = FR

  /**
    * NOTE: The left type parameter is not forced to be covariant
    * because [[BIOFunctor]] does not yet expose any operations
    * on it.
    **/
  type BIOFunctor[F[+_, +_]] = bio.BIOFunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOBifunctor[F[+_, +_]] = BIOBifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOApplicative[F[+_, +_]] = BIOApplicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOGuarantee[F[+_, +_]] = BIOGuarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOError[F[+_, +_]] = BIOError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOMonad[F[+_, +_]] = BIOMonad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOMonadError[F[+_, +_]] = BIOMonadError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOBracket[F[+_, +_]] = BIOBracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOPanic[F[+_, +_]] = BIOPanic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIO[F[+_, +_]] = BIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOAsync[F[+_, +_]] = BIOAsync3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOTemporal[F[+_, +_]] = BIOTemporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOFork[F[+_, +_]] = BIOFork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOLatch[F[+_, +_]] = BIOPromise[F, Nothing, Unit]
  type BIOFiber[F[+_, +_], +E, +A] = BIOFiber3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]], E, A]

  type BlockingIO[F[_, _]] = BlockingIO3[Lambda[(R, E, A) => F[E, A]]]
  object BlockingIO {
    def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly
  }

//  type BIOPrimitives1[F[+_]] = instances.BIOPrimitives[Lambda[(`+E`, `+A`) => F[A]]]
  type BIOPrimitives3[F[-_, +_, +_]] = BIOPrimitives[F[Any, +?, +?]]

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

  trait =!=[A, B] extends Serializable
  object =!= {
    implicit def neq[A, B] : A =!= B = new =!=[A, B] {}
    @implicitAmbiguous("Can not prove that type ${A} not equal ${A}.")
    implicit def neqAmbig1[A] : A =!= A = new =!=[A, A] {}
    implicit def neqAmbig2[A] : A =!= A = new =!=[A, A] {}
  }
}
