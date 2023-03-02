package izumi.functional.lifecycle

import cats.Applicative
import cats.effect.kernel
import cats.effect.kernel.{GenConcurrent, Resource, Sync}
import izumi.functional.quasi.*
import izumi.functional.bio.data.Morphism1
import izumi.functional.bio.{Fiber2, Fork2, Functor2, Functor3}
import izumi.fundamentals.orphans.{`cats.Functor`, `cats.Monad`, `cats.kernel.Monoid`}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.*
import zio.*
import zio.ZManaged.ReleaseMap

import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.annotation.unused

/**
  * `Lifecycle` is a class that describes the effectful allocation of a resource and its finalizer.
  * This can be used to represent expensive resources.
  *
  * Resources can be created using [[Lifecycle.make]]:
  *
  * {{{
  *   def open(file: File): Lifecycle[IO, BufferedReader] =
  *     Lifecycle.make(
  *       acquire = IO { new BufferedReader(new FileReader(file)) }
  *     )(release = reader => IO { reader.close() })
  * }}}
  *
  * Using inheritance from [[Lifecycle.Basic]]:
  *
  * {{{
  *   final class BufferedReaderResource(
  *     file: File
  *   ) extends Lifecycle.Basic[IO, BufferedReader] {
  *     def acquire: IO[BufferedReader] = IO { new BufferedReader(new FileReader(file)) }
  *     def release(reader: BufferedReader): IO[BufferedReader] = IO { reader.close() }
  *   }
  * }}}
  *
  * Using constructor-based inheritance from [[Lifecycle.Make]], [[Lifecycle.LiftF]], etc:
  *
  * {{{
  *   final class BufferedReaderResource(
  *     file: File
  *   ) extends Lifecycle.Make[IO, BufferedReader](
  *     acquire = IO { new BufferedReader(new FileReader(file)) },
  *     release = reader => IO { reader.close() },
  *   )
  * }}}
  *
  * Or by converting from an existing [[cats.effect.Resource]] or a [[zio.ZManaged]]:
  *   - Use [[Lifecycle.fromCats]], [[Lifecycle.SyntaxLifecycleCats#toCats]] to convert from and to a [[cats.effect.Resource]]
  *   - And [[Lifecycle.fromZIO]], [[Lifecycle.SyntaxLifecycleZIO#toZIO]] to convert from and to a [[zio.ZManaged]]
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
  * Lifecycles can be combined into larger Lifecycles via [[Lifecycle#flatMap]] (and the associated for-comprehension syntax):
  *
  * {{{
  *  val res: Lifecycle[IO, (BufferedReader, BufferedReader)] = {
  *    for {
  *      reader1 <- open(file1)
  *      reader2 <- open(file2)
  *    } yield (reader1, reader2)
  *  }
  * }}}
  *
  * Nested resources are released in reverse order of acquisition. Outer resources are
  * released even if an inner use or release fails.
  *
  * `Lifecycle` can be used without an effect-type with [[Lifecycle.Simple]]
  * it can also mimic Java's initialization-after-construction with [[Lifecycle.Mutable]]
  *
  * Use Lifecycle's to specify lifecycles of objects injected into the object graph.
  *
  *  {{{
  *   import distage.{Lifecycle, ModuleDef, Injector}
  *   import cats.effect.IO
  *
  *   class DBConnection
  *   class MessageQueueConnection
  *
  *   val dbResource = Lifecycle.make(IO { println("Connecting to DB!"); new DBConnection })(_ => IO(println("Disconnecting DB")))
  *   val mqResource = Lifecycle.make(IO { println("Connecting to Message Queue!"); new MessageQueueConnection })(_ => IO(println("Disconnecting Message Queue")))
  *
  *   class MyApp(db: DBConnection, mq: MessageQueueConnection) {
  *     val run = IO(println("Hello World!"))
  *   }
  *
  *   val module = new ModuleDef {
  *     make[DBConnection].fromResource(dbResource)
  *     make[MessageQueueConnection].fromResource(mqResource)
  *     make[MyApp]
  *   }
  *
  *   Injector[IO]()
  *     .produceGet[MyApp](module)
  *     .use(_.run())
  *     .unsafeRunSync()
  * }}}
  *
  * Will produce the following output:
  *
  * {{{
  *   Connecting to DB!
  *   Connecting to Message Queue!
  *   Hello World!
  *   Disconnecting Message Queue
  *   Disconnecting DB
  * }}}
  *
  * The lifecycle of the entire object graph is itself expressed with `Lifecycle`,
  * you can control it by controlling the scope of `.use` or by manually invoking
  * [[Lifecycle#acquire]] and [[Lifecycle#release]].
  *
  * == Inheritance helpers ==
  *
  * The following helpers allow defining `Lifecycle` sub-classes using expression-like syntax:
  *
  *  - [[Lifecycle.Of]]
  *  - [[Lifecycle.OfInner]]
  *  - [[Lifecycle.OfCats]]
  *  - [[Lifecycle.OfZIO]]
  *  - [[Lifecycle.LiftF]]
  *  - [[Lifecycle.Make]]
  *  - [[Lifecycle.Make_]]
  *  - [[Lifecycle.MakePair]]
  *  - [[Lifecycle.FromAutoCloseable]]
  *  - [[Lifecycle.SelfOf]]
  *  - [[Lifecycle.MutableOf]]
  *
  * The main reason to employ them is to workaround a limitation in Scala 2's eta-expansion — when converting a method to a function value,
  * Scala always tries to fulfill implicit parameters eagerly instead of making them parameters of the function value,
  * this limitation makes it harder to inject implicits using `distage`.
  *
  * However, when using `distage`'s type-based syntax: `make[A].fromResource[A.Resource[F]]` —
  * this limitation does not apply and implicits inject successfully.
  *
  * So to workaround the limitation you can convert an expression based resource-constructor such as:
  *
  * {{{
  *   import distage.Lifecycle, cats.Monad
  *
  *   class A
  *   object A {
  *     def resource[F[_]](implicit F: Monad[F]): Lifecycle[F, A] = Lifecycle.pure(new A)
  *   }
  * }}}
  *
  * Into a class-based form:
  *
  * {{{
  *   import distage.Lifecycle, cats.Monad
  *
  *   class A
  *   object A {
  *     final class Resource[F[_]](implicit F: Monad[F])
  *       extends Lifecycle.Of(
  *         Lifecycle.pure(new A)
  *       )
  *   }
  * }}}
  *
  * And inject successfully using `make[A].fromResource[A.Resource[F]]` syntax of [[izumi.distage.model.definition.dsl.ModuleDefDSL]].
  *
  * The following helpers ease defining `Lifecycle` sub-classes using traditional inheritance where `acquire`/`release` parts are defined as methods:
  *
  *  - [[Lifecycle.Basic]]
  *  - [[Lifecycle.Simple]]
  *  - [[Lifecycle.Mutable]]
  *  - [[Lifecycle.MutableNoClose]]
  *  - [[Lifecycle.Self]]
  *  - [[Lifecycle.SelfNoClose]]
  *  - [[Lifecycle.NoClose]]
  *
  * @see [[izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSLBase#fromResource ModuleDef.fromResource]]
  * @see [[https://typelevel.org/cats-effect/datatypes/resource.html cats.effect.Resource]]
  * @see [[https://zio.dev/docs/datatypes/datatypes_managed zio.ZManaged]]
  */
