package izumi.functional

import izumi.functional.bio.data.Isomorphism2
import izumi.functional.bio.syntax.{Syntax2, Syntax3}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}

/**
  *  Current hierarchy (use http://www.nomnoml.com/ to render, rendered: https://izumi.7mind.io/bio/media/bio-relationship-hierarchy.svg)
  *
  *  {{{
  *  [Functor3]<--[Bifunctor3]
  *  [Bifunctor3]<--[ApplicativeError3]
  *  [Functor3]<--[Applicative3]
  *  [Applicative3]<--[Guarantee3]
  *  [Applicative3]<--[Monad3]
  *  [Guarantee3]<--[ApplicativeError3]
  *  [ApplicativeError3]<--[Error3]
  *  [Monad3]<--[Error3]
  *  [Error3]<--[Bracket3]
  *  [Bracket3]<--[Panic3]
  *  [Panic3]<--[IO3]
  *  [IO3]<--[Async3]
  *
  *  [Monad3]<--[Parallel3]
  *  [Parallel3]<--[Concurrent3]
  *  [Concurrent3]<--[Async3]
  *
  *  [Error3]<--[Temporal3]
  *
  *  [Functor3]<--[Profunctor3]
  *  [Profunctor3]<--[Arrow3]
  *  [Arrow3]<--[ArrowChoice3]
  *  [ArrowChoice3]<--[Local3]
  *
  *  [Applicative3]<--[Ask3]
  *  [Monad3]<--[MonadAsk3]
  *  [Ask3]<--[MonadAsk3]
  *  [MonadAsk3]<--[Local3]
  *  }}}
  *
  *  Auxiliary algebras:
  *
  *  {{{
  *  [cats.effect.*]<:--[CatsConversions]
  *
  *  [Fork3]<:--[Fiber3]
  *
  *  [BlockingIO3]<:--[BlockingIO2]
  *
  *  [Primitives2]<:--[Primitives3]
  *  [Primitives3]<:--[Ref3]
  *
  *  [Primitives3]<:--[Semaphore3]
  *  [Primitives3]<:--[Promise3]
  *
  *  [Entropy]<:--[Entropy2]
  *
  *  [Entropy2]<:--[Entropy3]
  *  }}}
  *
  *  inheritance hierarchy:
  *
  *  {{{
  *  [Functor3]<--[Applicative3]
  *  [Applicative3]<--[Guarantee3]
  *  [Applicative3]<--[Monad3]
  *  [Guarantee3]<--[ApplicativeError3]
  *  [Bifunctor3]<--[ApplicativeError3]
  *  [ApplicativeError3]<--[Error3]
  *  [Monad3]<--[Error3]
  *  [Error3]<--[Bracket3]
  *  [Bracket3]<--[Panic3]
  *  [Panic3]<--[IO3]
  *
  *  [Parallel3]<--[Concurrent3]
  *  [Concurrent3]<--[Async3]
  *  [IO3]<--[Async3]
  *
  *  [Temporal3]
  *
  *  [Profunctor3]<--[Arrow3]
  *  [Arrow3]<--[ArrowChoice3]
  *  [ArrowChoice3]<--[Local3]
  *  [Ask3]<--[MonadAsk3]
  *  [MonadAsk3]<--[Local3]
  *  }}}
  *
  *  current hierarchy roots:
  *
  *  bifunctor:
  *  - Functor3
  *  - Bifunctor3
  *  - Parallel3
  *  - Temporal3
  *
  *  trifunctor:
  *  - Profunctor3
  *  - Ask3
  *
  *  standalone:
  *  - Fork3
  *  - BlockingIO3
  *  - Primitives3
  */
/*
  New BIO typeclass checklist:

  [ ] - add syntax in BIOSyntax3 & BIOSyntax at the same name as type
  [ ] - add syntax for new root's InnerF at the same name in BIOSyntax3 & BIOSyntax
  [ ] - add new attachments in BIORootInstanceLowPriorityN
  [ ] - add conversion BIOConvertToBIONewRoot in BIORootInstanceLowPriorityN
        (conversions implicit priority: from most specific InnerF to least specific)
 */
