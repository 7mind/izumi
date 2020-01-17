package izumi.distage.model.definition

import java.util.concurrent.{ExecutorService, TimeUnit}

import cats.effect.Bracket
import cats.{Applicative, ~>}
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.providers.ProviderMagnet
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.reflection.TagMacro
import izumi.fundamentals.reflection.Tags.{Tag, TagK}
import zio.{Exit, Reservation, ZIO, ZManaged}

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

/**
  * `DIResource` is a class that captures the effectful
  * allocation of a resource, along with its finalizer.
  *
  * This can be used to wrap expensive resources.
  *
  * Example with [[DIResource.make]]:
  *
  * {{{
  *   def open(file: File): DIResource[IO, BufferedReader] =
  *     DIResource.make(IO { new BufferedReader(new FileReader(file)) })(in => IO { in.close() })
  * }}}
  *
  * Example with inheritance:
  *
  * {{{
  *   final class BufferedReaderResource(file: File) extends DIResource[IO, BufferedReader] {
  *     val acquire = IO { new BufferedReader(new FileReader(file)) }
  *     def release(in: BufferedReader) = IO { in.close() }
  *   }
  * }}}
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
  * DIResource is compatible with [[cats.effect.Resource]]. Use [[DIResource.fromCats]]
  * and [[DIResource.DIResourceCatsSyntax.toCats]] to convert back and forth.
  *
  * Use DIResource's to specify lifecycles of objects injected into the object graph.
  *
  * Example:
  * {{{
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
  *   Injector().produceF[IO](module).use {
  *     objects =>
  *       objects.get[MyApp].run
  *   }.unsafeRunSync()
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
  * you can control it by controlling the scope in `.use` or by manually using
  * [[DIResourceBase.acquire]] and [[DIResourceBase.release]].
  *
  * @see ModuleDef.fromResource: [[izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSL.fromResource]]
  *      [[cats.effect.Resource]]: https://typelevel.org/cats-effect/datatypes/resource.html
  **/
trait DIResource[+F[_], Resource] extends DIResourceBase[F, Resource] {
  def acquire: F[Resource]
  def release(resource: Resource): F[Unit]

  override final def extract(resource: Resource): Resource = resource
  override final type InnerResource = Resource
}

object DIResource {
  import cats.effect.Resource

  implicit final class DIResourceUse[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    def use[B](use: A => F[B])(implicit F: DIEffect[F]): F[B] = {
      F.bracket(acquire = resource.acquire)(release = resource.release)(
        use = a => F.flatMap(F.maybeSuspend(resource.extract(a)))(use)
      )
    }
  }

  implicit final class DIResourceUseEffect[F[_], A](
    private val resource: DIResourceBase[F, F[A]],
  ) extends AnyVal {
    def useEffect(implicit F: DIEffect[F]): F[A] = {
      resource.use(identity)
    }
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

  def liftF[F[_], A](acquire: => F[A])(implicit F: DIEffect[F]): DIResource[F, A] = {
    make(acquire)(_ => F.unit)
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire)(_.close)
  }

  def fromAutoCloseableF[F[_], A <: AutoCloseable](acquire: => F[A])(implicit F: DIEffect[F]): DIResource[F, A] = {
    make(acquire)(a => F.maybeSuspend(a.close()))
  }