trait Lifecycle[+F[_], +A] {
  type InnerResource

  /**
    * The action in `F` used to acquire the resource.
    *
    * @note the `acquire` action is performed *uninterruptibly*,
    * when `F` is an effect type that supports interruption/cancellation.
    */
  def acquire: F[InnerResource]

  /**
    * The action in `F` used to release, close or deallocate the resource
    * after it has been acquired and used through [[izumi.distage.model.definition.Lifecycle.SyntaxUse#use]].
    *
    * @note the `release` action is performed *uninterruptibly*,
    * when `F` is an effect type that supports interruption/cancellation.
    */
  def release(resource: InnerResource): F[Unit]

  /**
    * Either an action in `F` or a pure function used to
    * extract the `A` from the `InnerResource`
    *
    * The effect in the `Left` branch will be performed *interruptibly*,
    * it is not afforded the same kind of safety as `acquire` and `release` actions
    * when `F` is an effect type that supports interruption/cancellation.
    *
    * When `F` is `Identity`, it doesn't matter whether the output is a `Left` or `Right` branch.
    *
    * When consuming the output of `extract` you can use `_.fold(identity, F.pure)` to convert the `Either` to `F[B]`
    *
    * @see [[Lifecycle.Basic]] `extract` doesn't have to be defined when inheriting from `Lifecycle.Basic`
    */
  def extract[B >: A](resource: InnerResource): Either[F[B], B]