package object bio extends Syntax3 with Syntax2 {

  /**
    * A convenient dependent summoner for BIO hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   import izumi.functional.bio.{F, Temporal2}
    *
    *   def y[F[+_, +_]: Temporal2] = {
    *     F.timeout(5.seconds)(F.unit)
    *   }
    * }}}
    */
  @inline override final def F[FR[-_, +_, +_]](implicit FR: Functor3[FR]): FR.type = FR

  type Functor2[F[+_, +_]] = Functor3[[R, E, A] =>> F[E, A]]
  type Bifunctor2[F[+_, +_]] = Bifunctor3[[R, E, A] =>> F[E, A]]
  type Applicative2[F[+_, +_]] = Applicative3[[R, E, A] =>> F[E, A]]
  type Guarantee2[F[+_, +_]] = Guarantee3[[R, E, A] =>> F[E, A]]
  type ApplicativeError2[F[+_, +_]] = ApplicativeError3[[R, E, A] =>> F[E, A]]
  type Monad2[F[+_, +_]] = Monad3[[R, E, A] =>> F[E, A]]
  type Error2[F[+_, +_]] = Error3[[R, E, A] =>> F[E, A]]
  type Bracket2[F[+_, +_]] = Bracket3[[R, E, A] =>> F[E, A]]
  type Panic2[F[+_, +_]] = Panic3[[R, E, A] =>> F[E, A]]
  type IO2[F[+_, +_]] = IO3[[R, E, A] =>> F[E, A]]
  type Parallel2[F[+_, +_]] = Parallel3[[R, E, A] =>> F[E, A]]
  type Concurrent2[F[+_, +_]] = Concurrent3[[R, E, A] =>> F[E, A]]
  type Async2[F[+_, +_]] = Async3[[R, E, A] =>> F[E, A]]
  type Temporal2[F[+_, +_]] = Temporal3[[R, E, A] =>> F[E, A]]

  type Fork2[F[+_, +_]] = Fork3[[R, E, A] =>> F[E, A]]

  type Primitives3[F[-_, +_, +_]] = Primitives2[F[Any, + _, + _]]
  object Primitives3 {
    @inline def apply[F[-_, +_, +_]: Primitives3]: Primitives3[F] = implicitly
  }

  type PrimitivesM3[F[-_, +_, +_]] = PrimitivesM2[F[Any, + _, + _]]
  object PrimitivesM3 {
    @inline def apply[F[-_, +_, +_]: PrimitivesM3]: PrimitivesM3[F] = implicitly
  }

  type BlockingIO2[F[+_, +_]] = BlockingIO3[[R, E, A] =>> F[E, A]]
  object BlockingIO2 {
    @inline def apply[F[+_, +_]: BlockingIO2]: BlockingIO2[F] = implicitly
  }

  type TransZio[F[_, _]] = Isomorphism2[F, zio.IO]
  object TransZio {
    @inline def apply[F[_, _]: TransZio]: TransZio[F] = implicitly
  }

  type Fiber3[+F[-_, +_, +_], +E, +A] = Fiber2[F[Any, + _, + _], E, A]
  lazy val Fiber3: Fiber2.type = Fiber2

  type RefM3[F[_, +_, +_], A] = RefM2[F[Any, + _, + _], A]
  lazy val RefM3: RefM2.type = RefM2

  type Mutex3[F[_, +_, +_], A] = Mutex2[F[Any, + _, + _]]
  lazy val Mutex3: Mutex2.type = Mutex2

  type Ref2[+F[_, _], A] = Ref1[F[Nothing, _], A]
  lazy val Ref2: Ref1.type = Ref1
  type Ref3[+F[_, _, _], A] = Ref1[F[Any, Nothing, _], A]
  lazy val Ref3: Ref1.type = Ref1

  type Promise3[+F[-_, +_, +_], E, A] = Promise2[F[Any, + _, + _], E, A]
  lazy val Promise3: Promise2.type = Promise2

  type Latch2[+F[+_, +_]] = Promise2[F, Nothing, Unit]
  lazy val Latch2: Promise2.type = Promise2
  type Latch3[+F[-_, +_, +_]] = Promise3[F, Nothing, Unit]
  lazy val Latch3: Promise2.type = Promise2

  type Semaphore2[+F[_, _]] = Semaphore1[F[Nothing, _]]
  lazy val Semaphore2: Semaphore1.type = Semaphore1
  type Semaphore3[+F[_, _, _]] = Semaphore1[F[Any, Nothing, _]]
  lazy val Semaphore3: Semaphore1.type = Semaphore1

  type UnsafeRun3[F[_, _, _]] = UnsafeRun2[F[Any, _, _]]
  object UnsafeRun3 {
    @inline def apply[F[_, _, _]: UnsafeRun3]: UnsafeRun3[F] = implicitly
  }

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, _]]
  object SyncSafe2 {
    @inline def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe[F[Any, Nothing, _]]
  object SyncSafe3 {
    @inline def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, _]]
  object Clock2 {
    @inline def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock[F[Any, Nothing, _]]
  object Clock3 {
    @inline def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, _]]
  object Entropy2 {
    @inline def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy[F[Any, Nothing, _]]
  object Entropy3 {
    @inline def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }

  @inline private[bio] final def cast3To2[C[_[-_, +_, +_]], FR[-_, +_, +_], R](
    instance: C[FR]
  ): C[[R0, E, A] =>> FR[R, E, A]] = {
    instance.asInstanceOf[C[[R0, E, A] =>> FR[R, E, A]]]
  }

  import izumi.fundamentals.platform.language.{SourceFilePositionMaterializer, unused}

  import java.util.concurrent.CompletionStage
  import scala.collection.immutable.Queue
  import scala.concurrent.{ExecutionContext, Future}
  import scala.util.Try

  trait Functor3[F[-_, +_, +_]] extends RootBifunctor[F] {
    def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

    def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
    def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())

    /** Extracts the optional value, or returns the given `valueOnNone` value */
    def fromOptionOr[R, E, A](valueOnNone: => A, r: F[R, E, Option[A]]): F[R, E, A] = map(r)(_.getOrElse(valueOnNone))

    @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @unused ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
  }

  import cats.data.Kleisli
  import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
  import izumi.functional.bio.PredefinedHelper.{NotPredefined, Predefined}
  import izumi.functional.bio.SpecificityHelper._
  import izumi.functional.bio.impl.{AsyncMonix, AsyncZio, BioEither, BioIdentity3}
  import izumi.functional.bio.retry.Scheduler3
  import izumi.fundamentals.orphans.`monix.bio.IO`
  import izumi.fundamentals.platform.functional.{Identity2, Identity3}
  import zio.ZIO

  import scala.language.implicitConversions

  trait Root extends DivergenceHelper with PredefinedHelper

  trait RootBifunctor[F[-_, +_, +_]] extends Root

  trait RootTrifunctor[F[-_, +_, +_]] extends Root

  object Root extends RootInstancesLowPriority1 {
    @inline implicit final def ConvertFromConcurrent[FR[-_, +_, +_]](implicit Concurrent: NotPredefined.Of[Concurrent3[FR]]): Panic3[FR] with S1 =
      S1(Concurrent.InnerF)

    @inline implicit final def AttachLocal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Local: Local3[FR]): Local.type = Local
    @inline implicit final def AttachPrimitives3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Primitives: Primitives3[FR]): Primitives.type =
      Primitives
    @inline implicit final def AttachScheduler3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Scheduler: Scheduler3[FR]): Scheduler.type = Scheduler
    @inline implicit final def AttachPrimitivesM3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit PrimitivesM: PrimitivesM3[FR]): PrimitivesM.type =
      PrimitivesM
    @inline implicit final def AttachFork3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Fork: Fork3[FR]): Fork.type = Fork
    @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO

    implicit final class KleisliSyntaxAttached[FR[-_, +_, +_]](private val FR0: Functor3[FR]) extends AnyVal {
      @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, _], R, A])(implicit FR: MonadAsk3[FR]): FR[R, E, A] = FR.fromKleisli(k)
      @inline def toKleisli[R, E, A](fr: FR[R, E, A])(implicit FR: Local3[FR]): Kleisli[FR[Any, E, _], R, A] = {
        val _ = FR0
        FR.toKleisli(fr)
      }
    }
  }

  sealed trait RootInstancesLowPriority1 extends RootInstancesLowPriority2 {
    @inline implicit final def ConvertFromTemporal[FR[-_, +_, +_]](implicit Temporal: NotPredefined.Of[Temporal3[FR]]): Error3[FR] with S2 =
      S2(Temporal.InnerF)

    @inline implicit final def AttachArrowChoice[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit ArrowChoice: ArrowChoice3[FR]): ArrowChoice.type =
      ArrowChoice
    @inline implicit final def AttachMonadAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit MonadAsk: MonadAsk3[FR]): MonadAsk.type = MonadAsk
    @inline implicit final def AttachConcurrent[FR[-_, +_, +_], R](@unused self: Concurrent3[FR])(implicit Concurrent: Concurrent3[FR]): Concurrent.type =
      Concurrent
  }

  sealed trait RootInstancesLowPriority2 extends RootInstancesLowPriority3 {
    @inline implicit final def ConvertFromParallel[FR[-_, +_, +_]](implicit Parallel: NotPredefined.Of[Parallel3[FR]]): Monad3[FR] with S3 =
      S3(Parallel.InnerF)

    @inline implicit final def AttachArrow[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Arrow: Arrow3[FR]): Arrow.type = Arrow
    @inline implicit final def AttachBifunctor[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Bifunctor: Bifunctor3[FR]): Bifunctor.type =
      Bifunctor
    @inline implicit final def AttachConcurrent[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Concurrent: Concurrent3[FR]): Concurrent.type =
      Concurrent
  }

  sealed trait RootInstancesLowPriority3 extends RootInstancesLowPriority4 {
    @inline implicit final def ConvertFromMonadAsk[FR[-_, +_, +_]](implicit MonadAsk: NotPredefined.Of[MonadAsk3[FR]]): Monad3[FR] with S4 =
      S4(MonadAsk.InnerF)

    @inline implicit final def AttachAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Ask: Ask3[FR]): Ask.type = Ask
    @inline implicit final def AttachProfunctor[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Profunctor: Profunctor3[FR]): Profunctor.type =
      Profunctor
    @inline implicit final def AttachParallel[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Parallel: Parallel3[FR]): Parallel.type = Parallel
  }

  sealed trait RootInstancesLowPriority4 extends RootInstancesLowPriority5 {
    @inline implicit final def ConvertFromAsk[FR[-_, +_, +_]](implicit Ask: NotPredefined.Of[Ask3[FR]]): Applicative3[FR] with S5 = S5(Ask.InnerF)

    @inline implicit final def AttachTemporal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Temporal: Temporal3[FR]): Temporal.type = Temporal
  }

  sealed trait RootInstancesLowPriority5 extends RootInstancesLowPriority6 {
    @inline implicit final def ConvertFromProfunctor[FR[-_, +_, +_]](implicit Profunctor: NotPredefined.Of[Profunctor3[FR]]): Functor3[FR] with S6 =
      S6(Profunctor.InnerF)
  }

  sealed trait RootInstancesLowPriority6 extends RootInstancesLowPriority7 {
    @inline implicit final def ConvertFromBifunctor[FR[-_, +_, +_]](implicit Bifunctor: NotPredefined.Of[Bifunctor3[FR]]): Functor3[FR] with S7 =
      S7(Bifunctor.InnerF)
  }

  sealed trait RootInstancesLowPriority7 extends RootInstancesLowPriority8 {
    @inline implicit final def Local3ZIO: Predefined.Of[Local3[ZIO]] = Predefined(AsyncZio)
    @inline implicit final def BIOZIO: Predefined.Of[Async3[ZIO]] = Predefined(AsyncZio)
  }

  sealed trait RootInstancesLowPriority8 extends RootInstancesLowPriority9 {
    /**
      * This instance uses 'no more orphans' trick to provide an Optional instance
      * only IFF you have monix-bio as a dependency without REQUIRING a monix-bio dependency.
      *
      * Optional instance via https://blog.7mind.io/no-more-orphans.html
      */
    // for some reason ZIO instances do not require no-more-orphans machinery and do not create errors when zio is not on classpath...
    // seems like it's because of the type lambda in `Async2` definition
    @inline implicit final def BIOMonix[MonixBIO[+_, +_]](implicit @unused M: `monix.bio.IO`[MonixBIO]): Predefined.Of[Async2[MonixBIO]] =
      AsyncMonix.asInstanceOf[Predefined.Of[Async2[MonixBIO]]]
  }

  sealed trait RootInstancesLowPriority9 extends RootInstancesLowPriority10 {
    @inline implicit final def BIOEither: Predefined.Of[Error2[Either]] = Predefined(BioEither)
  }

  sealed trait RootInstancesLowPriority10 extends RootInstancesLowPriority11 {
    @inline implicit final def BIOIdentity2: Predefined.Of[Monad2[Identity2]] = BioIdentity3.asInstanceOf[Predefined.Of[Monad2[Identity2]]]
    @inline implicit final def BIOIdentity3: Predefined.Of[Monad3[Identity3]] = Predefined(BioIdentity3)
  }

  sealed trait RootInstancesLowPriority11 {
//    @inline implicit final def Convert3To2[C[f[-_, +_, +_]] <: DivergenceHelper with RootBifunctor[f], FR[-_, +_, +_], R0](
//      implicit BifunctorPlus: C[FR] { type Divergence = Nondivergent }
//    ): C[[R, E, A] =>> FR[R0, E, A]] with DivergenceHelper { type Divergence = Divergent } =
//      Divergent(cast3To2[C, FR, R0](BifunctorPlus))

    @inline implicit final def Convert3To2[C[f[-_, +_, +_]] <: DivergenceHelper with RootBifunctor[f] with Root, FR[-_, +_, +_], R0](
      implicit BifunctorPlus: C[FR] { type Divergence = Nondivergent }
    ): C[[R, E, A] =>> FR[R0, E, A]] /*with DivergenceHelper { type Divergence = Divergent } */ =
      Divergent(cast3To2[C, FR, R0](BifunctorPlus))

//    @inline implicit final def Convert3To2[C[fr[-_, +_, +_]] <: DivergenceHelper with RootBifunctor[fr], FR[-_, +_, +_], R](
//      implicit BifunctorPlus: /*=>*/ (C[FR] with RootBifunctor[FR] with DivergenceHelper) // /*&*/ { type Divergence = Nondivergent }
//    ): (C[[R0, E, A] =>> FR[R, E, A]] with RootBifunctor[[R0, E, A] =>> FR[R, E, A]] with DivergenceHelper) { type Divergence = Divergent } =
//      ??? // Divergent(cast3To2[C, FR, R](BifunctorPlus))
  }

  trait Applicative3[F[-_, +_, +_]] extends Functor3[F] {
    def pure[A](a: A): F[Any, Nothing, A]

    /** execute two operations in order, map their results */
    def map2[R, E, A, B, C](firstOp: F[R, E, A], secondOp: => F[R, E, B])(f: (A, B) => C): F[R, E, C]

    /** execute two operations in order, return result of second operation */
    def *>[R, E, A, B](firstOp: F[R, E, A], secondOp: => F[R, E, B]): F[R, E, B] = map2(firstOp, secondOp)((_, b) => b)

    /** execute two operations in order, same as `*>`, but return result of first operation */
    def <*[R, E, A, B](firstOp: F[R, E, A], secondOp: => F[R, E, B]): F[R, E, A] = map2(firstOp, secondOp)((a, _) => a)

    def traverse[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]] = {
      map(
        l.foldLeft(pure(Queue.empty[B]): F[R, E, Queue[B]])((q, a) => map2(q, f(a))(_ :+ _))
      )(_.toList)
    }

    @inline final def forever[R, E, A](r: F[R, E, A]): F[R, E, Nothing] = *>(r, forever(r))
    def traverse_[R, E, A](l: Iterable[A])(f: A => F[R, E, Unit]): F[R, E, Unit] = void(traverse(l)(f))
    def sequence[R, E, A, B](l: Iterable[F[R, E, A]]): F[R, E, List[A]] = traverse(l)(identity)
    def sequence_[R, E](l: Iterable[F[R, E, Unit]]): F[R, E, Unit] = void(traverse(l)(identity))

    def unit: F[Any, Nothing, Unit] = pure(())
    @inline final def traverse[R, E, A, B](o: Option[A])(f: A => F[R, E, B]): F[R, E, Option[B]] = o match {
      case Some(a) => map(f(a))(Some(_))
      case None => pure(None)
    }
    @inline final def when[R, E](cond: Boolean)(ifTrue: F[R, E, Unit]): F[R, E, Unit] = if (cond) ifTrue else unit
    @inline final def unless[R, E](cond: Boolean)(ifFalse: F[R, E, Unit]): F[R, E, Unit] = if (cond) unit else ifFalse
    @inline final def ifThenElse[R, E, A](cond: Boolean)(ifTrue: F[R, E, A], ifFalse: F[R, E, A]): F[R, E, A] = if (cond) ifTrue else ifFalse
  }

  trait ApplicativeError3[F[-_, +_, +_]] extends Guarantee3[F] with Bifunctor3[F] {
    override def InnerF: Functor3[F] = this

    def fail[E](v: => E): F[Any, E, Nothing]

    /** map errors from two operations into a new error if both fail */
    def leftMap2[R, E, A, E2, E3](firstOp: F[R, E, A], secondOp: => F[R, E2, A])(f: (E, E2) => E3): F[R, E3, A]

    /** execute second operation only if the first one fails */
    def orElse[R, E, A, E2](r: F[R, E, A], f: => F[R, E2, A]): F[R, E2, A]

    def fromEither[E, V](effect: => Either[E, V]): F[Any, E, V]
    def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A]
    def fromTry[A](effect: => Try[A]): F[Any, Throwable, A]
  }

  trait Arrow3[FR[-_, +_, +_]] extends Profunctor3[FR] {
    def askWith[R, A](f: R => A): FR[R, Nothing, A]
    def andThen[R, R1, E, A](f: FR[R, E, R1], g: FR[R1, E, A]): FR[R, E, A]
    def asking[R, E, A](f: FR[R, E, A]): FR[R, E, (A, R)]
  }

  trait ArrowChoice3[FR[-_, +_, +_]] extends Arrow3[FR] {
    def choose[R1, R2, E, A, B](f: FR[R1, E, A], g: FR[R2, E, B]): FR[Either[R1, R2], E, Either[A, B]]

    // defaults
    def choice[R1, R2, E, A](f: FR[R1, E, A], g: FR[R2, E, A]): FR[Either[R1, R2], E, A] = dimap(choose(f, g))(identity[Either[R1, R2]])(_.merge)
  }

  trait Ask3[FR[-_, +_, +_]] extends RootTrifunctor[FR] {
    def InnerF: Applicative3[FR]
    def ask[R]: FR[R, Nothing, R]

    // defaults
    def askWith[R, A](f: R => A): FR[R, Nothing, A] = InnerF.map[R, Nothing, R, A](ask)(f)
  }

  trait Async3[F[-_, +_, +_]] extends Concurrent3[F] with IO3[F] {
    override def InnerF: Panic3[F] = this

    final type Canceler = F[Any, Nothing, Unit]

    def async[E, A](register: (Either[E, A] => Unit) => Unit): F[Any, E, A]
    def asyncF[R, E, A](register: (Either[E, A] => Unit) => F[R, E, Unit]): F[R, E, A]
    def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): F[Any, E, A]

    def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Any, Throwable, A]
    def fromFutureJava[A](javaFuture: => CompletionStage[A]): F[Any, Throwable, A]

    // defaults
    override def never: F[Any, Nothing, Nothing] = async(_ => ())

    @inline final def fromFuture[A](mkFuture: => Future[A]): F[Any, Throwable, A] = fromFuture(_ => mkFuture)
  }

  trait Bifunctor3[F[-_, +_, +_]] extends RootBifunctor[F] {
    def InnerF: Functor3[F]

    def bimap[R, E, A, E2, A2](r: F[R, E, A])(f: E => E2, g: A => A2): F[R, E2, A2]
    def leftMap[R, E, A, E2](r: F[R, E, A])(f: E => E2): F[R, E2, A] = bimap(r)(f, identity)

    @inline final def widenError[R, E, A, E1](r: F[R, E, A])(implicit @unused ev: E <:< E1): F[R, E1, A] = r.asInstanceOf[F[R, E1, A]]
    @inline final def widenBoth[R, E, A, E1, A1](r: F[R, E, A])(implicit @unused ev: E <:< E1, @unused ev2: A <:< A1): F[R, E1, A1] =
      r.asInstanceOf[F[R, E1, A1]]
  }

  trait Parallel3[F[-_, +_, +_]] extends RootBifunctor[F] {
    def InnerF: Monad3[F]

    def parTraverse[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]
    def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]
    def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = InnerF.void(parTraverse(l)(f))
    def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = InnerF.void(parTraverseN(maxConcurrent)(l)(f))

    /**
      * Returns an effect that executes both effects,
      * in parallel, combining their results with the specified `f` function. If
      * either side fails, then the other side will be interrupted.
      */
    def zipWithPar[R, E, A, B, C](fa: F[R, E, A], fb: F[R, E, B])(f: (A, B) => C): F[R, E, C]

    // defaults
    /**
      * Returns an effect that executes both effects,
      * in parallel, combining their results into a tuple. If either side fails,
      * then the other side will be interrupted.
      */
    def zipPar[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, (A, B)] = zipWithPar(fa, fb)((a, b) => (a, b))

    /**
      * Returns an effect that executes both effects,
      * in parallel, the left effect result is returned. If either side fails,
      * then the other side will be interrupted.
      */
    def zipParLeft[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, A] = zipWithPar(fa, fb)((a, _) => a)

    /**
      * Returns an effect that executes both effects,
      * in parallel, the right effect result is returned. If either side fails,
      * then the other side will be interrupted.
      */
    def zipParRight[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, B] = zipWithPar(fa, fb)((_, b) => b)
  }

  trait Profunctor3[FR[-_, +_, +_]] extends RootTrifunctor[FR] {
    def InnerF: Functor3[FR]

    def dimap[R1, E, A1, R2, A2](fra: FR[R1, E, A1])(fr: R2 => R1)(fa: A1 => A2): FR[R2, E, A2]

    // defaults
    def contramap[R, E, A, R0](fr: FR[R, E, A])(f: R0 => R): FR[R0, E, A] = dimap(fr)(f)(identity)
  }

  trait Bracket3[F[-_, +_, +_]] extends Error3[F] {
    def bracketCase[R, E, A, B](acquire: F[R, E, A])(release: (A, Exit[E, B]) => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B]

    def bracket[R, E, A, B](acquire: F[R, E, A])(release: A => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B] = {
      bracketCase(acquire)((a, _: Exit[E, B]) => release(a))(use)
    }

    def guaranteeCase[R, E, A](f: F[R, E, A], cleanup: Exit[E, A] => F[R, Nothing, Unit]): F[R, E, A] = {
      bracketCase(unit: F[R, E, Unit])((_, e: Exit[E, A]) => cleanup(e))(_ => f)
    }

    final def bracketOnFailure[R, E, A, B](acquire: F[R, E, A])(cleanupOnFailure: (A, Exit.Failure[E]) => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B] = {
      bracketCase[R, E, A, B](acquire) {
        case (a, e: Exit.Failure[E]) => cleanupOnFailure(a, e)
        case _ => unit
      }(use)
    }

    final def guaranteeOnFailure[R, E, A](f: F[R, E, A], cleanupOnFailure: Exit.Failure[E] => F[R, Nothing, Unit]): F[R, E, A] = {
      guaranteeCase[R, E, A](f, { case e: Exit.Failure[E] => cleanupOnFailure(e); case _ => unit })
    }

    // defaults
    override def guarantee[R, E, A](f: F[R, E, A], cleanup: F[R, Nothing, Unit]): F[R, E, A] = {
      bracket(unit: F[R, E, Unit])(_ => cleanup)(_ => f)
    }
  }

  trait Monad3[F[-_, +_, +_]] extends Applicative3[F] {
    def flatMap[R, E, A, B](r: F[R, E, A])(f: A => F[R, E, B]): F[R, E, B]
    def flatten[R, E, A](r: F[R, E, F[R, E, A]]): F[R, E, A] = flatMap(r)(identity)

    def tailRecM[R, E, A, B](a: A)(f: A => F[R, E, Either[A, B]]): F[R, E, B] =
      flatMap(f(a)) {
        case Left(next) => tailRecM(next)(f)
        case Right(res) => pure(res)
      }

    def tap[R, E, A](r: F[R, E, A], f: A => F[R, E, Unit]): F[R, E, A] = flatMap(r)(a => as(f(a))(a))

    @inline final def when[R, E, E1](cond: F[R, E, Boolean])(ifTrue: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
      ifThenElse(cond)(ifTrue, unit)
    }
    @inline final def unless[R, E, E1](cond: F[R, E, Boolean])(ifFalse: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
      ifThenElse(cond)(unit, ifFalse)
    }
    @inline final def ifThenElse[R, E, E1, A](
      cond: F[R, E, Boolean]
    )(ifTrue: F[R, E1, A],
      ifFalse: F[R, E1, A],
    )(implicit @unused ev: E <:< E1
    ): F[R, E1, A] = {
      flatMap(cond.asInstanceOf[F[R, E1, Boolean]])(if (_) ifTrue else ifFalse)
    }

    /** Extracts the optional value, or executes the `fallbackOnNone` effect */
    def fromOptionF[R, E, A](fallbackOnNone: => F[R, E, A], r: F[R, E, Option[A]]): F[R, E, A] = {
      flatMap(r) {
        case Some(value) => pure(value)
        case None => fallbackOnNone
      }
    }

    /**
      * Execute an action repeatedly until its result fails to satisfy the given predicate
      * and return that result, discarding all others.
      */
    @inline def iterateWhile[R, E, A](r: F[R, E, A])(p: A => Boolean): F[R, E, A] = {
      flatMap(r)(i => iterateWhileF(i)(_ => r)(p))
    }

    /**
      * Execute an action repeatedly until its result satisfies the given predicate
      * and return that result, discarding all others.
      */
    @inline def iterateUntil[R, E, A](r: F[R, E, A])(p: A => Boolean): F[R, E, A] = {
      flatMap(r)(i => iterateUntilF(i)(_ => r)(p))
    }

    /**
      * Apply an effectful function iteratively until its result fails
      * to satisfy the given predicate and return that result.
      */
    @inline def iterateWhileF[R, E, A](init: A)(f: A => F[R, E, A])(p: A => Boolean): F[R, E, A] = {
      tailRecM(init) {
        a =>
          if (p(a)) {
            map(f(a))(Left(_))
          } else {
            pure(Right(a))
          }
      }
    }

    /**
      * Apply an effectful function iteratively until its result satisfies
      * the given predicate and return that result.
      */
    @inline def iterateUntilF[R, E, A](init: A)(f: A => F[R, E, A])(p: A => Boolean): F[R, E, A] = {
      iterateWhileF(init)(f)(!p(_))
    }

    // defaults
    override def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B] = flatMap(r)(a => pure(f(a)))
    override def *>[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, B] = flatMap(f)(_ => next)
    override def <*[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, A] = flatMap(f)(a => map(next)(_ => a))
    override def map2[R, E, A, B, C](r1: F[R, E, A], r2: => F[R, E, B])(f: (A, B) => C): F[R, E, C] = flatMap(r1)(a => map(r2)(b => f(a, b)))
  }

  trait MonadAsk3[FR[-_, +_, +_]] extends Ask3[FR] with MonadAskSyntax {
    override def InnerF: Monad3[FR]

    def access[R, E, A](f: R => FR[R, E, A]): FR[R, E, A]
  }

  private[bio] sealed trait MonadAskSyntax
  object MonadAskSyntax {
    implicit final class KleisliSyntaxMonadAsk[FR[-_, +_, +_]](private val FR: MonadAsk3[FR]) extends AnyVal {
      @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, _], R, A]): FR[R, E, A] = FR.access(k.run)
    }
  }

  trait Error3[F[-_, +_, +_]] extends ApplicativeError3[F] with Monad3[F] {

    def catchAll[R, E, A, E2](r: F[R, E, A])(f: E => F[R, E2, A]): F[R, E2, A]

    def catchSome[R, E, A, E1 >: E](r: F[R, E, A])(f: PartialFunction[E, F[R, E1, A]]): F[R, E1, A] = {
      catchAll(r)(e => f.applyOrElse(e, (_: E) => fail(e)))
    }

    def redeem[R, E, A, E2, B](r: F[R, E, A])(err: E => F[R, E2, B], succ: A => F[R, E2, B]): F[R, E2, B] = {
      flatMap(attempt(r))(_.fold(err, succ))
    }
    def redeemPure[R, E, A, B](r: F[R, E, A])(err: E => B, succ: A => B): F[R, Nothing, B] = catchAll(map(r)(succ))(e => pure(err(e)))
    def attempt[R, E, A](r: F[R, E, A]): F[R, Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))

    def tapError[R, E, A, E1 >: E](r: F[R, E, A])(f: E => F[R, E1, Unit]): F[R, E1, A] = {
      catchAll(r)(e => *>(f(e), fail(e)))
    }

    def flip[R, E, A](r: F[R, E, A]): F[R, A, E] = {
      redeem(r)(pure, fail(_))
    }
    def leftFlatMap[R, E, A, E2](r: F[R, E, A])(f: E => F[R, Nothing, E2]): F[R, E2, A] = {
      redeem(r)(e => flatMap(f(e))(fail(_)), pure)
    }
    def tapBoth[R, E, A, E1 >: E](r: F[R, E, A])(err: E => F[R, E1, Unit], succ: A => F[R, E1, Unit]): F[R, E1, A] = {
      tap(tapError[R, E, A, E1](r)(err), succ)
    }

    /** Extracts the optional value or fails with the `errorOnNone` error */
    def fromOption[R, E, A](errorOnNone: => E, r: F[R, E, Option[A]]): F[R, E, A] = {
      flatMap(r) {
        case Some(value) => pure(value)
        case None => fail(errorOnNone)
      }
    }

    /** Retries this effect while its error satisfies the specified predicate. */
    def retryWhile[R, E, A](r: F[R, E, A])(f: E => Boolean): F[R, E, A] = {
      retryWhileF(r)(e => pure(f(e)))
    }
    /** Retries this effect while its error satisfies the specified effectful predicate. */
    def retryWhileF[R, R1 <: R, E, A](r: F[R, E, A])(f: E => F[R1, Nothing, Boolean]): F[R1, E, A] = {
      catchAll(r: F[R1, E, A])(e => flatMap(f(e))(if (_) retryWhileF(r)(f) else fail(e)))
    }

    /** Retries this effect until its error satisfies the specified predicate. */
    def retryUntil[R, E, A](r: F[R, E, A])(f: E => Boolean): F[R, E, A] = {
      retryUntilF(r)(e => pure(f(e)))
    }
    /** Retries this effect until its error satisfies the specified effectful predicate. */
    def retryUntilF[R, R1 <: R, E, A](r: F[R, E, A])(f: E => F[R1, Nothing, Boolean]): F[R1, E, A] = {
      catchAll(r: F[R1, E, A])(e => flatMap(f(e))(if (_) fail(e) else retryUntilF(r)(f)))
    }

    /** for-comprehensions sugar:
      *
      * {{{
      *   for {
      *     (1, 2) <- F.pure((2, 1))
      *   } yield ()
      * }}}
      *
      * Use [[widenError]] to for pattern matching with non-Throwable errors:
      *
      * {{{
      *   val f = for {
      *     (1, 2) <- F.pure((2, 1)).widenError[Option[Unit]]
      *   } yield ()
      *   // f: F[Option[Unit], Unit] = F.fail(Some(())
      * }}}
      */
    @inline final def withFilter[R, E, A](r: F[R, E, A])(predicate: A => Boolean)(implicit filter: WithFilter[E], pos: SourceFilePositionMaterializer): F[R, E, A] = {
      flatMap(r)(a => if (predicate(a)) pure(a) else fail(filter.error(a, pos.get)))
    }

    // defaults
    override def bimap[R, E, A, E2, B](r: F[R, E, A])(f: E => E2, g: A => B): F[R, E2, B] = catchAll(map(r)(g))(e => fail(f(e)))
    override def leftMap2[R, E, A, E2, E3](firstOp: F[R, E, A], secondOp: => F[R, E2, A])(f: (E, E2) => E3): F[R, E3, A] =
      catchAll(firstOp)(e => leftMap(secondOp)(f(e, _)))
    override def orElse[R, E, A, E2](r: F[R, E, A], f: => F[R, E2, A]): F[R, E2, A] = catchAll(r)(_ => f)
  }

  trait IO3[F[-_, +_, +_]] extends Panic3[F] {
    final type Or[+E, +A] = F[Any, E, A]
    final type Just[+A] = F[Any, Nothing, A]

    /**
      * Capture a side-effectful block of code that can throw exceptions
      *
      * @note `sync` means `synchronous`, that is, a blocking CPU effect, as opposed to a non-blocking
      *        [[izumi.functional.bio.Async3#async asynchronous]] effect or a
      *        long blocking I/O effect ([[izumi.functional.bio.BlockingIO3#syncBlocking]])
      */
    def syncThrowable[A](effect: => A): F[Any, Throwable, A]

    /**
      * Capture an _exception-safe_ side-effect such as memory mutation or randomness
      *
      * @example
      * {{{
      * import izumi.functional.bio.F
      *
      * val referentiallyTransparentArrayAllocation: F[Nothing, Array[Byte]] = {
      *   F.sync(new Array(256))
      * }
      * }}}
      *
      * @note If you're not completely sure that a captured block can't throw, use [[syncThrowable]]
      * @note `sync` means `synchronous`, that is, a blocking CPU effect, as opposed to a non-blocking
      *       [[izumi.functional.bio.Async3#async asynchronous]] effect or a
      *       long blocking I/O effect ([[izumi.functional.bio.BlockingIO3#syncBlocking]])
      */
    def sync[A](effect: => A): F[Any, Nothing, A]

    def suspend[R, A](effect: => F[R, Throwable, A]): F[R, Throwable, A] = flatten(syncThrowable(effect))

    @inline final def apply[A](effect: => A): F[Any, Throwable, A] = syncThrowable(effect)

    // defaults
    override def fromEither[E, A](effect: => Either[E, A]): F[Any, E, A] = flatMap(sync(effect)) {
      case Left(e) => fail(e): F[Any, E, A]
      case Right(v) => pure(v): F[Any, E, A]
    }
    override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A] = {
      flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
    }
    override def fromTry[A](effect: => Try[A]): F[Any, Throwable, A] = {
      syncThrowable(effect.get)
    }
  }

  trait Concurrent3[F[-_, +_, +_]] extends Parallel3[F] {
    override def InnerF: Panic3[F]

    /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
    def race[R, E, A](r1: F[R, E, A], r2: F[R, E, A]): F[R, E, A]

    /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
    def racePair[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, Either[(A, Fiber3[F, E, B]), (Fiber3[F, E, A], B)]]

    def uninterruptible[R, E, A](r: F[R, E, A]): F[R, E, A]

    def yieldNow: F[Any, Nothing, Unit]

    def never: F[Any, Nothing, Nothing]
  }

  trait Guarantee3[F[-_, +_, +_]] extends Applicative3[F] {
    def guarantee[R, E, A](f: F[R, E, A], cleanup: F[R, Nothing, Unit]): F[R, E, A]
  }

  trait Local3[FR[-_, +_, +_]] extends MonadAsk3[FR] with ArrowChoice3[FR] with LocalInstances {
    override def InnerF: Monad3[FR]

    def provide[R, E, A](fr: FR[R, E, A])(env: => R): FR[Any, E, A] = contramap(fr)((_: Any) => env)

    // defaults
    override def dimap[R1, E, A1, R2, A2](fab: FR[R1, E, A1])(f: R2 => R1)(g: A1 => A2): FR[R2, E, A2] = InnerF.map(contramap(fab)(f))(g)
    override def choose[RL, RR, E, AL, AR](f: FR[RL, E, AL], g: FR[RR, E, AR]): FR[Either[RL, RR], E, Either[AL, AR]] = access {
      case Left(a) => InnerF.map(provide(f)(a))(Left(_))
      case Right(b) => InnerF.map(provide(g)(b))(Right(_)): FR[Either[RL, RR], E, Either[AL, AR]]
    }
    override def askWith[R, A](f: R => A): FR[R, Nothing, A] = InnerF.map(ask[R])(f)
    override def andThen[R, R1, E, A](f: FR[R, E, R1], g: FR[R1, E, A]): FR[R, E, A] = InnerF.flatMap(f)(provide(g)(_))
    override def asking[R, E, A](f: FR[R, E, A]): FR[R, E, (A, R)] = InnerF.map2(ask[R], f)((r, a) => (a, r))
    override def access[R, E, A](f: R => FR[R, E, A]): FR[R, E, A] = InnerF.flatMap(ask[R])(f)
  }

  private[bio] sealed trait LocalInstances
  object LocalInstances {
    implicit final class ToKleisliSyntaxLocal[FR[-_, +_, +_]](private val FR: Local3[FR]) extends AnyVal {
      @inline final def toKleisli[R, E, A](fr: FR[R, E, A]): Kleisli[FR[Any, E, _], R, A] = Kleisli(FR.provide(fr)(_))
    }
  }

  import izumi.functional.bio.PredefinedHelper.Predefined
  import izumi.functional.bio.impl.{TemporalMonix, TemporalZio}
  import zio.ZIO

  import scala.concurrent.duration.{Duration, FiniteDuration}
  import izumi.fundamentals.orphans.{`cats.effect.Timer`, `monix.bio.IO`}

  trait Temporal3[F[-_, +_, +_]] extends RootBifunctor[F] with TemporalInstances {
    def InnerF: Error3[F]

    def sleep(duration: Duration): F[Any, Nothing, Unit]
    def timeout[R, E, A](duration: Duration)(r: F[R, E, A]): F[R, E, Option[A]]

    def retryOrElse[R, E, A, E2](r: F[R, E, A])(duration: FiniteDuration, orElse: => F[R, E2, A]): F[R, E2, A]

    @inline final def timeoutFail[R, E, A](duration: Duration)(e: E, r: F[R, E, A]): F[R, E, A] = {
      InnerF.flatMap(timeout(duration)(r))(_.fold[F[R, E, A]](InnerF.fail(e))(InnerF.pure))
    }

    @inline final def repeatUntil[R, E, A](action: F[R, E, Option[A]])(tooManyAttemptsError: => E, sleep: FiniteDuration, maxAttempts: Int): F[R, E, A] = {
      def go(n: Int): F[R, E, A] = {
        InnerF.flatMap(action) {
          case Some(value) =>
            InnerF.pure(value)
          case None =>
            if (n <= maxAttempts) {
              InnerF.*>(this.sleep(sleep), go(n + 1))
            } else {
              InnerF.fail(tooManyAttemptsError)
            }
        }
      }

      go(0)
    }
  }

  private[bio] sealed trait TemporalInstances
  object TemporalInstances extends TemporalInstancesLowPriority1 {
    @inline implicit final def Temporal3Zio(implicit clockService: zio.clock.Clock): Predefined.Of[Temporal3[ZIO]] = Predefined(new TemporalZio(clockService))
  }
  sealed trait TemporalInstancesLowPriority1 {
    @inline implicit final def TemporalMonix[MonixBIO[+_, +_]: `monix.bio.IO`, Timer[_[_]]: `cats.effect.Timer`](
      implicit
      timer: Timer[MonixBIO[Nothing, _]]
    ): Predefined.Of[Temporal2[MonixBIO]] = new TemporalMonix(timer.asInstanceOf[cats.effect.Timer[monix.bio.UIO]]).asInstanceOf[Predefined.Of[Temporal2[MonixBIO]]]
  }

  import izumi.functional.bio.PredefinedHelper.Predefined
  import izumi.fundamentals.orphans.`monix.bio.IO`
  import zio.ZIO

  trait Fork3[F[-_, +_, +_]] extends RootBifunctor[F] with ForkInstances {
    def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, Fiber3[F, E, A]]
  }

  private[bio] sealed trait ForkInstances
  object ForkInstances extends LowPriorityForkInstances {
    @inline implicit def ForkZio: Predefined.Of[Fork3[ZIO]] = Predefined(impl.ForkZio)
  }
  sealed trait LowPriorityForkInstances {
    @inline implicit def ForkMonix[MonixBIO[+_, +_]: `monix.bio.IO`]: Predefined.Of[Fork2[MonixBIO]] = impl.ForkMonix.asInstanceOf[Predefined.Of[Fork2[MonixBIO]]]
  }

}