  def fromExecutorService[A <: ExecutorService](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire) { es =>
      if (!(es.isShutdown || es.isTerminated)) {
        es.shutdown()
        if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
          es.shutdownNow().discard()
        }
      }
    }
  }

  def pure[A](a: A): DIResource[Identity, A] = {
    DIResource.makeSimple(a)(_ => ())
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
        managed.reserve.uninterruptible.flatMap {
          reservation => reservation.acquire.map(a => a -> reservation.release(Exit.succeed(a)).unit)
        }
      }
    }
  }

  implicit final class DIResourceCatsSyntax[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
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
      ZManaged(resource.acquire.map(r => Reservation(
        zio.ZIO.effectTotal(resource.extract(r)),
        _ => resource.release(r).orDieWith {
          case e: Throwable => e
          case any: Any => new RuntimeException(s"DIResource finalizer: $any")
        },
      )))
    }
  }

  implicit final class DIResourceSimpleSyntax[A](private val resource: DIResourceBase[Identity, A]) extends AnyVal {
    def toEffect[G[_]: DIEffect]: DIResourceBase[G, A] = {
      new DIResourceBase[G, A] {
        override type InnerResource = resource.InnerResource
        override def acquire: G[InnerResource] = DIEffect[G].maybeSuspend(resource.acquire)
        override def release(res: InnerResource): G[Unit] = DIEffect[G].maybeSuspend(resource.release(res))
        override def extract(res: InnerResource): A = resource.extract(res)
      }
    }
  }

  trait Simple[A] extends DIResource[Identity, A]

  trait Mutable[+A] extends DIResource.Self[Identity, A] { this: A => }

  trait MutableNoClose[+A] extends DIResource.SelfNoClose[Identity, A] { this: A => }

  abstract class NoClose[+F[_]: DIEffect, A] extends DIResourceBase.NoClose[F, A] with DIResource[F, A]

  /**
    * Class-based variant of [[make]]:
    *
    * {{{
    *   class IntRes extends DIResource.Make(
    *     acquire = IO(1000)
    *   )(release = _ => IO.unit)
    * }}}
    */
  class Make[+F[_], A] private[this](acquire0: () => F[A])(release: A => F[Unit], @unused dummy: Boolean = false) extends DIResource[F, A] {
    def this(acquire: => F[A])(release: A => F[Unit]) = this(() => acquire)(release)

    override final def acquire: F[A] = acquire0()
    override final def release(resource: A): F[Unit] = release.apply(resource)
  }

  /**
    * Class-based variant of [[make_]]:
    *
    * {{{
    *   class IntRes extends DIResource.Make_(IO(1000))(IO.unit)
    * }}}
    */
  class Make_[+F[_], A](acquire: => F[A])(release: => F[Unit]) extends Make[F, A](acquire)(_ => release)

  /**
    * Class-based variant of [[makePair]]:
    *
    * {{{
    *   class IntRes extends DIResource.MakePair(IO(1000 -> IO.unit))
    * }}}
    */
  class MakePair[F[_], A] private[this](acquire0: () => F[(A, F[Unit])], @unused dummy: Boolean = false) extends FromCats[F, A] {
    def this(acquire: => F[(A, F[Unit])]) = this(() => acquire)

    override final def acquire: F[(A, F[Unit])] = acquire0()
  }

  /**
    * Class-based variant of [[liftF]]:
    *
    * {{{
    *   class IntRes extends DIResource.LiftF(acquire = IO(1000))
    * }}}
    */
  class LiftF[+F[_]: DIEffect, A] private[this](acquire0: () => F[A], @unused dummy: Boolean = false) extends NoClose[F, A] {
    def this(acquire: => F[A]) = this(() => acquire)

    override final def acquire: F[A] = acquire0()
  }

  /**
    * Class-based variant of [[fromAutoCloseableF]]:
    *
    * {{{
    *   class FileOutputRes extends DIResource.FromAutoCloseable(
    *     acquire = IO(new FileOutputStream("abc"))
    *   )
    * }}}
    */
  class FromAutoCloseable[+F[_]: DIEffect, +A <: AutoCloseable](acquire: => F[A]) extends DIResource.Of(DIResource.fromAutoCloseableF(acquire))

  /**
    * Class-based proxy over a [[DIResource]] value
    *
    * {{{
    *   class IntRes extends DIResource.Of(DIResource.pure(1000))
    * }}}
    */
  class Of[+F[_], +A](private[Of] val inner: DIResourceBase[F, A]) extends DIResourceBase[F, A] {
    override final type InnerResource = inner.InnerResource
    override final def acquire: F[inner.InnerResource] = inner.acquire
    override final def release(resource: inner.InnerResource): F[Unit] = inner.release(resource)
    override final def extract(resource: inner.InnerResource): A = inner.extract(resource)
  }

  /**
    * Class-based proxy over a [[cats.effect.Resource]] value
    *
    * {{{
    *   class IntRes extends DIResource.OfCats(Resource.pure(1000))
    * }}}
    */
  class OfCats[F[_]: Bracket[?[_], Throwable], A](inner: Resource[F, A]) extends DIResource.Of[F, A](fromCats(inner))

  /**
    * Class-based proxy over a [[zio.ZManaged]] value
    *
    * {{{
    *   class IntRes extends DIResource.OfZIO(Managed.succeed(1000))
    * }}}
    */
  class OfZIO[-R, +E, +A](inner: ZManaged[R, E, A]) extends DIResource.Of[ZIO[R, E, ?], A](fromZIO(inner))

  abstract class SelfNoClose[+F[_]: DIEffect, +A] extends DIResourceBase.NoClose[F, A] { this: A =>
    override type InnerResource = Unit
    override final def extract(resource: Unit): A = this
  }

  trait Self[+F[_], +A] extends DIResourceBase[F, A] { this: A =>
    def release: F[Unit]
    override final type InnerResource = Unit
    override final def release(resource: Unit): F[Unit] = release
    override final def extract(resource: Unit): A = this
  }

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
  trait DIResourceBase[+F[_], +OuterResource] { self =>
    type InnerResource
    def acquire: F[InnerResource]
    def release(resource: InnerResource): F[Unit]
    def extract(resource: InnerResource): OuterResource

    final def map[B](f: OuterResource => B): DIResourceBase[F, B] = mapImpl(this)(f)
    final def flatMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => DIResourceBase[G, B]): DIResourceBase[G, B] = flatMapImpl[G, OuterResource, B](this)(f)
    final def evalMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => G[B]): DIResourceBase[G, B] = evalMapImpl[G, OuterResource, B](this)(f)

    /** Wrap acquire action of this resource in another effect, e.g. for logging purposes */
    final def logAcquire[G[x] >: F[x]](f: (=> G[InnerResource]) => G[InnerResource]): DIResourceBase[G, OuterResource] = logAcquireImpl[G, OuterResource](this: this.type)(f)

    /** Wrap release action of this resource in another effect, e.g. for logging purposes */
    final def logRelease[G[x] >: F[x]](f: (InnerResource => G[Unit], InnerResource) => G[Unit]): DIResourceBase[G, OuterResource] = logReleaseImpl[G, OuterResource](this: this.type)(f)

    final def void: DIResourceBase[F, Unit] = map(_ => ())
  }

  object DIResourceBase {
    abstract class NoClose[+F[_]: DIEffect, +A] extends DIResourceBase[F, A] {
      override final def release(resource: InnerResource): F[Unit] = DIEffect[F].unit
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
  implicit final def providerFromCats[F[_]: TagK, A](resource: => Resource[F, A])(implicit tag: Tag[DIResource.FromCats[F, A]]): ProviderMagnet[DIResource.FromCats[F, A]] = {
    ProviderMagnet.identity[Bracket[F, Throwable]].map {
      implicit bracket: Bracket[F, Throwable] =>
        fromCats(resource)
    }
  }
  /**
    * Allows you to bind [[zio.ZManaged]]-based constructors in `ModuleDef`:
    */
  implicit final def providerFromZIO[R, E, A](managed: => ZManaged[R, E, A])(implicit tag: Tag[DIResource.FromZIO[R, E, A]]): ProviderMagnet[DIResource.FromZIO[R, E, A]] = {
    ProviderMagnet.lift(fromZIO(managed))
  }

  /** Support binding various FP libraries' Resource types in `.fromResource` */
  trait AdaptProvider[A] {
    type Out
    def apply(a: ProviderMagnet[A])(implicit tag: ResourceTag[Out]): ProviderMagnet[Out]
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

        override def apply(a: ProviderMagnet[Resource[F, A]])(implicit tag: ResourceTag[DIResource.FromCats[F, A]]): ProviderMagnet[DIResource.FromCats[F, A]] = {
          import tag.tagFull
          implicit val tagF: TagK[F] = tag.tagK.asInstanceOf[TagK[F]]; val _ = tagF

          a.zip(ProviderMagnet.identity[Bracket[F, Throwable]])
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

        override def apply(a: ProviderMagnet[ZManaged[R, E, A]])(implicit tag: ResourceTag[Out]): ProviderMagnet[FromZIO[R, E, A]] = {
          import tag.tagFull
          a.map(fromZIO)
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

      sealed trait InnerResource0 {
        def extract: B
        def deallocate: F[Unit]
      }
    }
  }

  @inline
  private[this] final def evalMapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => F[B])(implicit F: DIEffect[F]): DIResourceBase[F, B] = {
    flatMapImpl(self)(a => DIResource.make(f(a))(_ => F.unit))
  }

  @inline
  private[this] final def logAcquireImpl[F[_], A](self: DIResourceBase[F, A])(f: (=> F[self.InnerResource]) => F[self.InnerResource]): DIResourceBase[F, A] = {
    new DIResourceBase[F, A] {
      override final type InnerResource = self.InnerResource
      override def acquire: F[InnerResource] = f(self.acquire)
      override def release(resource: InnerResource): F[Unit] = self.release(resource)
      override def extract(resource: InnerResource): A = self.extract(resource)
    }
  }

  @inline
  private[this] final def logReleaseImpl[F[_], A](self: DIResourceBase[F, A])(f: (self.InnerResource => F[Unit], self.InnerResource) => F[Unit]): DIResourceBase[F, A] = {
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
    def apply[A: ResourceTag]: ResourceTag[A] = implicitly

    implicit def resourceTag[R <: DIResourceBase[F0, A0]: Tag, F0[_]: TagK, A0: Tag]: ResourceTag[R with DIResourceBase[F0, A0]] {type F[X] = F0[X]; type A = A0} = {
      new ResourceTag[R] {
        type F[X] = F0[X]
        type A = A0
        val tagK: TagK[F0] = TagK[F0]
        val tagA: Tag[A0] = Tag[A0]
        val tagFull: Tag[R] = Tag[R]
      }
    }

    def fakeResourceTagMacroIntellijWorkaroundImpl[R <: DIResourceBase[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[ResourceTag[R]] = {
      val tagMacro = new TagMacro(c)
      tagMacro.makeTagImpl[R] // run the macro AGAIN, to get a fresh error message
      val tagTrace = tagMacro.getImplicitError()

      c.abort(c.enclosingPosition, s"could not find implicit ResourceTag for ${c.universe.weakTypeOf[R]}!\n$tagTrace")
    }
  }

  trait ResourceTagLowPriority {
    /**
      * The `resourceTag` implicit above works perfectly fine, this macro here is exclusively
      * a workaround for highlighting in Intellij IDEA
      *
      * (it's also used to display error trace from TagK's @implicitNotFound)
      *
      * TODO: report to IJ bug tracker
      */
    implicit final def fakeResourceTagMacroIntellijWorkaround[R <: DIResourceBase[Any, Any]]: ResourceTag[R] = macro ResourceTag.fakeResourceTagMacroIntellijWorkaroundImpl[R]
  }

}