  final def map[G[x] >: F[x]: QuasiFunctor, B](f: A => B): Lifecycle[G, B] = LifecycleMethodImpls.mapImpl[G, A, B](this)(f)
  final def flatMap[G[x] >: F[x]: QuasiPrimitives, B](f: A => Lifecycle[G, B]): Lifecycle[G, B] =
    LifecycleMethodImpls.flatMapImpl[G, A, B](this)(f)
  final def flatten[G[x] >: F[x]: QuasiPrimitives, B](implicit ev: A <:< Lifecycle[G, B]): Lifecycle[G, B] = this.flatMap(ev)

  final def catchAll[G[x] >: F[x]: QuasiIO, B >: A](recover: Throwable => Lifecycle[G, B]): Lifecycle[G, B] =
    LifecycleMethodImpls.redeemImpl[G, A, B](this)(recover, Lifecycle.pure[G](_))
  final def catchSome[G[x] >: F[x]: QuasiIO, B >: A](recover: PartialFunction[Throwable, Lifecycle[G, B]]): Lifecycle[G, B] =
    catchAll(e => recover.applyOrElse(e, (_: Throwable) => Lifecycle.fail(e)))

  final def redeem[G[x] >: F[x]: QuasiIO, B](onFailure: Throwable => Lifecycle[G, B], onSuccess: A => Lifecycle[G, B]): Lifecycle[G, B] =
    LifecycleMethodImpls.redeemImpl[G, A, B](this)(onFailure, onSuccess)

  final def evalMap[G[x] >: F[x]: QuasiPrimitives, B](f: A => G[B]): Lifecycle[G, B] = LifecycleMethodImpls.evalMapImpl[G, A, B](this)(f)
  final def evalTap[G[x] >: F[x]: QuasiPrimitives](f: A => G[Unit]): Lifecycle[G, A] =
    evalMap[G, A](a => QuasiFunctor[G].map(f(a))(_ => a))

  /** Wrap acquire action of this resource in another effect, e.g. for logging purposes */
  final def wrapAcquire[G[x] >: F[x]](f: (=> G[InnerResource]) => G[InnerResource]): Lifecycle[G, A] =
    LifecycleMethodImpls.wrapAcquireImpl[G, A](this: this.type)(f)

  /** Wrap release action of this resource in another effect, e.g. for logging purposes */
  final def wrapRelease[G[x] >: F[x]](f: (InnerResource => G[Unit], InnerResource) => G[Unit]): Lifecycle[G, A] =
    LifecycleMethodImpls.wrapReleaseImpl[G, A](this: this.type)(f)

  final def beforeAcquire[G[x] >: F[x]: QuasiApplicative](f: => G[Unit]): Lifecycle[G, A] =
    wrapAcquire[G](acquire => QuasiApplicative[G].map2(f, acquire)((_, res) => res))

  /** Prepend release action to existing */
  final def beforeRelease[G[x] >: F[x]: QuasiApplicative](f: InnerResource => G[Unit]): Lifecycle[G, A] =
    wrapRelease[G]((release, res) => QuasiApplicative[G].map2(f(res), release(res))((_, _) => ()))

  final def void[G[x] >: F[x]: QuasiFunctor]: Lifecycle[G, Unit] = map[G, Unit](_ => ())

  @inline final def widen[B >: A]: Lifecycle[F, B] = this
  @inline final def widenF[G[x] >: F[x]]: Lifecycle[G, A] = this
}

object Lifecycle extends LifecycleInstances {

