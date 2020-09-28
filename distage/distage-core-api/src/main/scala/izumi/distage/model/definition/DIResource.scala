package izumi.distage.model.definition

import java.util.concurrent.{ExecutorService, TimeUnit}

import cats.effect.Bracket
import cats.{Applicative, ~>}
import izumi.distage.constructors.HasConstructor
import izumi.distage.model.Locator
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.effect.{DIApplicative, DIEffect}
import izumi.distage.model.providers.Functoid
import izumi.functional.bio.BIOLocal
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.language.unused
import izumi.reflect.{Tag, TagK, TagK3, TagMacro}
import zio.ZManaged.ReleaseMap
import zio._

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

/**
  * `DIResource` is a class that captures the effectful allocation of a resource, along with its finalizer.
  * This can be used to wrap expensive resources.
  *
  * Resources can be created using [[DIResource.make]]:
  *
  * {{{
  *   def open(file: File): DIResource[IO, BufferedReader] =
  *     DIResource.make(IO { new BufferedReader(new FileReader(file)) })(in => IO { in.close() })
  * }}}
  *
  * Using inheritance from [[DIResource]]:
  *
  * {{{
  *   final class BufferedReaderResource(file: File)
  *     extends DIResource[IO, BufferedReader] {
  *       val acquire = IO { new BufferedReader(new FileReader(file)) }
  *       def release(in: BufferedReader) = IO { in.close() }
  *     }
  * }}}
  *
  * Using constructor-based inheritance from [[DIResource.Make]], [[DIResource.LiftF]], etc:
  *
  * {{{
  *   final class BufferedReaderResource(file: File)
  *     extends DIResource.Make[IO, BufferedReader](
  *       acquire = IO { new BufferedReader(new FileReader(file)) },
  *       release = in => IO { in.close() },
  *     }
  * }}}
  *
  * Or by converting an existing [[cats.effect.Resource]] or a [[zio.ZManaged]].
  * Use [[DIResource.fromCats]], [[DIResource.DIResourceCatsSyntax#toCats]] and
  * [[DIResource.fromZIO]], [[DIResource.DIResourceZIOSyntax#toZIO]] to convert back and forth.
  *
  * Usage is done via [[DIResource.DIResourceUse#use use]]:
  *
  * {{{
  *   open(file1).use { in1 =>
  *     open(file2).use { in2 =>
  *       readFiles(in1, in2)
  *     }
  *   }
  * }}}
  *
  * DIResources can be combined into a larger resource via [[DIResourceBase.flatMap]]:
  *
  * {{{
  *  val res: DIResource[IO, (BufferedReader, BufferedReader)] =
  *    open(file1).flatMap { in1 =>
  *     open(file2).flatMap { in2 =>
  *       IO.pure(in1 -> in2)
  *     }
  *   }
  * }}}
  *
  * Nested resources are released in reverse order of acquisition. Outer resources are
  * released even if an inner use or release fails.
  *
  * `DIResource` can be used in non-FP context with [[DIResource.Simple]]
  * it can also mimic Java's initialization-after-construction with [[DIResource.Mutable]]
  *
  * Use DIResource's to specify lifecycles of objects injected into the object graph.
  *
  *  {{{
  *   import distage.{DIResource, ModuleDef, Injector}
  *   import cats.effect.IO
  *
  *   class DBConnection
  *   class MessageQueueConnection
  *
  *   val dbResource = DIResource.make(IO { println("Connecting to DB!"); new DBConnection })(_ => IO(println("Disconnecting DB")))
  *   val mqResource = DIResource.make(IO { println("Connecting to Message Queue!"); new MessageQueueConnection })(_ => IO(println("Disconnecting Message Queue")))
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
  * The lifecycle of the entire object graph is itself expressed with `DIResource`,
  * you can control it by controlling the scope of `.use` or by manually invoking
  * [[DIResourceBase.acquire]] and [[DIResourceBase.release]].
  *
  * @see ModuleDef.fromResource: [[izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSLBase#fromResource]]
  *      [[cats.effect.Resource]]: https://typelevel.org/cats-effect/datatypes/resource.html
  *      [[zio.ZManaged]]: https://zio.dev/docs/datatypes/datatypes_managed
  */
trait DIResource[+F[_], Resource] extends DIResourceBase[F, Resource] {
  def acquire: F[Resource]
  def release(resource: Resource): F[Unit]

  override final def extract(resource: Resource): Resource = resource
  override final type InnerResource = Resource
}

