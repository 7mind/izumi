package izumi.functional.lifecycle

import cats.effect.kernel
import cats.effect.kernel.{GenConcurrent, Resource, Sync}
import izumi.functional.bio.data.{Morphism2, RestoreInterruption2}
import izumi.functional.bio.*
import izumi.fundamentals.platform.language.Quirks.*
import zio.internal.stacktracer.Tracer
import zio.managed.ZManaged.ReleaseMap
import zio.managed.{Reservation, ZManaged}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Scope, ZEnvironment, ZIO, ZLayer}

import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.annotation.unused

/**
  * `Lifecycle` is a class that describes the effectful allocation of a resource and its finalizer.
  * This can be used to represent expensive resources.
  *
  * Resources can be created using [[Lifecycle.make]]:
  *
  * {{{
  *   def open(file: File): Lifecycle[IO, Throwable, BufferedReader] =
  *     Lifecycle.make(
  *       acquire = IO { new BufferedReader(new FileReader(file)) }
  *     )(release = reader => IO { reader.close() })
  * }}}
  *
  * `Lifecycle` is a bifunctor: the `F[+_, +_]` parameter is a bifunctor effect type
  * (such as a [[izumi.functional.bio.IO2]] instance), the `+E` parameter is the typed
  * error channel, and the `+A` parameter is the value carried by the lifecycle.
  *
  * Usage is done via [[Lifecycle.SyntaxUse#use use]]:
  *
  * {{{
  *   open(file1).use {
  *     reader1 =>
  *       open(file2).use {
  *         reader2 =>
  *           readFiles(reader1, reader2)
  *       }
  *   }
  * }}}
  *
  * Lifecycles can be combined into larger Lifecycles via [[Lifecycle#flatMap]] (and the
  * associated for-comprehension syntax). Nested resources are released in reverse order of
  * acquisition. Outer resources are released even if an inner use or release fails.
  *
  *  - Use [[Lifecycle.fromCats]] / [[SyntaxLifecycleCats#toCats]] to convert from / to a [[cats.effect.Resource]]
  *  - Use [[Lifecycle.fromZIO]] / [[SyntaxLifecycleZIO#toZIO]] to convert from / to a scoped [[zio.ZIO]]
  *  - Use [[Lifecycle.fromZManaged]] / [[SyntaxLifecycleZManaged#toZManaged]] to convert from / to a [[zio.managed.ZManaged]]
  *
  * @see [[Lifecycle.SyntaxUse.use]] - main entrypoint
  * @see [[izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSLBase#fromResource ModuleDef.fromResource]]
  * @see [[https://typelevel.org/cats-effect/datatypes/resource.html cats.effect.Resource]]
  * @see [[https://zio.dev/1.0.18/reference/resource/zmanaged/ zio.managed.ZManaged]]
  * @see [[https://zio.dev/guides/migrate/zio-2.x-migration-guide#scopes-1 scoped zio.ZIO]]
  * @see [[https://zio.dev/reference/contextual/zlayer zio.ZLayer]]
  */
trait Lifecycle[+F[+_, +_], +E, +A] {
  type InnerResource

  /**
    * The action in `F` used to acquire the resource.
    *
    * @note the `acquire` action is performed *uninterruptibly* by [[Lifecycle.SyntaxUse#use]] and other interpreters,
    * when `F` is an effect type that supports interruption/cancellation.
    */
  def acquire: F[E, InnerResource]

  /**
    * The action in `F` used to release, close or deallocate the resource
    * after it has been acquired and used through [[Lifecycle.SyntaxUse#use]].
    *
    * The release action returns `F[Nothing, Unit]` — release is not allowed to surface typed
    * errors. Any underlying failure of the release effect appears as a defect / termination
    * (e.g. `Exit.Termination`).
    *
    * @note the `release` action is performed *uninterruptibly* by [[Lifecycle.SyntaxUse#use]] and other interpreters,
    * when `F` is an effect type that supports interruption/cancellation.
    */
  def release(resource: InnerResource): F[Nothing, Unit]

  /**
    * Either an action in `F` or a pure function used to
    * extract the `A` from the `InnerResource`
    *
    * The effect in the `Left` branch will be performed *interruptibly*,
    * it is not afforded the same kind of safety as `acquire` and `release` actions
    * when `F` is an effect type that supports interruption/cancellation.
    *
    * When consuming the output of `extract` you can use `_.fold(identity, F.pure)` to convert the `Either` to `F[E, B]`
    *
    * @see [[Lifecycle.Basic]] `extract` doesn't have to be defined when inheriting from `Lifecycle.Basic`
    *
    * @note the `extract` action is performed *interruptibly* by [[Lifecycle.SyntaxUse#use]] and other interpreters
    */
  def extract[B >: A](resource: InnerResource): Either[F[E, B], B]