  /**
    * A sub-trait of [[izumi.distage.model.definition.Lifecycle]] suitable for less-complex resource definitions via inheritance
    * that do not require overriding [[izumi.distage.model.definition.Lifecycle#InnerResource]].
    *
    * {{{
    *   final class BufferedReaderResource(
    *     file: File
    *   ) extends Lifecycle.Basic[IO, BufferedReader] {
    *     def acquire: IO[BufferedReader] = IO { new BufferedReader(new FileReader(file)) }
    *     def release(reader: BufferedReader): IO[BufferedReader] = IO { reader.close() }
    *   }
    * }}}
    */
  trait Basic[+F[_], A] extends Lifecycle[F, A] {
    def acquire: F[A]
    def release(resource: A): F[Unit]

    override final def extract[B >: A](resource: A): Right[Nothing, A] = Right(resource)
    override final type InnerResource = A
  }

  def make[F[_], A](acquire: => F[A])(release: A => F[Unit]): Lifecycle[F, A] = {
    @inline def a = acquire; @inline def r = release
    new Lifecycle.Basic[F, A] {
      override def acquire: F[A] = a
      override def release(resource: A): F[Unit] = r(resource)
    }
  }

  def make_[F[_], A](acquire: => F[A])(release: => F[Unit]): Lifecycle[F, A] = {
    make(acquire)(_ => release)
  }

  def makeSimple[A](acquire: => A)(release: A => Unit): Lifecycle[Identity, A] = {
    make[Identity, A](acquire)(release)
  }

  def makePair[F[_], A](allocate: F[(A, F[Unit])]): Lifecycle[F, A] = {
    new Lifecycle.FromPair[F, A] {
      override def acquire: F[(A, F[Unit])] = allocate
    }
  }

  /** @param effect is performed interruptibly, unlike in [[make]] */
  def liftF[F[_], A](effect: => F[A])(implicit F: QuasiApplicative[F]): Lifecycle[F, A] = {
    new Lifecycle.LiftF(effect)
  }

  /** @param effect is performed interruptibly, unlike in [[make]] */
  def suspend[F[_]: QuasiPrimitives, A](effect: => F[Lifecycle[F, A]]): Lifecycle[F, A] = {
    liftF(effect).flatten
  }

  /**
    * Fork the specified action into a new fiber.
    * When this `Lifecycle` is released, the fiber will be interrupted using [[izumi.functional.bio.Fiber2#interrupt]]
    *
    * @return The [[izumi.functional.bio.Fiber2 fiber]] running `f` action
    */
  def fork[F[+_, +_]: Fork2, E, A](f: F[E, A]): Lifecycle[F[Nothing, _], Fiber2[F, E, A]] = {
    Lifecycle.make(f.fork)(_.interrupt)
  }

  /** @see [[fork]] */
  def fork_[F[+_, +_]: Fork2: Functor2, E, A](f: F[E, A]): Lifecycle[F[Nothing, _], Unit] = {
    Lifecycle.fork(f).void
  }

  /**
    * Fork the specified action into a new fiber.
    * When this `Lifecycle` is released, the fiber will be interrupted using [[cats.effect.Fiber#cancel]]
    *
    * @return The fiber running `f` action
    */
  def forkCats[F[_], E, A](f: F[A])(implicit F: GenConcurrent[F, E]): Lifecycle[F, cats.effect.Fiber[F, E, A]] = {
    Lifecycle.make(F.start(f))(_.cancel)
  }

  def traverse[F[_]: QuasiPrimitives, A, B](l: Iterable[A])(f: A => Lifecycle[F, B]): Lifecycle[F, List[B]] = {
    l.foldLeft(pure[F](List.empty[B])) {
      (acc, a) => acc.flatMap(list => f(a).map(r => list ++ List(r)))
    }
  }

  def traverse_[F[_]: QuasiPrimitives, A](l: Iterable[A])(f: A => Lifecycle[F, Unit]): Lifecycle[F, Unit] = {
    l.foldLeft(unit) {
      (acc, a) => acc.flatMap(_ => f(a))
    }
  }