object DIResource {
  import cats.effect.Resource

  implicit final class DIResourceUse[F[_], +A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    def use[B](use: A => F[B])(implicit F: DIEffect[F]): F[B] = {
      F.bracket(acquire = resource.acquire)(release = resource.release)(
        use = a => F.flatMap(F.maybeSuspend(resource.extract(a)))(use)
      )
    }
  }

  implicit final class DIResourceUseEffect[F[_], A](private val resource: DIResourceBase[F, F[A]]) extends AnyVal {
    def useEffect(implicit F: DIEffect[F]): F[A] =
      resource.use(identity)
  }

  implicit final class DIResourceLocatorRun[F[_]](private val resource: DIResourceBase[F, Locator]) extends AnyVal {
    def run[B](function: Functoid[F[B]])(implicit F: DIEffect[F]): F[B] =
      resource.use(_.run(function))
  }

  def make[F[_], A](acquire: => F[A])(release: A => F[Unit]): DIResource[F, A] = {
    @inline def a = acquire; @inline def r = release
    new DIResource[F, A] {
      override def acquire: F[A] = a
      override def release(resource: A): F[Unit] = r(resource)
    }
  }

  def make_[F[_], A](acquire: => F[A])(release: => F[Unit]): DIResource[F, A] = {
    make(acquire)(_ => release)
  }

  def makeSimple[A](acquire: => A)(release: A => Unit): DIResource[Identity, A] = {
    make[Identity, A](acquire)(release)
  }

  def makePair[F[_], A](allocate: F[(A, F[Unit])]): DIResource.FromCats[F, A] = {
    new DIResource.FromCats[F, A] {
      override def acquire: F[(A, F[Unit])] = allocate
    }
  }

  def liftF[F[_], A](acquire: => F[A])(implicit F: DIApplicative[F]): DIResource[F, A] = {
    make(acquire)(_ => F.unit)
  }

  def suspend[F[_]: DIEffect, A](acquire: => F[DIResourceBase[F, A]]): DIResourceBase[F, A] = {
    liftF(acquire).flatten
  }

