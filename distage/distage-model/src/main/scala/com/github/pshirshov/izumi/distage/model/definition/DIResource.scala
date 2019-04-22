package com.github.pshirshov.izumi.distage.model.definition

import java.util.concurrent.{ExecutorService, TimeUnit}

import cats.Applicative
import cats.effect.Bracket
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{Tag, TagK}
import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

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
  * Usage is done via [[DIResource.DIResourceUse.use use]]:
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
  * you can control it by controlling the scope in `.use` or you can opt-out and use
  * [[DIResourceBase.acquire]] and [[DIResourceBase.release]] manually.
  *
  * @see ModuleDef.fromResource: [[com.github.pshirshov.izumi.distage.model.definition.dsl.ModuleDefDSL.BindDSL.fromResource]]
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

  def make[F[_], A](acquire: => F[A])(release: A => F[Unit]): DIResource[F, A] = {
    def a = acquire
    def r = release
    new DIResource[F, A] {
      override def acquire: F[A] = a
      override def release(resource: A): F[Unit] = r(resource)
    }
  }

  def makeSimple[A](acquire: => A)(release: A => Unit): DIResource[Identity, A] = {
    def a = acquire
    def r = release
    new DIResource.Simple[A] {
      override def acquire: A = a
      override def release(a: A): Unit = r(a)
    }
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire)(_.close)
  }

  private val fallback = TrivialLogger.make[DIResource.type]("izumi.distage.debug.bootstrap", forceLog = true)

  def fromExecutor[A <: ExecutorService](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire) {
      es =>
      if (!(es.isShutdown || es.isTerminated)) {
        es.shutdown()
        if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
          val dropped = es.shutdownNow()
          fallback.log(s"Executor $es didn't finish in time, ${dropped.size()} tasks were dropped")
        }
      }
    }
  }

  implicit final class DIResourceUse[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    def use[B](use: A => F[B])(implicit F: DIEffect[F]): F[B] = {
      F.bracket(acquire = resource.acquire)(release = resource.release)(
        use = a => F.flatMap(F.maybeSuspend(resource.extract(a)))(use)
      )
    }
  }

  /** Convert [[cats.effect.Resource]] into a [[DIResource]] */
  def fromCats[F[_]: Bracket[?[_], Throwable], A](resource: Resource[F, A]): DIResource.Cats[F, A] = {
    new Cats[F, A] {
      override def acquire: F[(A, F[Unit])] = resource.allocated
    }
  }

  implicit final class DIResourceCatsSyntax[F[_], A](private val resource: DIResourceBase[F, A]) extends AnyVal {
    /** Convert [[DIResource]] into a [[cats.effect.Resource]] */
    def toCats[G[x] >: F[x]: Applicative]: Resource[G, A] = {
      Resource.make[G, resource.InnerResource](resource.acquire)(resource.release).map(resource.extract)
    }
  }

  trait Simple[A] extends DIResource[Identity, A]

  trait Mutable[+A] extends DIResourceBase[Identity, A] with AutoCloseable {
    this: A =>
    override final type InnerResource = Unit
    override final def release(resource: Unit): Unit = close()
    override final def extract(resource: Unit): A = this
  }

  trait Cats[F[_], A] extends DIResourceBase[F, A] {
    override final type InnerResource = (A, F[Unit])
    override final def release(resource: (A, F[Unit])): F[Unit] = resource._2
    override final def extract(resource: (A, F[Unit])): A = resource._1
  }

  trait DIResourceBase[+F[_], +OuterResource] { self =>
    type InnerResource
    def acquire: F[InnerResource]
    def release(resource: InnerResource): F[Unit]
    def extract(resource: InnerResource): OuterResource

    final def map[B](f: OuterResource => B): DIResourceBase[F, B] = mapImpl(this)(f)
    final def flatMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => DIResourceBase[G, B]): DIResourceBase[G, B] = flatMapImpl[G, OuterResource, B](this)(f)
    final def evalMap[G[x] >: F[x]: DIEffect, B](f: OuterResource => G[B]): DIResourceBase[G, B] = evalMapImpl[G, OuterResource, B](this)(f)
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
  implicit def providerFromCats[F[_]: TagK, A: Tag](resource: Resource[F, A]): ProviderMagnet[DIResource.Cats[F, A]] = {
    providerFromCatsProvider(ProviderMagnet.pure(resource))
  }

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
    *       dependency on `Bracket[F, Throwable]` for
    *       your corresponding `F` type
    */
  implicit def providerFromCatsProvider[F[_]: TagK, A: Tag](resourceProvider: ProviderMagnet[Resource[F, A]]): ProviderMagnet[DIResource.Cats[F, A]] = {
    resourceProvider
      .zip(ProviderMagnet.identity[Bracket[F, Throwable]])
      .map { case (resource, bracket) => fromCats(resource)(bracket) }
  }

  private[this] final def mapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => B): DIResourceBase[F, B] = {
    new DIResourceBase[F, B] {
      type InnerResource = self.InnerResource
      def acquire: F[InnerResource] = self.acquire
      def release(resource: InnerResource): F[Unit] = self.release(resource)
      def extract(resource: InnerResource): B = f(self.extract(resource))
    }
  }

  private[this] final def flatMapImpl[F[_], A, B](self: DIResourceBase[F ,A])(f: A => DIResourceBase[F, B])(implicit F: DIEffect[F]): DIResourceBase[F, B] = {

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

  private[this] final def evalMapImpl[F[_], A, B](self: DIResourceBase[F, A])(f: A => F[B])(implicit F: DIEffect[F]): DIResourceBase[F, B] = {
    flatMapImpl(self)(a => DIResource.make(f(a))(_ => F.unit))
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

    implicit def resourceTag[R <: DIResourceBase[F0, A0] : Tag, F0[_] : TagK, A0: Tag]: ResourceTag[R with DIResourceBase[F0, A0]] {type F[X] = F0[X]; type A = A0} = {
      new ResourceTag[R] {
        type F[X] = F0[X]
        type A = A0
        val tagK: TagK[F0] = TagK[F0]
        val tagA: Tag[A0] = Tag[A0]
        val tagFull: Tag[R] = Tag[R]
      }
    }

    def fakeResourceTagMacroIntellijWorkaroundImpl[R <: DIResourceBase[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[ResourceTag[R]] = {
      c.abort(c.enclosingPosition, s"could not find implicit ResourceTag for ${c.universe.weakTypeOf[R]}!")
    }
  }

  trait ResourceTagLowPriority {
    /**
      * The `resourceTag` implicit above works perfectly fine, this macro here is exclusively
      * a workaround for highlighting in Intellij IDEA
      * TODO: include link to IJ bug tracker
      */
    implicit final def fakeResourceTagMacroIntellijWorkaround[R <: DIResourceBase[Any, Any]]: ResourceTag[R] = macro ResourceTag.fakeResourceTagMacroIntellijWorkaroundImpl[R]

////    ^ the above should just work as an implicit but unfortunately doesn't, this macro does the same thing scala typer should've done, but manually...
//    def resourceTagMacroImpl[R <: DIResourceBase[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[ResourceTag[R]] = {
//      import c.universe._
//
//      val full = weakTypeOf[R].widen
//      val base = weakTypeOf[R].baseType(symbolOf[DIResourceBase[Any, Any]])
//      val List(f, a) = base.typeArgs
//
//      c.Expr[ResourceTag[R]](q"${reify(ResourceTag)}.resourceTag[$full, $f, $a]")
//    }
  }

}