  final def map[G[+e, +a] >: F[e, a], B](f: A => B)(implicit FF: Functor2[G]): Lifecycle[G, E, B] =
    LifecycleMethodImpls.mapImpl[G, E, A, B](this)(f)
  final def flatMap[G[+e, +a] >: F[e, a], E1 >: E, B](f: A => Lifecycle[G, E1, B])(implicit FF: IO2[G], FP: Primitives2[G]): Lifecycle[G, E1, B] =
    LifecycleMethodImpls.flatMapImpl[G, E1, A, B](this.widenError[E1])(f)
  final def flatten[G[+e, +a] >: F[e, a], E1 >: E, B](implicit ev: A <:< Lifecycle[G, E1, B], FF: IO2[G], FP: Primitives2[G]): Lifecycle[G, E1, B] =
    this.flatMap[G, E1, B](ev)

  final def catchAll[G[+e, +a] >: F[e, a], E1 >: E, E2, B >: A](recover: E1 => Lifecycle[G, E2, B])(implicit FF: IO2[G], FP: Primitives2[G]): Lifecycle[G, E2, B] =
    LifecycleMethodImpls.redeemImpl[G, E1, E2, A, B](this.widenError[E1])(recover, Lifecycle.pure[G](_))
  final def catchSome[G[+e, +a] >: F[e, a], E1 >: E, B >: A](recover: PartialFunction[E1, Lifecycle[G, E1, B]])(implicit FF: IO2[G], FP: Primitives2[G]): Lifecycle[G, E1, B] =
    catchAll[G, E1, E1, B](e => recover.applyOrElse(e, (_: E1) => Lifecycle.fail[G, E1, B](e)))

  final def redeem[G[+e, +a] >: F[e, a], E1 >: E, E2, B](
    onFailure: E1 => Lifecycle[G, E2, B],
    onSuccess: A => Lifecycle[G, E2, B],
  )(implicit FF: IO2[G], FP: Primitives2[G]
  ): Lifecycle[G, E2, B] =
    LifecycleMethodImpls.redeemImpl[G, E1, E2, A, B](this.widenError[E1])(onFailure, onSuccess)

  final def evalMap[G[+e, +a] >: F[e, a], E1 >: E, B](f: A => G[E1, B])(implicit FF: IO2[G], FP: Primitives2[G]): Lifecycle[G, E1, B] =
    flatMap[G, E1, B](a => Lifecycle.liftF[G, E1, B](f(a)))
  final def evalTap[G[+e, +a] >: F[e, a], E1 >: E](f: A => G[E1, Unit])(implicit FF: IO2[G], FP: Primitives2[G]): Lifecycle[G, E1, A] =
    evalMap[G, E1, A](a => FF.map[E1, Unit, A](f(a))(_ => a))

  /** Wrap acquire action of this resource in another effect, e.g. for logging purposes */
  final def wrapAcquire[G[+e, +a] >: F[e, a], E1 >: E](f: (=> G[E1, InnerResource]) => G[E1, InnerResource]): Lifecycle[G, E1, A] =
    LifecycleMethodImpls.wrapAcquireImpl[G, E1, A, InnerResource](this.widenError[E1].asInstanceOf[Lifecycle[G, E1, A] { type InnerResource = Lifecycle.this.InnerResource }])(f)

  /** Wrap release action of this resource in another effect, e.g. for logging purposes */
  final def wrapRelease[G[+e, +a] >: F[e, a], E1 >: E](
    f: (InnerResource => G[Nothing, Unit], InnerResource) => G[Nothing, Unit]
  ): Lifecycle[G, E1, A] =
    LifecycleMethodImpls.wrapReleaseImpl[G, E1, A, InnerResource](this.widenError[E1].asInstanceOf[Lifecycle[G, E1, A] { type InnerResource = Lifecycle.this.InnerResource }])(f)

  final def beforeAcquire[G[+e, +a] >: F[e, a], E1 >: E](f: => G[E1, Unit])(implicit FF: Applicative2[G]): Lifecycle[G, E1, A] =
    wrapAcquire[G, E1](acquire => FF.map2[E1, Unit, InnerResource, InnerResource](f, acquire)((_, res) => res))

  /** Prepend release action to existing */
  final def beforeRelease[G[+e, +a] >: F[e, a], E1 >: E](f: InnerResource => G[Nothing, Unit])(implicit FF: Applicative2[G]): Lifecycle[G, E1, A] =
    wrapRelease[G, E1]((release, res) => FF.map2[Nothing, Unit, Unit, Unit](f(res), release(res))((_, _) => ()))

  final def void[G[+e, +a] >: F[e, a]](implicit FF: Functor2[G]): Lifecycle[G, E, Unit] = map[G, Unit](_ => ())

  final def mapK[G[+e, +a] >: F[e, a], H[+_, +_]](f: Morphism2[G, H]): Lifecycle[H, E, A] =
    LifecycleMethodImpls.mapKImpl[G, H, E, A](this.asInstanceOf[Lifecycle[G, E, A]], f)

  @inline final def widen[B >: A]: Lifecycle[F, E, B] = this
  @inline final def widen[B](implicit ev: A <:< B): Lifecycle[F, E, B] = this.asInstanceOf[Lifecycle[F, E, B]]
  @inline final def widenError[E1 >: E]: Lifecycle[F, E1, A] = this
}