  def fromAutoCloseable[F[_], A <: AutoCloseable](acquire: => F[A])(implicit F: DIEffect[F]): DIResource[F, A] = {
    make(acquire)(a => F.maybeSuspend(a.close()))
  }
  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire)(_.close)
  }

  def fromExecutorService[F[_], A <: ExecutorService](acquire: => F[A])(implicit F: DIEffect[F]): DIResource[F, A] = {
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
  def fromExecutorService[A <: ExecutorService](acquire: => A): DIResource[Identity, A] = {
    fromExecutorService[Identity, A](acquire)
  }

  def pure[F[_], A](a: A)(implicit F: DIApplicative[F]): DIResource[F, A] = {
    DIResource.liftF(F.pure(a))
  }

  def unit[F[_]](implicit F: DIApplicative[F]): DIResource[F, Unit] = {
    DIResource.liftF(F.unit)
  }

  /** Convert [[cats.effect.Resource]] to [[DIResource]] */
  def fromCats[F[_]: Bracket[?[_], Throwable], A](resource: Resource[F, A]): DIResource.FromCats[F, A] = {
    new FromCats[F, A] {
      override val acquire: F[(A, F[Unit])] = resource.allocated
    }
  }

  /** Convert [[zio.ZManaged]] to [[DIResource]] */
  def fromZIO[R, E, A](managed: ZManaged[R, E, A]): DIResource.FromZIO[R, E, A] = {
    new FromZIO[R, E, A] {
      override def acquire: ZIO[R, E, (A, ZIO[R, Nothing, Unit])] = {
        ZManaged
          .ReleaseMap.make.bracketExit(
            release = (releaseMap: ReleaseMap, exit: Exit[E, _]) =>
              exit match {
                case Exit.Success(_) => UIO.unit
                case Exit.Failure(_) => releaseMap.releaseAll(exit, ExecutionStrategy.Sequential)
              }
          ) {
            releaseMap =>
              managed
                .zio
                .provideSome[R](_ -> releaseMap)
                .map { case (_, a) => (a, releaseMap.releaseAll(Exit.succeed(a), ExecutionStrategy.Sequential).unit) }
          }
      }
    }
  }

  implicit final class DIResourceCatsSyntax[+F[_], +A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    /** Convert [[DIResource]] to [[cats.effect.Resource]] */
    def toCats[G[x] >: F[x]: Applicative]: Resource[G, A] = {
      Resource.make[G, resource.InnerResource](resource.acquire)(resource.release).map(resource.extract)
    }

    def mapK[G[x] >: F[x], H[_]](f: G ~> H): DIResourceBase[H, A] = {
      new DIResourceBase[H, A] {
        override type InnerResource = resource.InnerResource
        override def acquire: H[InnerResource] = f(resource.acquire)
        override def release(res: InnerResource): H[Unit] = f(resource.release(res))
        override def extract(res: InnerResource): A = resource.extract(res)
      }
    }
  }

  implicit final class DIResourceZIOSyntax[-R, +E, +A](private val resource: DIResourceBase[ZIO[R, E, ?], A]) extends AnyVal {
    /** Convert [[DIResource]] to [[zio.ZManaged]] */
    def toZIO: ZManaged[R, E, A] = {
      ZManaged.makeReserve(
        resource
          .acquire.map(
            r =>
              Reservation(
                ZIO.effectTotal(resource.extract(r)),
                _ =>
                  resource.release(r).orDieWith {
                    case e: Throwable => e
                    case any: Any => new RuntimeException(s"DIResource finalizer: $any")
                  },
              )
          )
      )
    }
  }

  implicit final class DIResourceSimpleSyntax[+A](private val resource: DIResourceBase[Identity, A]) extends AnyVal {
    def toEffect[F[_]: DIEffect]: DIResourceBase[F, A] = {
      new DIResourceBase[F, A] {
        override type InnerResource = resource.InnerResource
        override def acquire: F[InnerResource] = DIEffect[F].maybeSuspend(resource.acquire)
        override def release(res: InnerResource): F[Unit] = DIEffect[F].maybeSuspend(resource.release(res))
        override def extract(res: InnerResource): A = resource.extract(res)
      }
    }
  }

  implicit final class DIResourceFlatten[F[_], +A](private val resource: DIResourceBase[F, DIResourceBase[F, A]]) extends AnyVal {
    def flatten(implicit F: DIEffect[F]): DIResourceBase[F, A] = resource.flatMap(identity)
  }

  implicit final class DIResourceUnsafeGet[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    /** Unsafely acquire the resource and throw away the finalizer,
      * this will leak the resource and cause it to never be cleaned up.
      *
      * This function only makes sense in code examples or at top-level,
      * please use [[DIResourceUse#use]] instead!
      */
    def unsafeGet()(implicit F: DIApplicative[F]): F[A] = F.map(resource.acquire)(resource.extract)
  }

  /**
    * Class-based proxy over a [[DIResource]] value
    *
    * {{{
    *   class IntRes extends DIResource.Of(DIResource.pure(1000))
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
    * Note: when the expression passed to [[DIResource.Of]] defines many local methods
    *       it can hit a Scalac bug https://github.com/scala/bug/issues/11969
    *       and fail to compile, in that case you may switch to [[DIResource.OfInner]]
    */
  class Of[+F[_], +A] private[this] (inner0: () => DIResourceBase[F, A], @unused dummy: Boolean = false) extends DIResource.OfInner[F, A] {
    def this(inner: => DIResourceBase[F, A]) = this(() => inner)

    override val inner: DIResourceBase[F, A] = inner0()
  }

  /**
    * Class-based proxy over a [[cats.effect.Resource]] value
    *
    * {{{
    *   class IntRes extends DIResource.OfCats(Resource.pure(1000))
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
  class OfCats[F[_]: Bracket[?[_], Throwable], A](inner: => Resource[F, A]) extends DIResource.Of[F, A](fromCats(inner))

  /**
    * Class-based proxy over a [[zio.ZManaged]] value
    *
    * {{{
    *   class IntRes extends DIResource.OfZIO(Managed.succeed(1000))
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
  class OfZIO[-R, +E, +A](inner: => ZManaged[R, E, A]) extends DIResource.Of[ZIO[R, E, ?], A](fromZIO(inner))

  /**
    * Class-based variant of [[make]]:
    *
    * {{{
    *   class IntRes extends DIResource.Make(
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
  class Make[+F[_], A] private[this] (acquire0: () => F[A])(release0: A => F[Unit], @unused dummy: Boolean = false) extends DIResource[F, A] {
    def this(acquire: => F[A])(release: A => F[Unit]) = this(() => acquire)(release)

    override final def acquire: F[A] = acquire0()
    override final def release(resource: A): F[Unit] = release0(resource)
  }

  /**
    * Class-based variant of [[make_]]:
    *
    * {{{
    *   class IntRes extends DIResource.Make_(IO(1000))(IO.unit)
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
  class Make_[+F[_], A](acquire: => F[A])(release: => F[Unit]) extends Make[F, A](acquire)(_ => release)

  /**
    * Class-based variant of [[makePair]]:
    *
    * {{{
    *   class IntRes extends DIResource.MakePair(IO(1000 -> IO.unit))
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
  class MakePair[F[_], A] private[this] (acquire0: () => F[(A, F[Unit])], @unused dummy: Boolean = false) extends FromCats[F, A] {
    def this(acquire: => F[(A, F[Unit])]) = this(() => acquire)

    override final def acquire: F[(A, F[Unit])] = acquire0()
  }

  /**
    * Class-based variant of [[liftF]]:
    *
    * {{{
    *   class IntRes extends DIResource.LiftF(acquire = IO(1000))
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
  class LiftF[+F[_]: DIApplicative, A] private[this] (acquire0: () => F[A], @unused dummy: Boolean = false) extends NoClose[F, A] {
    def this(acquire: => F[A]) = this(() => acquire)

    override final def acquire: F[A] = acquire0()
  }

  /**
    * Class-based variant of [[fromAutoCloseable]]:
    *
    * {{{
    *   class FileOutputRes extends DIResource.FromAutoCloseable(
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
  class FromAutoCloseable[+F[_]: DIEffect, +A <: AutoCloseable](acquire: => F[A]) extends DIResource.Of(DIResource.fromAutoCloseable(acquire))

  /**
    * Trait-based proxy over a [[DIResource]] value
    *
    * {{{
    *   class IntRes extends DIResource.OfInner {
    *
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
    * NOTE: This class may be used instead of [[DIResource.Of]] to
    * workaround scalac bug https://github.com/scala/bug/issues/11969
    * when defining local methods
    */
  trait OfInner[+F[_], +A] extends DIResourceBase[F, A] {
    val inner: DIResourceBase[F, A]

    override final type InnerResource = inner.InnerResource
    override final def acquire: F[inner.InnerResource] = inner.acquire
    override final def release(resource: inner.InnerResource): F[Unit] = inner.release(resource)
    override final def extract(resource: inner.InnerResource): A = inner.extract(resource)
  }

  trait Simple[A] extends DIResource[Identity, A]

  trait Mutable[+A] extends DIResource.Self[Identity, A] { this: A => }

  trait Self[+F[_], +A] extends DIResourceBase[F, A] { this: A =>
    def release: F[Unit]

    override final type InnerResource = Unit
    override final def release(resource: Unit): F[Unit] = release
    override final def extract(resource: Unit): A = this
  }

  trait MutableOf[+A] extends DIResource.SelfOf[Identity, A] { this: A => }

  trait SelfOf[+F[_], +A] extends DIResourceBase[F, A] { this: A =>
    val inner: DIResourceBase[F, Unit]

    override final type InnerResource = inner.InnerResource
    override final def acquire: F[inner.InnerResource] = inner.acquire
    override final def release(resource: inner.InnerResource): F[Unit] = inner.release(resource)
    override final def extract(resource: inner.InnerResource): A = this
  }

  trait MutableNoClose[+A] extends DIResource.SelfNoClose[Identity, A] { this: A => }

  abstract class SelfNoClose[+F[_]: DIApplicative, +A] extends DIResourceBase.NoClose[F, A] { this: A =>
    override type InnerResource = Unit
    override final def extract(resource: Unit): A = this
  }

  abstract class NoClose[+F[_]: DIApplicative, A] extends DIResourceBase.NoClose[F, A] with DIResource[F, A]

  trait FromCats[F[_], A] extends DIResourceBase[F, A] {
    override final type InnerResource = (A, F[Unit])
    override final def release(resource: (A, F[Unit])): F[Unit] = resource._2
    override final def extract(resource: (A, F[Unit])): A = resource._1
  }

  trait FromZIO[R, E, A] extends DIResourceBase[ZIO[R, E, ?], A] {
    override final type InnerResource = (A, ZIO[R, Nothing, Unit])
    override final def release(resource: (A, ZIO[R, Nothing, Unit])): ZIO[R, Nothing, Unit] = resource._2
    override final def extract(resource: (A, ZIO[R, Nothing, Unit])): A = resource._1
  }

  /** Generalized [[DIResource]] */
  trait DIResourceBase[+F[_], +OuterResource] {
    type InnerResource
    def acquire: F[InnerResource]
    def release(resource: InnerResource): F[Unit]
    def extract(resource: InnerResource): OuterResource

    final def map[B](f: OuterResource => B): DIResourceBase[F, B] = mapImpl(this)(f)
    final def flatMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => DIResourceBase[G, B]): DIResourceBase[G, B] = flatMapImpl[G, OuterResource, B](this)(f)
    final def evalMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => G[B]): DIResourceBase[G, B] = evalMapImpl[G, OuterResource, B](this)(f)
    final def evalTap[G[x] >: F[x]: DIEffect](f: OuterResource => G[Unit]): DIResourceBase[G, OuterResource] =
      evalMap[G, OuterResource](a => DIEffect[G].map(f(a))(_ => a))

    /** Wrap acquire action of this resource in another effect, e.g. for logging purposes */
    final def wrapAcquire[G[x] >: F[x]](f: (=> G[InnerResource]) => G[InnerResource]): DIResourceBase[G, OuterResource] =
      wrapAcquireImpl[G, OuterResource](this: this.type)(f)

    /** Wrap release action of this resource in another effect, e.g. for logging purposes */
    final def wrapRelease[G[x] >: F[x]](f: (InnerResource => G[Unit], InnerResource) => G[Unit]): DIResourceBase[G, OuterResource] =
      wrapReleaseImpl[G, OuterResource](this: this.type)(f)

    final def beforeAcquire[G[x] >: F[x]: DIApplicative](f: => G[Unit]): DIResourceBase[G, OuterResource] =
      wrapAcquire[G](acquire => DIApplicative[G].map2(f, acquire)((_, res) => res))
    final def beforeRelease[G[x] >: F[x]: DIApplicative](f: InnerResource => G[Unit]): DIResourceBase[G, OuterResource] =
      wrapRelease[G]((release, res) => DIApplicative[G].map2(f(res), release(res))((_, _) => ()))

    final def void: DIResourceBase[F, Unit] = map(_ => ())
  }

  object DIResourceBase {
    abstract class NoClose[+F[_]: DIApplicative, +A] extends DIResourceBase[F, A] {
      override final def release(resource: InnerResource): F[Unit] = DIApplicative[F].unit
    }
  }

  /**
    * Allows you to bind [[cats.effect.Resource]]-based constructors in `ModuleDef`:
    *
    * Example:
    * {{{
    *   import cats.effect._
    *
    *   val catsResource = Resource.liftF(IO(5))
    *
    *   val module = new distage.ModuleDef {
    *
    *     make[Int].fromResource(catsResource)
    *
    *     addImplicit[Bracket[IO, Throwable]]
    *   }
    * }}}
    *
    * NOTE: binding a cats Resource[F, A] will add a
    *       dependency on `Bracket[F, Throwable]` for
    *       your corresponding `F` type
    */
  implicit final def providerFromCats[F[_]: TagK, A](
    resource: => Resource[F, A]
  )(implicit tag: Tag[DIResource.FromCats[F, A]]
  ): Functoid[DIResource.FromCats[F, A]] = {
    Functoid.identity[Bracket[F, Throwable]].map {
      implicit bracket: Bracket[F, Throwable] =>
        fromCats(resource)
    }
  }

  /**
    * Allows you to bind [[zio.ZManaged]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZIO[R, E, A](
    managed: => ZManaged[R, E, A]
  )(implicit tag: Tag[DIResource.FromZIO[R, E, A]]
  ): Functoid[DIResource.FromZIO[R, E, A]] = {
    Functoid.lift(fromZIO(managed))
  }

  /**
    * Allows you to bind [[zio.ZManaged]]-based constructors in `ModuleDef`:
    */
  // workaround for inference issues with `E=Nothing`, scalac error: Couldn't find Tag[FromZIO[Any, E, Clock]] when binding ZManaged[Any, Nothing, Clock]
  implicit final def providerFromZIONothing[R, A](
    managed: => ZManaged[R, Nothing, A]
  )(implicit tag: Tag[DIResource.FromZIO[R, Nothing, A]]
  ): Functoid[DIResource.FromZIO[R, Nothing, A]] = {
    Functoid.lift(fromZIO(managed))
  }

  /**
    * Allows you to bind [[zio.ZLayer]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZLayerHas1[R, E, A: Tag](
    layer: => ZLayer[R, E, Has[A]]
  )(implicit tag: Tag[DIResource.FromZIO[R, E, A]]
  ): Functoid[DIResource.FromZIO[R, E, A]] = {
    Functoid.lift(fromZIO(layer.build.map(_.get)))
  }

  /**
    * Allows you to bind [[zio.ZLayer]]-based constructors in `ModuleDef`:
    */
  // workaround for inference issues with `E=Nothing`, scalac error: Couldn't find Tag[FromZIO[Any, E, Clock]] when binding ZManaged[Any, Nothing, Clock]
  implicit final def providerFromZLayerNothingHas1[R, A: Tag](
    layer: => ZLayer[R, Nothing, Has[A]]
  )(implicit tag: Tag[DIResource.FromZIO[R, Nothing, A]]
  ): Functoid[DIResource.FromZIO[R, Nothing, A]] = {
    Functoid.lift(fromZIO(layer.build.map(_.get)))
  }

  /** Support binding various FP libraries' Resource types in `.fromResource` */
  trait AdaptProvider[A] {
    type Out
    def apply(a: Functoid[A])(implicit tag: ResourceTag[Out]): Functoid[Out]
  }
  object AdaptProvider {
    type Aux[A, B] = AdaptProvider[A] { type Out = B }

    /**
      * Allows you to bind [[cats.effect.Resource]]-based constructor functions in `ModuleDef`:
      *
      * Example:
      * {{{
      *   import cats.effect._
      *   import doobie.hikari._
      *
      *   final case class JdbcConfig(driverClassName: String, url: String, user: String, pass: String)
      *
      *   val module = new distage.ModuleDef {
      *
      *     make[ExecutionContext].from(scala.concurrent.ExecutionContext.global)
      *
      *     make[JdbcConfig].from {
      *       conf: JdbcConfig @ConfPath("jdbc") => conf
      *     }
      *
      *     make[HikariTransactor[IO]].fromResource {
      *       (ec: ExecutionContext, jdbc: JdbcConfig) =>
      *         implicit val C: ContextShift[IO] = IO.contextShift(ec)
      *
      *         HikariTransactor.newHikariTransactor[IO](jdbc.driverClassName, jdbc.url, jdbc.user, jdbc.pass, ec, ec)
      *     }
      *
      *     addImplicit[Bracket[IO, Throwable]]
      *   }
      * }}}
      *
      * NOTE: binding a cats Resource[F, A] will add a
      * dependency on `Bracket[F, Throwable]` for
      * your corresponding `F` type
      */
    implicit final def providerFromCatsProvider[F[_], A]: AdaptProvider.Aux[Resource[F, A], DIResource.FromCats[F, A]] = {
      new AdaptProvider[Resource[F, A]] {
        type Out = DIResource.FromCats[F, A]

        override def apply(a: Functoid[Resource[F, A]])(implicit tag: ResourceTag[DIResource.FromCats[F, A]]): Functoid[DIResource.FromCats[F, A]] = {
          import tag.tagFull
          implicit val tagF: TagK[F] = tag.tagK.asInstanceOf[TagK[F]]; val _ = tagF

          a.zip(Functoid.identity[Bracket[F, Throwable]])
            .map { case (resource, bracket) => fromCats(resource)(bracket) }
        }
      }
    }

    /**
      * Allows you to bind [[zio.ZManaged]]-based constructor functions in `ModuleDef`:
      */
    implicit final def providerFromZIOProvider[R, E, A]: AdaptProvider.Aux[ZManaged[R, E, A], FromZIO[R, E, A]] = {
      new AdaptProvider[ZManaged[R, E, A]] {
        type Out = DIResource.FromZIO[R, E, A]

        override def apply(a: Functoid[ZManaged[R, E, A]])(implicit tag: ResourceTag[DIResource.FromZIO[R, E, A]]): Functoid[FromZIO[R, E, A]] = {
          import tag.tagFull
          a.map(fromZIO)
        }
      }
    }

    /**
      * Allows you to bind [[zio.ZManaged]]-based constructor functions in `ModuleDef`:
      */
    implicit final def providerFromZLayerProvider[R, E, A: Tag]: AdaptProvider.Aux[ZLayer[R, E, Has[A]], FromZIO[R, E, A]] = {
      new AdaptProvider[ZLayer[R, E, Has[A]]] {
        type Out = DIResource.FromZIO[R, E, A]

        override def apply(a: Functoid[ZLayer[R, E, Has[A]]])(implicit tag: ResourceTag[DIResource.FromZIO[R, E, A]]): Functoid[FromZIO[R, E, A]] = {
          import tag.tagFull
          a.map(layer => fromZIO(layer.map(_.get).build))
        }
      }
    }

  }

  @inline
  private[this] final def mapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => B): DIResourceBase[F, B] = {
    new DIResourceBase[F, B] {
      type InnerResource = self.InnerResource
      def acquire: F[InnerResource] = self.acquire
      def release(resource: InnerResource): F[Unit] = self.release(resource)
      def extract(resource: InnerResource): B = f(self.extract(resource))
    }
  }

  @inline
  private[this] final def flatMapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => DIResourceBase[F, B])(implicit F: DIEffect[F]): DIResourceBase[F, B] = {

    def bracketOnError[a, b](acquire: => F[a])(releaseOnError: a => F[Unit])(use: a => F[b]): F[b] = {
      F.bracketCase(acquire = acquire)(release = {
        case (a, Some(_)) => releaseOnError(a)
        case _ => F.unit
      })(use = use)
    }
    import DIEffect.syntax._

    new DIResourceBase[F, B] {
      override type InnerResource = InnerResource0
      sealed trait InnerResource0 {
        def extract: B
        def deallocate: F[Unit]
      }
      override def acquire: F[InnerResource] = {
        bracketOnError(self.acquire)(self.release) {
          inner1 =>
            val res2 = f(self.extract(inner1))
            bracketOnError(res2.acquire)(res2.release) {
              inner2 =>
                F.pure(new InnerResource0 {
                  def extract: B = res2.extract(inner2)
                  def deallocate: F[Unit] = {
                    F.unit
                      .guarantee(res2.release(inner2))
                      .guarantee(self.release(inner1))
                  }
                })
            }
        }
      }
      override def release(resource: InnerResource): F[Unit] = resource.deallocate
      override def extract(resource: InnerResource): B = resource.extract
    }
  }

  @inline
  private[this] final def evalMapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => F[B])(implicit F: DIEffect[F]): DIResourceBase[F, B] = {
    flatMapImpl(self)(a => DIResource.make(f(a))(_ => F.unit))
  }

  @inline
  private[this] final def wrapAcquireImpl[F[_], A](self: DIResourceBase[F, A])(f: (=> F[self.InnerResource]) => F[self.InnerResource]): DIResourceBase[F, A] = {
    new DIResourceBase[F, A] {
      override final type InnerResource = self.InnerResource
      override def acquire: F[InnerResource] = f(self.acquire)
      override def release(resource: InnerResource): F[Unit] = self.release(resource)
      override def extract(resource: InnerResource): A = self.extract(resource)
    }
  }

  @inline
  private[this] final def wrapReleaseImpl[F[_], A](
    self: DIResourceBase[F, A]
  )(f: (self.InnerResource => F[Unit], self.InnerResource) => F[Unit]
  ): DIResourceBase[F, A] = {
    new DIResourceBase[F, A] {
      override final type InnerResource = self.InnerResource
      override def acquire: F[InnerResource] = self.acquire
      override def release(resource: InnerResource): F[Unit] = f(self.release, resource)
      override def extract(resource: InnerResource): A = self.extract(resource)
    }
  }

  trait ResourceTag[R] {
    type F[_]
    type A
    implicit def tagFull: Tag[R]
    implicit def tagK: TagK[F]
    implicit def tagA: Tag[A]
  }
  object ResourceTag extends ResourceTagLowPriority {
    @inline def apply[A: ResourceTag]: ResourceTag[A] = implicitly

    implicit def resourceTag[R <: DIResourceBase[F0, A0]: Tag, F0[_]: TagK, A0: Tag]: ResourceTag[R with DIResourceBase[F0, A0]] { type F[X] = F0[X]; type A = A0 } = {
      new ResourceTag[R] {
        type F[X] = F0[X]
        type A = A0
        val tagK: TagK[F0] = TagK[F0]
        val tagA: Tag[A0] = Tag[A0]
        val tagFull: Tag[R] = Tag[R]
      }
    }
  }
  sealed trait ResourceTagLowPriority {
    /**
      * The `resourceTag` implicit above works perfectly fine, this macro here is exclusively
      * a workaround for highlighting in Intellij IDEA
      *
      * (it's also used to display error trace from TagK's @implicitNotFound)
      *
      * TODO: report to IJ bug tracker
      */
    implicit final def fakeResourceTagMacroIntellijWorkaround[R <: DIResourceBase[Any, Any]]: ResourceTag[R] =
      macro ResourceTagMacro.fakeResourceTagMacroIntellijWorkaroundImpl[R]
  }

  trait TrifunctorHasResourceTag[R0, T] {
    type F[-RR, +EE, +AA]
    type R
    type E
    type A <: T
    implicit def tagBIOLocal: Tag[BIOLocal[F]]
    implicit def tagFull: Tag[DIResourceBase[F[Any, E, ?], A]]
    implicit def ctorR: HasConstructor[R]
    implicit def ev: R0 <:< DIResourceBase[F[R, E, ?], A]
    implicit def resourceTag: ResourceTag[DIResourceBase[F[Any, E, ?], A]]
  }
  import scala.annotation.unchecked.{uncheckedVariance => v}
  object TrifunctorHasResourceTag extends TrifunctorHasResourceTagLowPriority {

    implicit def trifunctorResourceTag[
      R1 <: DIResourceBase[F0[R0, E0, ?], A0],
      F0[_, _, _]: TagK3,
      R0: HasConstructor,
      E0: Tag,
      A0 <: A1: Tag,
      A1,
    ]: TrifunctorHasResourceTag[R1 with DIResourceBase[F0[R0, E0, ?], A0], A1] {
      type R = R0
      type E = E0
      type A = A0
      type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v]
    } = new TrifunctorHasResourceTag[R1, A1] { self =>
      type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v]
      type R = R0
      type E = E0
      type A = A0
      val tagBIOLocal: Tag[BIOLocal[F]] = implicitly
      val ctorR: HasConstructor[R0] = implicitly
      val tagFull: Tag[DIResourceBase[F0[Any, E0, ?], A0]] = implicitly
      val ev: R1 <:< DIResourceBase[F0[R0, E0, ?], A0] = implicitly
      val resourceTag: ResourceTag[DIResourceBase[F0[Any, E0, ?], A0]] = new ResourceTag[DIResourceBase[F0[Any, E0, ?], A0]] {
        type F[AA] = F0[Any, E0, AA]
        type A = A0
        val tagFull: Tag[DIResourceBase[F0[Any, E0, ?], A0]] = self.tagFull
        val tagK: TagK[F0[Any, E0, ?]] = TagK[F0[Any, E0, ?]]
        val tagA: Tag[A0] = implicitly
      }
    }
  }
  sealed trait TrifunctorHasResourceTagLowPriority extends TrifunctorHasResourceTagLowPriority1 {
    implicit def trifunctorResourceTagNothing[
      R1 <: DIResourceBase[F0[R0, Nothing, ?], A0],
      F0[_, _, _]: TagK3,
      R0: HasConstructor,
      A0 <: A1: Tag,
      A1,
    ]: TrifunctorHasResourceTag[R1 with DIResourceBase[F0[R0, Nothing, ?], A0], A1] {
      type R = R0
      type E = Nothing
      type A = A0
      type F[-RR, +EE, +AA] = F0[RR @v, EE @v, AA @v] @v
    } = TrifunctorHasResourceTag.trifunctorResourceTag[R1, F0, R0, Nothing, A0, A1]
  }
  sealed trait TrifunctorHasResourceTagLowPriority1 {
    implicit final def fakeResourceTagMacroIntellijWorkaround[R <: DIResourceBase[Any, Any], T]: TrifunctorHasResourceTag[R, T] =
      macro ResourceTagMacro.fakeResourceTagMacroIntellijWorkaroundImpl[R]
  }

  object ResourceTagMacro {
    def fakeResourceTagMacroIntellijWorkaroundImpl[R <: DIResourceBase[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[Nothing] = {
      val tagMacro = new TagMacro(c)
      tagMacro.makeTagImpl[R] // run the macro AGAIN, to get a fresh error message
      val tagTrace = tagMacro.getImplicitError()

      c.abort(c.enclosingPosition, s"could not find implicit ResourceTag for ${c.universe.weakTypeOf[R]}!\n$tagTrace")
    }
  }

  @deprecated("renamed to fromAutoCloseable", "0.11")
  def fromAutoCloseableF[F[_], A <: AutoCloseable](acquire: => F[A])(implicit F: DIEffect[F]): DIResource[F, A] = fromAutoCloseable(acquire)
}
