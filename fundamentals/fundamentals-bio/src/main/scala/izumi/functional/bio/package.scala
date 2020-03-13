package izumi.functional

import izumi.functional.bio.impl.BIOAsyncZio
import izumi.functional.bio.instances.BIOForkInstances.BIOForkZio
import izumi.functional.bio.instances.BIOPrimitives
import izumi.functional.bio.syntax.{BIO3Syntax, BIOSyntax}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}
import zio.ZIO

import scala.language.implicitConversions

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
  trait BIOFunctor[F[_, +_]] extends instances.BIOFunctor3[Lambda[(`-R`, E, `+A`) => F[E, A]]] with BIOFunctorInstances
  private[bio] sealed trait BIOFunctorInstances
  object BIOFunctorInstances {
    @inline implicit final def BIOZIO[R]: BIOAsync[ZIO[R, +?, +?]] = BIOAsyncZio.asInstanceOf[BIOAsync[ZIO[R, +?, +?]]]

    @inline implicit final def AttachBIOPrimitives[F[+_, +_]](
      @deprecated("unused", "") self: BIOFunctor[F]
    )(implicit BIOPrimitives: BIOPrimitives[F]): BIOPrimitives.type = BIOPrimitives

    @inline implicit final def AttachBlockingIO[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BlockingIO: BlockingIO[F]): BlockingIO.type =
      BlockingIO

    @inline implicit final def AttachBIOFork[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BIOFork: BIOFork[F]): BIOFork.type = BIOFork
  }
  type BIOFunctor3[F[-_, _, +_]] = instances.BIOFunctor3[F]

  trait BIOBifunctor[F[+_, +_]] extends BIOFunctor[F] with instances.BIOBifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOBifunctor3[F[-_, +_, +_]] = instances.BIOBifunctor3[F]

  trait BIOApplicative[F[+_, +_]] extends BIOBifunctor[F] with instances.BIOApplicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOApplicative3[F[-_, +_, +_]] = instances.BIOApplicative3[F]

  trait BIOGuarantee[F[+_, +_]] extends BIOApplicative[F] with instances.BIOGuarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOGuarantee3[F[-_, +_, +_]] = instances.BIOGuarantee3[F]

  trait BIOError[F[+_, +_]] extends BIOGuarantee[F] with instances.BIOError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOError3[F[-_, +_, +_]] = instances.BIOError3[F]

  trait BIOMonad[F[+_, +_]] extends BIOApplicative[F] with instances.BIOMonad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOMonad3[F[-_, +_, +_]] = instances.BIOMonad3[F]

  trait BIOMonadError[F[+_, +_]] extends BIOMonad[F] with BIOError[F] with instances.BIOMonadError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOMonadError3[F[-_, +_, +_]] = instances.BIOMonadError3[F]

  trait BIOBracket[F[+_, +_]] extends BIOMonadError[F] with instances.BIOBracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOBracket3[F[-_, +_, +_]] = instances.BIOBracket3[F]

  trait BIOPanic[F[+_, +_]] extends BIOBracket[F] with instances.BIOPanic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOPanic3[F[-_, +_, +_]] = instances.BIOPanic3[F]

  trait BIO[F[+_, +_]] extends BIOPanic[F] with instances.BIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIO3[F[-_, +_, +_]] = instances.BIO3[F]

  trait BIOAsync[F[+_, +_]] extends BIO[F] with instances.BIOAsync3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOAsync3[F[-_, +_, +_]] = instances.BIOAsync3[F]

  trait BIOTemporal[F[+_, +_]] extends BIOAsync[F] with instances.BIOTemporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOTemporal3[F[-_, +_, +_]] = instances.BIOTemporal3[F]

  trait BIOFork[F[+_, +_]] extends instances.BIOFork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  object BIOFork {
    // FIXME: bad encoding for lifting to 2-parameters...
    implicit def BIOForkZioIO[R]: BIOFork[ZIO[R, +?, +?]] = BIOForkZio.asInstanceOf[BIOFork[ZIO[R, +?, +?]]]
  }
  type BIOFork3[F[-_, +_, +_]] = instances.BIOFork3[F]

  trait BIOLatch[F[+_, +_]] extends BIOPromise[F, Nothing, Unit]
  type BIOFiber[F[+_, +_], E, A] = BIOFiber3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]], Any, E, A]

  trait BlockingIO[F[_, _]] extends BlockingIO3[Lambda[(R, E, A) => F[E, A]]]
  object BlockingIO {
    def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly
  }

  type BIOPrimitives3[F[-_, +_, +_]] = instances.BIOPrimitives[F[Any, +?, +?]]

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