object Lifecycle extends LifecycleInstances {

  /**
    * A sub-trait of [[Lifecycle]] suitable for less-complex resource definitions via inheritance
    * that do not require overriding [[Lifecycle#InnerResource]].
    */
  trait Basic[F[+_, +_], +E, A] extends Lifecycle[F, E, A] {
    def acquire: F[E, A]
    def release(resource: A): F[Nothing, Unit]

    override final def extract[B >: A](resource: A): Right[Nothing, A] = Right(resource)
    override final type InnerResource = A
  }

  def make[F[+_, +_], E, A](acquire: => F[E, A])(release: A => F[Nothing, Unit]): Lifecycle[F, E, A] = {
    @inline def a: F[E, A] = acquire; @inline def r: A => F[Nothing, Unit] = release
    new Lifecycle.Basic[F, E, A] {
      override def acquire: F[E, A] = a
      override def release(resource: A): F[Nothing, Unit] = r(resource)
    }
  }

  def make_[F[+_, +_], E, A](acquire: => F[E, A])(release: => F[Nothing, Unit]): Lifecycle[F, E, A] = {
    make(acquire)(_ => release)
  }

  def makeSimple[A](acquire: => A)(release: A => Unit): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, A] = {
    Lifecycle.make[Bifunctorized.IdentityBifunctorized, Throwable, A] {
      Bifunctorized.bifunctorizeIdentity(acquire)
    } { a =>
      Bifunctorized
        .bifunctorizeIdentity(release(a))
        .asInstanceOf[Bifunctorized.IdentityBifunctorized[Nothing, Unit]]
    }
  }

  /** For stateful objects that have a separate post-creation init method. */
  def makeSimpleInit[A](create: => A)(init: A => Unit)(release: A => Unit): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, A] = {
    makeSimple {
      val a = create
      init(a)
      a
    }(release)
  }

  def makeUninterruptibleExcept[F[+_, +_], E, A](
    acquire: RestoreInterruption2[F] => F[E, A]
  )(release: A => F[Nothing, Unit]
  )(implicit F: IO2[F], P: Primitives2[F]
  ): Lifecycle[F, E, A] = {
    LifecycleMethodImpls.makeUninterruptibleExceptImpl[F, E, A](acquire)(release)
  }

  def makePair[F[+_, +_], E, A](allocate: F[E, (A, F[Nothing, Unit])]): Lifecycle[F, E, A] = {
    new Lifecycle.FromPair[F, E, A] {
      override def acquire: F[E, (A, F[Nothing, Unit])] = allocate
    }
  }

  /** @param effect is performed interruptibly, unlike in [[make]] */
  def liftF[F[+_, +_], E, A](effect: => F[E, A])(implicit F: Applicative2[F]): Lifecycle[F, E, A] = {
    new Lifecycle.LiftF[F, E, A](effect)
  }

  /** @param effect is performed interruptibly, unlike in [[make]] */
  def suspend[F[+_, +_]: IO2: Primitives2, E, A](effect: => F[E, Lifecycle[F, E, A]]): Lifecycle[F, E, A] = {
    liftF(effect).flatten
  }

  /**
    * Fork the specified action into a new fiber.
    * When this `Lifecycle` is released, the fiber will be interrupted using [[izumi.functional.bio.Fiber2#interrupt]]
    *
    * @return The [[izumi.functional.bio.Fiber2 fiber]] running `f` action
    */
  def fork[F[+_, +_]: Fork2, E, A](f: F[E, A]): Lifecycle[F, Nothing, Fiber2[F, E, A]] = {
    Lifecycle.make[F, Nothing, Fiber2[F, E, A]](f.fork)(_.interrupt)
  }

  /** @see [[fork]] */
  def fork_[F[+_, +_]: Fork2: Functor2, E, A](f: F[E, A]): Lifecycle[F, Nothing, Unit] = {
    Lifecycle.fork(f).void
  }

  /**
    * Fork the specified action into a new fiber.
    * When this `Lifecycle` is released, the fiber will be interrupted using [[cats.effect.Fiber#cancel]]
    *
    * @return The fiber running `f` action
    */
  def forkCats[F[_], E, A](
    f: F[A]
  )(implicit F: GenConcurrent[F, E]
  ): Lifecycle[Bifunctorized[F, +_, +_], Throwable, cats.effect.Fiber[F, E, A]] = {
    new Lifecycle.Basic[Bifunctorized[F, +_, +_], Throwable, cats.effect.Fiber[F, E, A]] {
      override def acquire: Bifunctorized[F, Throwable, cats.effect.Fiber[F, E, A]] =
        Bifunctorized.assert(F.start(f))
      override def release(resource: cats.effect.Fiber[F, E, A]): Bifunctorized[F, Nothing, Unit] =
        Bifunctorized.assert(resource.cancel)
    }
  }

  def traverse[F[+_, +_]: IO2: Primitives2, E, A, B](l: Iterable[A])(f: A => Lifecycle[F, E, B]): Lifecycle[F, E, List[B]] = {
    l.foldLeft[Lifecycle[F, E, List[B]]](pure[F](List.empty[B]).widenError[E]) {
      (acc, a) => acc.flatMap[F, E, List[B]](list => f(a).map[F, List[B]](r => list ++ List(r)))
    }
  }

  def traverse_[F[+_, +_]: IO2: Primitives2, E, A](l: Iterable[A])(f: A => Lifecycle[F, E, Unit]): Lifecycle[F, E, Unit] = {
    l.foldLeft[Lifecycle[F, E, Unit]](unit[F].widenError[E]) {
      (acc, a) => acc.flatMap[F, E, Unit](_ => f(a))
    }
  }

  def fromAutoCloseable[F[+_, +_], E, A <: AutoCloseable](acquire: => F[E, A])(implicit F: IO2[F]): Lifecycle[F, E, A] = {
    make(acquire)(a => F.sync(a.close()))
  }
  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, A] = {
    makeSimple(acquire)(_.close())
  }

  def fromExecutorService[F[+_, +_], E, A <: ExecutorService](acquire: => F[E, A])(implicit F: IO2[F]): Lifecycle[F, E, A] = {
    make(acquire) {
      es =>
        F.sync {
          if (!(es.isShutdown || es.isTerminated)) {
            es.shutdown()
            if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
              es.shutdownNow().discard()
            }
          }
        }
    }
  }

  def fromExecutorService[A <: ExecutorService](acquire: => A): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, A] = {
    makeSimple(acquire) { es =>
      if (!(es.isShutdown || es.isTerminated)) {
        es.shutdown()
        if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
          es.shutdownNow().discard()
        }
      }
    }
  }

  @inline def pure[F[+_, +_]]: SyntaxPure[F] = new SyntaxPure[F]
  implicit final class SyntaxPure[F[+_, +_]](private val dummy: Boolean = false) extends AnyVal {
    @inline def apply[A](a: A)(implicit F: Applicative2[F]): Lifecycle[F, Nothing, A] = {
      Lifecycle.liftF[F, Nothing, A](F.pure(a))
    }
  }

  def unit[F[+_, +_]](implicit F: Applicative2[F]): Lifecycle[F, Nothing, Unit] = {
    Lifecycle.liftF[F, Nothing, Unit](F.unit)
  }

  def fail[F[+_, +_], E, A](error: => E)(implicit F: IO2[F]): Lifecycle[F, E, A] = {
    Lifecycle.liftF[F, E, A](F.suspendSafe(F.fail(error)))
  }

  implicit final class SyntaxUse[F[+_, +_], +E, +A](private val resource: Lifecycle[F, E, A]) extends AnyVal {
    /**
      * The main entrypoint for using a Lifecycle
      *
      * @example
      * {{{
      * open(file1).use {
      *   reader1 =>
      *     open(file2).use {
      *       reader2 =>
      *         readFiles(reader1, reader2)
      *     }
      * }
      * }}}
      */
    def use[E1 >: E, B](use: A => F[E1, B])(implicit FF: IO2[F]): F[E1, B] = {
      FF.bracket[E1, resource.InnerResource, B](acquire = resource.acquire)(release = resource.release(_))(
        use = a =>
          FF.suspendSafe(resource.extract[A](a) match {
            case Left(effect) => FF.flatMap[E1, A, B](effect)(use)
            case Right(value) => use(value)
          })
      )
    }
  }

  implicit final class SyntaxUseEffect[F[+_, +_], E, A](private val resource: Lifecycle[F, E, F[E, A]]) extends AnyVal {
    def useEffect(implicit F: IO2[F]): F[E, A] =
      resource.use[E, A](identity)
  }

  implicit final class SyntaxUnsafeGet[F[+_, +_], E, A](private val resource: Lifecycle[F, E, A]) extends AnyVal {
    /**
      * Unsafely acquire the resource and throw away the finalizer,
      * this will leak the resource and cause it to never be cleaned up.
      *
      * This function usually only makes sense in code examples or at top-level,
      * please use [[SyntaxUse#use]] otherwise!
      *
      * @note will acquire the resource without an uninterruptible section
      */
    def unsafeGet()(implicit F: IO2[F]): F[E, A] = {
      F.flatMap[E, resource.InnerResource, A](resource.acquire)(resource.extract[A](_).fold(identity, F.pure))
    }

    /**
      * Unsafely acquire the resource, return it and the finalizer.
      * The resource will be leaked unless the finalizer is used.
      *
      * This function usually only makes sense in code examples or at top-level,
      * please use [[SyntaxUse#use]] otherwise!
      *
      * @note will acquire the resource without an uninterruptible section
      */
    def unsafeAllocate()(implicit F: IO2[F]): F[E, (A, () => F[Nothing, Unit])] = {
      F.flatMap[E, resource.InnerResource, (A, () => F[Nothing, Unit])](resource.acquire) {
        inner =>
          F.map[E, A, (A, () => F[Nothing, Unit])](
            resource.extract[A](inner).fold(identity, F.pure)
          )(a => (a, () => resource.release(inner)))
      }
    }
  }

  /**
    * Specialised [[SyntaxUnsafeGet#unsafeGet]] for the
    * [[Bifunctorized.IdentityBifunctorized]] carrier: runs the underlying MiniBIO
    * and returns the bare `A` (a.k.a. `Identity[A]`) exactly once.
    *
    * Defined as a *separate, more-specific extension class* (`SyntaxUnsafeGetIdentity`)
    * so that callers using `Injector()` (`Lifecycle[IdentityBifunctorized, Throwable, _]`)
    * can rely on the ergonomic `.unsafeGet()` name returning bare `A` rather than
    * `IdentityBifunctorized[Throwable, A]`. Scala 3 implicit-class resolution prefers
    * this class because its parameter type
    * `Lifecycle[IdentityBifunctorized, Throwable, A]` is strictly more specific than
    * the generic `Lifecycle[F, E, A]` of [[SyntaxUnsafeGet]].
    *
    * Failure mode matches [[Bifunctorized.debifunctorizeIdentity]] — typed errors and
    * defects are re-raised as [[Throwable]] via `MiniBIO.run().toThrowable`.
    */
  implicit final class SyntaxUnsafeGetIdentity[A](private val resource: Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, A]) extends AnyVal {
    def unsafeGet(): A = {
      val F: IO2[Bifunctorized.IdentityBifunctorized] = Bifunctorized.identityBifunctorizedHasIO2
      Bifunctorized.debifunctorizeIdentity[A](
        F.flatMap[Throwable, resource.InnerResource, A](resource.acquire)((r: resource.InnerResource) =>
          resource.extract[A](r).fold(identity, (a: A) => F.pure(a))
        )
      )
    }
  }

  /** Convert [[cats.effect.Resource]] to [[Lifecycle]].
    *
    * Transparently bifunctorizes the monofunctor `F[_]`: the resulting Lifecycle's effect type
    * is `Bifunctorized[F, +_, +_]`, the typed-error channel is `Throwable` (the only error
    * channel a monofunctor with a `Sync` instance can express), and the underlying runtime
    * representation remains `F[A]` for any `Bifunctorized[F, E, A]` value (zero-cost).
    */
  def fromCats[F[_], A](
    resource: Resource[F, A]
  )(implicit F: Sync[F]
  ): Lifecycle.FromCats[F, A] = {
    new FromCats[F, A] {
      override def acquire: Bifunctorized[F, Throwable, kernel.Ref[F, List[F[Unit]]]] = {
        Bifunctorized.assert(kernel.Ref.of[F, List[F[Unit]]](Nil)(kernel.Ref.Make.syncInstance(F)))
      }

      override def release(finalizersRef: kernel.Ref[F, List[F[Unit]]]): Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(F.flatMap(finalizersRef.get)(cats.instances.list.catsStdInstancesForList.sequence_(_)(using F)))
      }

      override def extract[B >: A](finalizersRef: kernel.Ref[F, List[F[Unit]]]): Left[Bifunctorized[F, Throwable, B], Nothing] = {
        Left(Bifunctorized.assert(F.widen(allocatedTo(finalizersRef))))
      }

      private def allocatedTo(
        finalizers: kernel.Ref[F, List[F[Unit]]]
      ): F[A] = {
        // Because we have `.uninterruptibleMask` now it's safe to use CE Resource's native `allocated` method.
        // However, note that while CE Resource can express `bracketCase`, when using [[cats.effect.Resource#allocated]]
        // the ability to pass an `Outcome` to the finalizer is lost.
        // Moreover, Lifecycle itself has no ability to express `bracketCase` because `release` does not have
        // an `exit: Exit[E, A]` parameter.
        // FIXME: `Lifecycle.release` should have an `exit` parameter
        F.uncancelable(
          restore =>
            F.flatMap(restore(resource.allocated(F))) {
              case (a, finalizer) =>
                F.as(finalizers.update(finalizer :: _), a)
            }
        )
      }
    }
  }

  /** Convert a Scoped [[zio.ZIO]] to [[Lifecycle]]
    *
    * {{{
    *    def fromZIO[R, E, A](f: ZIO[Scope with R, E, A]): Lifecycle.FromZIO[R, E, A]
    * }}}
    */
  def fromZIO[R]: SyntaxLifecycleFromZIO[R] = new SyntaxLifecycleFromZIO[R]()
  final class SyntaxLifecycleFromZIO[R](private val dummy: Boolean = false) extends AnyVal {
    def apply[E, A](f: ZIO[Scope & R, E, A]): Lifecycle.FromZIO[R, E, A] = {
      implicit val trace: zio.Trace = Tracer.instance.empty

      new FromZIO.FromZIOScoped[R, E, A] {
        override def extract[B >: A](scope: Scope.Closeable): Either[ZIO[R, E, B], B] = Left {
          scope.extend[R](f)
        }
      }
    }
  }

  /** Convert [[zio.ZLayer]] to [[Lifecycle]] */
  def fromZLayer[R, E, A: zio.Tag](layer: ZLayer[R, E, A]): Lifecycle.FromZIO[R, E, A] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    fromZIO[R](layer.build.map(_.get[A](zio.Tag[A])))
  }

  /** Convert [[zio.ZLayer]] to [[Lifecycle]] */
  def fromZLayerZEnv[R, E, A](layer: ZLayer[R, E, A]): Lifecycle.FromZIO[R, E, ZEnvironment[A]] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    fromZIO[R](layer.build)
  }

  /** Convert [[zio.managed.ZManaged]] to [[Lifecycle]] */
  def fromZManaged[R, E, A](managed: ZManaged[R, E, A]): Lifecycle.FromZIO[R, E, A] = {
    implicit val trace: zio.Trace = Tracer.instance.empty

    new FromZIO.FromZIOManaged[R, E, A] {
      override def extract[B >: A](releaseMap: ReleaseMap): Either[ZIO[R, E, B], B] =
        Left {
          ZManaged.currentReleaseMap.locally(releaseMap)(managed.zio).map(_._2)
        }
    }
  }

  /** Convert [[Lifecycle]] to [[cats.effect.Resource]].
    *
    * Inverse of [[fromCats]]: takes a bifunctorized lifecycle over `Bifunctorized[F, +_, +_]`
    * with the `Throwable` error channel and produces a `Resource[F, A]`.
    */
  implicit final class SyntaxLifecycleCats[F[_], +A](private val resource: Lifecycle[Bifunctorized[F, +_, +_], Throwable, A]) extends AnyVal {
    def toCats(implicit F: Sync[F]): Resource[F, A] = {
      Resource
        .make[F, resource.InnerResource](resource.acquire.asInstanceOf[F[resource.InnerResource]])(
          (r: resource.InnerResource) => resource.release(r).asInstanceOf[F[Unit]]
        )
        .evalMap((r: resource.InnerResource) =>
          resource.extract[A](r).fold((eff: Bifunctorized[F, Throwable, A]) => eff.asInstanceOf[F[A]], (F.pure[A]))
        )
    }
  }

  implicit final class SyntaxLifecycleZIO[R, +E, +A](private val resource: Lifecycle[ZIO[R, +_, +_], E, A]) extends AnyVal {
    /** Convert [[Lifecycle]] to scoped [[zio.ZIO]] */
    def toZIO: ZIO[Scope & R, E, A] = {
      implicit val trace: zio.Trace = Tracer.instance.empty

      ZIO.uninterruptibleMask {
        restore =>
          ZIO
            .acquireRelease(
              resource.acquire
            )(resource.release(_)).flatMap {
              r =>
                ZIO.suspendSucceed(restore(resource.extract[A](r).fold(identity, zioSucceedWorkaround)))
            }
      }
    }
  }

  implicit final class SyntaxLifecycleZManaged[R, +E, +A](private val resource: Lifecycle[ZIO[R, +_, +_], E, A]) extends AnyVal {
    /** Convert [[Lifecycle]] to [[zio.managed.ZManaged]] */
    def toZManaged: ZManaged[R, E, A] = {
      implicit val trace: zio.Trace = Tracer.instance.empty

      ZManaged.fromReservationZIO(
        resource.acquire.map(
          r =>
            Reservation(
              ZIO.suspendSucceed(resource.extract[A](r).fold(identity, zioSucceedWorkaround)),
              _ => resource.release(r),
            )
        )
      )
    }
  }

  /**
    * Class-based proxy over a [[Lifecycle]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.Of(Lifecycle.pure(1000))
    * }}}
    *
    * For binding resource values using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    *
    * @note when the expression passed to [[Lifecycle.Of]] defines many local methods
    *       it can hit a Scalac bug https://github.com/scala/bug/issues/11969
    *       and fail to compile, in that case you may switch to [[Lifecycle.OfInner]]
    */
  open class Of[F[+_, +_], +E, +A] private (inner0: () => Lifecycle[F, E, A], @unused dummy: Boolean = false) extends Lifecycle.OfInner[F, E, A] {
    def this(inner: => Lifecycle[F, E, A]) = this(() => inner)

    override val lifecycle: Lifecycle[F, E, A] = inner0()
  }

  /**
    * Class-based proxy over a [[cats.effect.Resource]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfCats(Resource.pure(1000))
    * }}}
    *
    * For binding resource values using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class OfCats[F[_]: Sync, A](inner: => Resource[F, A]) extends Lifecycle.Of[Bifunctorized[F, +_, +_], Throwable, A](fromCats(inner))

  /**
    * Class-based proxy over a scoped [[zio.ZIO]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfZIO(ZIO.acquireRelease(ZIO.succeed(1000))(_ => ZIO.unit))
    * }}}
    *
    * For binding resource values using class syntax in [[izumi.distage.model.definition.ModuleDef ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class OfZIO[R, +E, +A](inner: => ZIO[Scope & R, E, A]) extends Lifecycle.Of[ZIO[R, +_, +_], E, A](fromZIO[R](inner))

  /**
    * Class-based proxy over a [[zio.managed.ZManaged]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfZManaged(ZManaged.succeed(1000))
    * }}}
    *
    * For binding resource values using class syntax in [[izumi.distage.model.definition.ModuleDef ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class OfZManaged[R, +E, +A](inner: => ZManaged[R, E, A]) extends Lifecycle.Of[ZIO[R, +_, +_], E, A](fromZManaged(inner))

  /**
    * Class-based proxy over a [[zio.ZLayer]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfZLayer(ZLayer.succeed(1000))
    * }}}
    *
    * For binding resource values using class syntax in [[izumi.distage.model.definition.ModuleDef ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class OfZLayer[R, +E, +A: zio.Tag](inner: => ZLayer[R, E, A]) extends Lifecycle.Of[ZIO[R, +_, +_], E, A](fromZLayer(inner))

  /**
    * Class-based variant of [[make]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.Make(
    *     acquire = IO(1000)
    *   )(release = _ => IO.unit)
    * }}}
    *
    * For binding resources using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class Make[F[+_, +_], +E, A] private (acquire0: () => F[E, A])(
    release0: A => F[Nothing, Unit],
    @unused dummy: Boolean = false,
  ) extends Lifecycle.Basic[F, E, A] {
    def this(acquire: => F[E, A])(release: A => F[Nothing, Unit]) = this(() => acquire)(release)

    override final def acquire: F[E, A] = acquire0()
    override final def release(resource: A): F[Nothing, Unit] = release0(resource)
  }

  /**
    * Class-based variant of [[make_]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.Make_(IO(1000))(IO.unit)
    * }}}
    *
    * For binding resources using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class Make_[F[+_, +_], +E, A](acquire: => F[E, A])(release: => F[Nothing, Unit]) extends Make[F, E, A](acquire)(_ => release)

  /**
    * Class-based variant of [[makePair]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.MakePair(IO(1000 -> IO.unit))
    * }}}
    *
    * For binding resources using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class MakePair[F[+_, +_], +E, A] private (
    acquire0: () => F[E, (A, F[Nothing, Unit])],
    @unused dummy: Boolean = false,
  ) extends FromPair[F, E, A] {
    def this(acquire: => F[E, (A, F[Nothing, Unit])]) = this(() => acquire)

    override final def acquire: F[E, (A, F[Nothing, Unit])] = acquire0()
  }

  /**
    * Class-based variant of [[liftF]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.LiftF(acquire = IO(1000))
    * }}}
    *
    * For binding resources using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    *
    * @note `acquire` is performed interruptibly, unlike in [[Make]]
    */
  open class LiftF[F[+_, +_]: Applicative2, +E, A] private (acquire0: () => F[E, A], @unused dummy: Boolean) extends NoCloseBase[F, E, A] {
    def this(acquire: => F[E, A]) = this(() => acquire, false)

    override final type InnerResource = Unit
    override final def acquire: F[Nothing, Unit] = Applicative2[F].unit
    override final def extract[B >: A](resource: Unit): Left[F[E, B], Nothing] = Left(Applicative2[F].widen[E, A, B](acquire0()))
  }

  /**
    * Class-based variant of [[fromAutoCloseable]]:
    *
    * {{{
    *   class FileOutputRes extends Lifecycle.FromAutoCloseable(
    *     acquire = IO(new FileOutputStream("abc"))
    *   )
    * }}}
    *
    * For binding resources using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class FromAutoCloseable[F[+_, +_]: IO2, +E, +A <: AutoCloseable](acquire: => F[E, A]) extends Lifecycle.Of[F, E, A](Lifecycle.fromAutoCloseable(acquire))

  /**
    * Trait-based proxy over a [[Lifecycle]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfInner[IO, Int] {
    *     override val lifecycle: Lifecycle[IO, Int] = Lifecycle.pure(1000)
    *   }
    * }}}
    *
    * For binding resource values using class syntax in [[distage.ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    *
    * @note This class may be used instead of [[Lifecycle.Of]] to
    * workaround scalac bug https://github.com/scala/bug/issues/11969
    * when defining local methods
    */
  trait OfInner[F[+_, +_], +E, +A] extends Lifecycle[F, E, A] {
    val lifecycle: Lifecycle[F, E, A]

    override final type InnerResource = lifecycle.InnerResource
    override final def acquire: F[E, lifecycle.InnerResource] = lifecycle.acquire
    override final def release(resource: lifecycle.InnerResource): F[Nothing, Unit] = lifecycle.release(resource)
    override final def extract[B >: A](resource: lifecycle.InnerResource): Either[F[E, B], B] = lifecycle.extract[B](resource)
  }

  trait Self[F[+_, +_], +E, +A] extends Lifecycle[F, E, A] { this: A =>
    def release: F[Nothing, Unit]

    override final type InnerResource = Unit
    override final def release(resource: Unit): F[Nothing, Unit] = release
    override final def extract[B >: A](resource: InnerResource): Right[Nothing, A] = Right(this)
  }

  trait SelfOf[F[+_, +_], +E, +A] extends Lifecycle[F, E, A] { this: A =>
    val inner: Lifecycle[F, E, Unit]

    override final type InnerResource = inner.InnerResource
    override final def acquire: F[E, inner.InnerResource] = inner.acquire
    override final def release(resource: inner.InnerResource): F[Nothing, Unit] = inner.release(resource)
    override final def extract[B >: A](resource: InnerResource): Right[Nothing, A] = Right(this)
  }

  abstract class SelfNoClose[F[+_, +_]: Applicative2, +E, +A] extends Lifecycle.NoCloseBase[F, E, A] { this: A =>
    override type InnerResource = Unit
    override final def extract[B >: A](resource: InnerResource): Right[Nothing, A] = Right(this)
  }

  abstract class NoClose[F[+_, +_]: Applicative2, +E, A] extends Lifecycle.NoCloseBase[F, E, A] with Lifecycle.Basic[F, E, A]

  trait FromPair[F[+_, +_], +E, A] extends Lifecycle[F, E, A] {
    override final type InnerResource = (A, F[Nothing, Unit])
    override final def release(resource: (A, F[Nothing, Unit])): F[Nothing, Unit] = resource._2
    override final def extract[B >: A](resource: (A, F[Nothing, Unit])): Right[Nothing, A] = Right(resource._1)
  }

  trait FromCats[F[_], A] extends Lifecycle[Bifunctorized[F, +_, +_], Throwable, A] {
    override final type InnerResource = kernel.Ref[F, List[F[Unit]]]
  }

  trait FromZIO[R, E, A] extends Lifecycle[ZIO[R, +_, +_], E, A]

  object FromZIO {
    trait FromZIOManaged[R, E, A] extends FromZIO[R, E, A] {
      override final type InnerResource = ReleaseMap

      override final def acquire: ZIO[R, E, ReleaseMap] = {
        ReleaseMap.make(Tracer.instance.empty)
      }

      override final def release(releaseMap: ReleaseMap): ZIO[R, Nothing, Unit] = {
        implicit val trace: zio.Trace = Tracer.instance.empty

        releaseMap.releaseAll(zio.Exit.succeed(()), zio.ExecutionStrategy.Sequential).unit
      }
    }

    trait FromZIOScoped[R, E, A] extends FromZIO[R, E, A] {
      override final type InnerResource = Scope.Closeable

      override final def acquire: ZIO[R, E, Scope.Closeable] = {
        Scope.make(Tracer.instance.empty)
      }

      override final def release(scope: Scope.Closeable): ZIO[R, Nothing, Unit] = {
        implicit val trace: zio.Trace = Tracer.instance.empty

        scope.close(zio.Exit.succeed(()))
      }

      disableAutoTrace.discard()
    }
  }

  abstract class NoCloseBase[F[+_, +_]: Applicative2, +E, +A] extends Lifecycle[F, E, A] {
    override final def release(resource: InnerResource): F[Nothing, Unit] = Applicative2[F].unit
  }

  // Workaround for the craziest, strangest bincompat failure on Scala 3:
  // [error] Test suite izumi.distage.impl.OptionalDependencyTest failed with java.lang.NoClassDefFoundError: zio/ZIO
  // at izumi.distage.impl.OptionalDependencyTest.f$proxy5$1(OptionalDependencyTest.scala:73
  // appeared in update from zio-2.1.5 to zio-2.1.7 https://github.com/7mind/izumi/pull/2159/
  // only relevant change was ZIOCompanionVersionSpecific became a 'transparent trait' from regular trait
  // BUT using zio.Exit.Success, which is not a trait at all, didn't fix the issue.
  // no idea wtf happened, why it broke and why _method internals_ are breaking bincompat/optionality here
  // Seems like this is the cause of the compat failure - https://github.com/zio/zio/pull/9047
  // - but I still don't understand why zio.Exit.Success is affected and why obscuring the return type is
  // necessary here.
  private def zioSucceedWorkaround[F[x] >: ZIO[Any, Nothing, x], A](a: A): F[A] = {
    zio.Exit.Success(a)
  }
  // Another workaround for a Scala 3 bincompat failure:
  // java.lang.NoClassDefFoundError: zio/CanFail.
  // Appeared in an update from zio 2.1.14 to 2.1.16
  @scala.annotation.nowarn("msg=never used")
  private implicit def zioCanFailWorkaround[F[x] >: zio.CanFail[x], E]: F[E] = null
}

private[izumi] sealed trait LifecycleInstances {
  implicit final def monad2ForLifecycle[F[+_, +_]: IO2: Primitives2]: Monad2[Lifecycle[F, +_, +_]] =
    new Monad2[Lifecycle[F, +_, +_]] {
      override def map[E, A, B](r: Lifecycle[F, E, A])(f: A => B): Lifecycle[F, E, B] = r.map(f)
      override def flatMap[E, A, B](r: Lifecycle[F, E, A])(f: A => Lifecycle[F, E, B]): Lifecycle[F, E, B] = r.flatMap(f)
      override def pure[A](a: A): Lifecycle[F, Nothing, A] = Lifecycle.pure[F](a)
    }
}
