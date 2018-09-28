package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleDefDSL.{BindingRef, SetRef, SingletonRef}
import com.github.pshirshov.izumi.distage.model.definition.ModuleDefDSL.{BindDSL, IdentSet, SetDSL}
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

trait AbstractModuleDefDSL {
  protected[definition] final val mutableState: mutable.ArrayBuffer[BindingRef] = _initialState

  protected def _initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  protected[definition] def frozenState: Seq[Binding] =  {
    mutableState
      .flatMap {
        case SingletonRef(b) => Seq(b)
        case SetRef(_, all) => all.map(_.ref)
      }
  }

  final protected def make[T: Tag](implicit pos: CodePositionMaterializer): BindDSL[T] = {
    val binding = Bindings.binding[T]
    val ref = SingletonRef(binding)

    mutableState += ref

    new BindDSL(ref, binding)
  }

  /**
    * Multibindings are useful for implementing event listeners, plugins, hooks, http routes, etc.
    *
    * To define a multibinding use `.many` and `.add` methods in ModuleDef DSL:
    *
    * {{{
    * import cats.effect._, org.http4s._, org.http4s.dsl.io._, scala.concurrent.ExecutionContext.Implicits.global
    * import distage._
    *
    * object HomeRouteModule extends ModuleDef {
    *   many[HttpRoutes[IO]].add {
    *     HttpRoutes.of[IO] { case GET -> Root / "home" => Ok(s"Home page!") }
    *   }
    * }
    * }}}
    *
    * Multibindings defined in different modules will be merged together into a single Set.
    * You can summon a multibinding by type `Set[_]`:
    *
    * {{{
    * import cats.implicits._, import org.http4s.server.blaze._, import org.http4s.implicits._
    *
    * object BlogRouteModule extends ModuleDef {
    *   many[HttpRoutes[IO]].add {
    *     HttpRoutes.of[IO] { case GET -> Root / "blog" / post => Ok("Blog post ``$post''!") }
    *   }
    * }
    *
    * class HttpServer(routes: Set[HttpRoutes[IO]]) {
    *   val router = routes.foldK
    *
    *   def serve = BlazeBuilder[IO]
    *     .bindHttp(8080, "localhost")
    *     .mountService(router, "/")
    *     .start
    * }
    *
    * val context = Injector().produce(HomeRouteModule ++ BlogRouteModule)
    * val server = context.get[HttpServer]
    *
    * val testRouter = server.router.orNotFound
    *
    * testRouter.run(Request[IO](uri = uri("/home"))).flatMap(_.as[String]).unsafeRunSync
    * // Home page!
    *
    * testRouter.run(Request[IO](uri = uri("/blog/1"))).flatMap(_.as[String]).unsafeRunSync
    * // Blog post ``1''!
    * }}}
    *
    * @see Guice wiki on Multibindings: https://github.com/google/guice/wiki/Multibindings
    */
  final protected def many[T: Tag](implicit pos: CodePositionMaterializer): SetDSL[T] = {
    val binding = Bindings.emptySet[T]
    val setRef = {
      val ref = SingletonRef(binding)
      SetRef(ref, mutable.ArrayBuffer(ref))
    }

    mutableState += setRef

    new SetDSL(setRef, IdentSet(binding.key, binding.tags, binding.origin))
  }


}

object AbstractModuleDefDSL {

  sealed trait BindingRef

  final case class SingletonRef(var ref: Binding) extends BindingRef

  final case class SetRef(emptySetBinding: SingletonRef, all: mutable.ArrayBuffer[SingletonRef]) extends BindingRef

}