  def fromAutoCloseable[F[_], A <: AutoCloseable](acquire: => F[A])(implicit F: QuasiIO[F]): Lifecycle[F, A] = {
    make(acquire)(a => F.maybeSuspend(a.close()))
  }
  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): Lifecycle[Identity, A] = {
    makeSimple(acquire)(_.close)
  }

  def fromExecutorService[F[_], A <: ExecutorService](acquire: => F[A])(implicit F: QuasiIO[F]): Lifecycle[F, A] = {
    make(acquire) {
      es =>
        F.maybeSuspend {
          if (!(es.isShutdown || es.isTerminated)) {
            es.shutdown()
            if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
              es.shutdownNow().discard()
            }
          }
        }
    }
  }

  def fromExecutorService[A <: ExecutorService](acquire: => A): Lifecycle[Identity, A] = {
    fromExecutorService[Identity, A](acquire)
  }

  @inline def pure[F[_]]: SyntaxPure[F] = new SyntaxPure[F]
  implicit final class SyntaxPure[F[_]](private val dummy: Boolean = false) extends AnyVal {
    @inline def apply[A](a: A)(implicit F: QuasiApplicative[F]): Lifecycle[F, A] = {
      Lifecycle.liftF(F.pure(a))
    }
  }

  def unit[F[_]](implicit F: QuasiApplicative[F]): Lifecycle[F, Unit] = {
    Lifecycle.liftF(F.unit)
  }

  def fail[F[_], A](error: => Throwable)(implicit F: QuasiIO[F]): Lifecycle[F, A] = {
    Lifecycle.liftF(F.fail(error))
  }

  implicit final class SyntaxUse[F[_], +A](private val resource: Lifecycle[F, A]) extends AnyVal {
    def use[B](use: A => F[B])(implicit F: QuasiPrimitives[F]): F[B] = {
      F.bracket(acquire = resource.acquire)(release = resource.release)(
        use = a =>
          F.suspendF(resource.extract(a) match {
            case Left(effect) => F.flatMap(effect)(use)
            case Right(value) => use(value)
          })
      )
    }
  }

  implicit final class SyntaxUseEffect[F[_], A](private val resource: Lifecycle[F, F[A]]) extends AnyVal {
    def useEffect(implicit F: QuasiPrimitives[F]): F[A] =
      resource.use(identity)
  }

  implicit final class SyntaxLifecycleIdentity[+A](private val resource: Lifecycle[Identity, A]) extends AnyVal {
    def toEffect[F[_]](implicit F: QuasiIO[F]): Lifecycle[F, A] = {
      new Lifecycle[F, A] {
        override type InnerResource = resource.InnerResource
        override def acquire: F[InnerResource] = F.maybeSuspend(resource.acquire)
        override def release(res: InnerResource): F[Unit] = F.maybeSuspend(resource.release(res))
        override def extract[B >: A](res: InnerResource): Either[F[B], B] = Right(resource.extract(res).merge)
      }
    }
  }

  implicit final class SyntaxUnsafeGet[F[_], A](private val resource: Lifecycle[F, A]) extends AnyVal {
    /**
      * Unsafely acquire the resource and throw away the finalizer,
      * this will leak the resource and cause it to never be cleaned up.
      *
      * This function only makes sense in code examples or at top-level,
      * please use [[SyntaxUse#use]] instead!
      *
      * @note will also acquire the resource without an uninterruptible section
      */
    def unsafeGet()(implicit F: QuasiPrimitives[F]): F[A] = {
      F.flatMap(resource.acquire)(resource.extract(_).fold(identity, F.pure))
    }
  }

  implicit final class SyntaxWidenError[F[+_, +_], +E, +A](private val resource: Lifecycle[F[E, _], A]) extends AnyVal {
    def widenError[E1 >: E]: Lifecycle[F[E1, _], A] = resource
  }

  /** Convert [[cats.effect.Resource]] to [[Lifecycle]] */
  def fromCats[F[_], A](resource: Resource[F, A])(implicit F: Sync[F]): FromCats[F, A] = {
    new FromCats[F, A] {
      override def acquire: F[kernel.Ref[F, List[F[Unit]]]] = {
        kernel.Ref.of[F, List[F[Unit]]](Nil)(kernel.Ref.Make.syncInstance(F))
      }

      override def release(finalizersRef: kernel.Ref[F, List[F[Unit]]]): F[Unit] = {
        F.flatMap(finalizersRef.get)(cats.instances.list.catsStdInstancesForList.sequence_(_))
      }

      override def extract[B >: A](finalizersRef: kernel.Ref[F, List[F[Unit]]]): Left[F[B], Nothing] = {
        Left(F.widen(allocatedTo(finalizersRef)))
      }

      private[this] def allocatedTo(
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
            F.flatMap(restore(resource.allocated)) {
              case (a, finalizer) =>
                F.as(finalizers.update(finalizer :: _), a)
            }
        )
      }
    }
  }

  /** Convert [[zio.ZManaged]] to [[Lifecycle]] */
  def fromZIO[R, E, A](managed: ZManaged[R, E, A]): FromZIO[R, E, A] = {
    new FromZIO[R, E, A] {
      override def extract[B >: A](releaseMap: ReleaseMap): Left[ZIO[R, E, A], Nothing] =
        Left(managed.zio.provideSome[R](_ -> releaseMap).map(_._2))
    }
  }

  implicit final class SyntaxLifecycleMapK[+F[_], +A](private val resource: Lifecycle[F, A]) extends AnyVal {
    def mapK[G[x] >: F[x], H[_]](f: Morphism1[G, H]): Lifecycle[H, A] = {
      new Lifecycle[H, A] {
        override type InnerResource = resource.InnerResource
        override def acquire: H[InnerResource] = f(resource.acquire)
        override def release(res: InnerResource): H[Unit] = f(resource.release(res))
        override def extract[B >: A](res: InnerResource): Either[H[B], B] = resource.extract(res).left.map {
          (fa: F[A]) => f(fa.asInstanceOf[G[B]])
        }
      }
    }
  }

  implicit final class SyntaxLifecycleCats[+F[_], +A](private val resource: Lifecycle[F, A]) extends AnyVal {
    /** Convert [[Lifecycle]] to [[cats.effect.Resource]] */
    def toCats[G[x] >: F[x]: Applicative]: Resource[G, A] = {
      Resource
        .make[G, resource.InnerResource](resource.acquire)(resource.release)
        .evalMap(resource.extract(_).fold(identity, Applicative[G].pure))
    }
  }

  implicit final class SyntaxLifecycleZIO[-R, +E, +A](private val resource: Lifecycle[ZIO[R, E, _], A]) extends AnyVal {
    /** Convert [[Lifecycle]] to [[zio.ZManaged]] */
    def toZIO: ZManaged[R, E, A] = {
      ZManaged.makeReserve(
        resource.acquire.map(
          r =>
            Reservation(
              ZIO.effectSuspendTotal(resource.extract(r).fold(identity, ZIO.succeed(_))),
              _ =>
                resource
                  .release(r).orDieWith {
                    case e: Throwable => e
                    case any => new RuntimeException(s"Lifecycle finalizer: $any")
                  },
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
    * For binding resource values using class syntax in [[ModuleDef]]:
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
  open class Of[+F[_], +A] private[this] (inner0: () => Lifecycle[F, A], @unused dummy: Boolean = false) extends Lifecycle.OfInner[F, A] {
    def this(inner: => Lifecycle[F, A]) = this(() => inner)

    override val lifecycle: Lifecycle[F, A] = inner0()
  }

  /**
    * Class-based proxy over a [[cats.effect.Resource]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfCats(Resource.pure(1000))
    * }}}
    *
    * For binding resource values using class syntax in [[ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class OfCats[F[_]: Sync, A](inner: => Resource[F, A]) extends Lifecycle.Of[F, A](fromCats(inner))

  /**
    * Class-based proxy over a [[zio.ZManaged]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfZIO(Managed.succeed(1000))
    * }}}
    *
    * For binding resource values using class syntax in [[ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class OfZIO[-R, +E, +A](inner: => ZManaged[R, E, A]) extends Lifecycle.Of[ZIO[R, E, _], A](fromZIO(inner))

  /**
    * Class-based variant of [[make]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.Make(
    *     acquire = IO(1000)
    *   )(release = _ => IO.unit)
    * }}}
    *
    * For binding resources using class syntax in [[ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class Make[+F[_], A] private[this] (acquire0: () => F[A])(release0: A => F[Unit], @unused dummy: Boolean = false) extends Lifecycle.Basic[F, A] {
    def this(acquire: => F[A])(release: A => F[Unit]) = this(() => acquire)(release)

    override final def acquire: F[A] = acquire0()
    override final def release(resource: A): F[Unit] = release0(resource)
  }

  /**
    * Class-based variant of [[make_]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.Make_(IO(1000))(IO.unit)
    * }}}
    *
    * For binding resources using class syntax in [[ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class Make_[+F[_], A](acquire: => F[A])(release: => F[Unit]) extends Make[F, A](acquire)(_ => release)

  /**
    * Class-based variant of [[makePair]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.MakePair(IO(1000 -> IO.unit))
    * }}}
    *
    * For binding resources using class syntax in [[ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class MakePair[F[_], A] private[this] (acquire0: () => F[(A, F[Unit])], @unused dummy: Boolean = false) extends FromPair[F, A] {
    def this(acquire: => F[(A, F[Unit])]) = this(() => acquire)

    override final def acquire: F[(A, F[Unit])] = acquire0()
  }

  /**
    * Class-based variant of [[liftF]]:
    *
    * {{{
    *   class IntRes extends Lifecycle.LiftF(acquire = IO(1000))
    * }}}
    *
    * For binding resources using class syntax in [[ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    *
    * @note `acquire` is performed interruptibly, unlike in [[Make]]
    */
  open class LiftF[+F[_]: QuasiApplicative, A] private[this] (acquire0: () => F[A], @unused dummy: Boolean) extends NoCloseBase[F, A] {
    def this(acquire: => F[A]) = this(() => acquire, false)

    override final type InnerResource = Unit
    override final def acquire: F[Unit] = QuasiApplicative[F].unit
    override final def extract[B >: A](resource: Unit): Left[F[B], Nothing] = Left(QuasiApplicative[F].widen(acquire0()))
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
    * For binding resources using class syntax in [[ModuleDef]]:
    *
    * {{{
    *   val module = new ModuleDef {
    *     make[Int].fromResource[IntRes]
    *   }
    * }}}
    */
  open class FromAutoCloseable[+F[_]: QuasiIO, +A <: AutoCloseable](acquire: => F[A]) extends Lifecycle.Of(Lifecycle.fromAutoCloseable(acquire))

  /**
    * Trait-based proxy over a [[Lifecycle]] value
    *
    * {{{
    *   class IntRes extends Lifecycle.OfInner[IO, Int] {
    *     override val lifecycle: Lifecycle[IO, Int] = Lifecycle.pure(1000)
    *   }
    * }}}
    *
    * For binding resource values using class syntax in [[ModuleDef]]:
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
  trait OfInner[+F[_], +A] extends Lifecycle[F, A] {
    val lifecycle: Lifecycle[F, A]

    override final type InnerResource = lifecycle.InnerResource
    override final def acquire: F[lifecycle.InnerResource] = lifecycle.acquire
    override final def release(resource: lifecycle.InnerResource): F[Unit] = lifecycle.release(resource)
    override final def extract[B >: A](resource: lifecycle.InnerResource): Either[F[B], B] = lifecycle.extract(resource)
  }

  trait Simple[A] extends Lifecycle.Basic[Identity, A]

  trait Mutable[+A] extends Lifecycle.Self[Identity, A] { this: A => }

  trait Self[+F[_], +A] extends Lifecycle[F, A] { this: A =>
    def release: F[Unit]

    override final type InnerResource = Unit
    override final def release(resource: Unit): F[Unit] = release
    override final def extract[B >: A](resource: InnerResource): Right[Nothing, A] = Right(this)
  }

  trait MutableOf[+A] extends Lifecycle.SelfOf[Identity, A] { this: A => }

  trait SelfOf[+F[_], +A] extends Lifecycle[F, A] { this: A =>
    val inner: Lifecycle[F, Unit]

    override final type InnerResource = inner.InnerResource
    override final def acquire: F[inner.InnerResource] = inner.acquire
    override final def release(resource: inner.InnerResource): F[Unit] = inner.release(resource)
    override final def extract[B >: A](resource: InnerResource): Right[Nothing, A] = Right(this)
  }

  trait MutableNoClose[+A] extends Lifecycle.SelfNoClose[Identity, A] { this: A => }

  abstract class SelfNoClose[+F[_]: QuasiApplicative, +A] extends Lifecycle.NoCloseBase[F, A] { this: A =>
    override type InnerResource = Unit
    override final def extract[B >: A](resource: InnerResource): Right[Nothing, A] = Right(this)
  }

  abstract class NoClose[+F[_]: QuasiApplicative, A] extends Lifecycle.NoCloseBase[F, A] with Lifecycle.Basic[F, A]

  trait FromPair[F[_], A] extends Lifecycle[F, A] {
    override final type InnerResource = (A, F[Unit])
    override final def release(resource: (A, F[Unit])): F[Unit] = resource._2
    override final def extract[B >: A](resource: (A, F[Unit])): Right[Nothing, A] = Right(resource._1)
  }

  trait FromCats[F[_], A] extends Lifecycle[F, A] {
    override final type InnerResource = kernel.Ref[F, List[F[Unit]]]
  }

  trait FromZIO[R, E, A] extends Lifecycle[ZIO[R, E, _], A] {
    override final type InnerResource = ReleaseMap
    override final def acquire: ZIO[R, E, ReleaseMap] = ReleaseMap.make
    override final def release(releaseMap: ReleaseMap): ZIO[R, Nothing, Unit] = releaseMap.releaseAll(zio.Exit.succeed(()), zio.ExecutionStrategy.Sequential).unit
  }

  abstract class NoCloseBase[+F[_]: QuasiApplicative, +A] extends Lifecycle[F, A] {
    override final def release(resource: InnerResource): F[Unit] = QuasiApplicative[F].unit
  }
}

private[izumi] sealed trait LifecycleInstances extends LifecycleCatsInstances {
  implicit final def functor2ForLifecycle[F[+_, +_]: Functor2]: Functor2[Lifecycle2[F, +_, +_]] = new Functor2[Lifecycle2[F, +_, +_]] {
    override def map[R, E, A, B](r: Lifecycle[F[E, _], A])(f: A => B): Lifecycle[F[E, _], B] = r.map(f)
  }

  implicit final def functor3ForLifecycle[F[-_, +_, +_]: Functor3]: Functor3[Lifecycle3[F, -_, +_, +_]] = new Functor3[Lifecycle3[F, -_, +_, +_]] {
    override def map[R, E, A, B](r: Lifecycle[F[R, E, _], A])(f: A => B): Lifecycle[F[R, E, _], B] = r.map(f)
  }
}

private[izumi] sealed trait LifecycleCatsInstances extends LifecycleCatsInstancesLowPriority {
  implicit final def catsMonadForLifecycle[Monad[_[_]]: `cats.Monad`, F[_]](
    implicit F: QuasiPrimitives[F]
  ): Monad[Lifecycle[F, _]] = {
    new cats.StackSafeMonad[Lifecycle[F, _]] {
      override def pure[A](x: A): Lifecycle[F, A] = Lifecycle.pure[F](x)
      override def flatMap[A, B](fa: Lifecycle[F, A])(f: A => Lifecycle[F, B]): Lifecycle[F, B] = fa.flatMap(f)
    }.asInstanceOf[Monad[Lifecycle[F, _]]]
  }

  implicit final def catsMonoidForLifecycle[Monoid[_]: `cats.kernel.Monoid`, F[_], A](
    implicit
    F: QuasiPrimitives[F],
    A0: Monoid[A],
  ): Monoid[Lifecycle[F, A]] = {
    val A = A0.asInstanceOf[cats.Monoid[A]]
    new cats.Monoid[Lifecycle[F, A]] {
      override def empty: Lifecycle[F, A] = Lifecycle.pure[F](A.empty)
      override def combine(x: Lifecycle[F, A], y: Lifecycle[F, A]): Lifecycle[F, A] = {
        for {
          rx <- x
          ry <- y
        } yield A.combine(rx, ry)
      }
    }.asInstanceOf[Monoid[Lifecycle[F, A]]]
  }
}

private[izumi] sealed trait LifecycleCatsInstancesLowPriority {
  implicit final def catsFunctorForLifecycle[F[_], Functor[_[_]]: `cats.Functor`](
    implicit F: QuasiFunctor[F]
  ): Functor[Lifecycle[F, _]] = {
    new cats.Functor[Lifecycle[F, _]] {
      override def map[A, B](fa: Lifecycle[F, A])(f: A => B): Lifecycle[F, B] = fa.map(f)
    }.asInstanceOf[Functor[Lifecycle[F, _]]]
  }
}
