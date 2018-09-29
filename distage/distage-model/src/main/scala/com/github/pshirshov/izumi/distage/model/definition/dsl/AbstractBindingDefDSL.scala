package com.github.pshirshov.izumi.distage.model.definition.dsl

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.{AddTagsAll, SetIdAll}
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction.{AddTags, SetId, SetImpl}
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.{BindingRef, SetRef, SingletonRef}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, Bindings, ImplDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, Tag, IdContract}
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

import scala.collection.mutable

trait AbstractBindingDefDSL {
  private[this] final val mutableState: mutable.ArrayBuffer[BindingRef] = _initialState

  private[definition] type BindDSL[T]

  private[definition] type SetDSL[T]

  protected def _initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  private[definition] def _bindDSL[T: Tag](ref: SingletonRef): BindDSL[T]

  private[definition] def _setDSL[T: Tag](ref: SetRef): SetDSL[T]

  private[definition] def frozenState: Seq[Binding] = {
    mutableState.flatMap(_.interpret)
  }

  private[definition] def registered[T <: BindingRef](bindingRef: T): T = {
    mutableState += bindingRef
    bindingRef
  }

  final protected def make[T: Tag](implicit pos: CodePositionMaterializer): BindDSL[T] = {
    val ref = registered(SingletonRef(Bindings.binding[T]))
    _bindDSL[T](ref)
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
    val setRef = registered(SetRef(Bindings.emptySet[T]))
    _setDSL(setRef)
  }


}

object AbstractBindingDefDSL {

  trait BindingRef {
    def interpret: Seq[Binding]
  }

  final case class SingletonRef(initial: SingletonBinding[DIKey.TypeKey], ops: mutable.Queue[SingletonInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret: Seq[ImplBinding] = Seq(
      ops.foldLeft(initial: ImplBinding) {
        (b, instr) =>
          instr match {
            case SetImpl(implDef) => b.withImplDef(implDef)
            case AddTags(tags) => b.addTags(tags)
            case s: SetId[_] => b.withTarget(DIKey.IdKey(b.key.tpe, s.id)(s.idContract))
          }
      }
    )
  }

  final case class SetRef(initial: EmptySetBinding[DIKey.TypeKey], setOps: mutable.Queue[SetInstruction] = mutable.Queue.empty, elems: mutable.Queue[SetElementRef] = mutable.Queue.empty) extends BindingRef {
    override def interpret: Seq[Binding] = {
      val emptySetBinding = setOps.foldLeft(initial: EmptySetBinding[DIKey.BasicKey]) {
        (b, instr) =>
          instr match {
            case AddTagsAll(tags) => b.addTags(tags)
            case s: SetIdAll[_] => b.withTarget(DIKey.IdKey(b.key.tpe, s.id)(s.idContract))
          }
      }

      emptySetBinding +: elems.map(_.interpret(emptySetBinding.key))
    }
  }

  final case class SetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[SetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): SetElementBinding[DIKey.BasicKey] =
      ops.foldLeft(SetElementBinding(setKey, implDef, Set.empty, pos)) {
        (b, instr) =>
          instr match {
            case ElementAddTags(tags) => b.addTags(tags)
          }
      }
  }

  sealed trait SingletonInstruction

  object SingletonInstruction {

    final case class SetImpl(implDef: ImplDef) extends SingletonInstruction

    final case class AddTags(tags: Set[String]) extends SingletonInstruction

    final case class SetId[I](id: I)(implicit val idContract: IdContract[I]) extends SingletonInstruction

  }

  sealed trait SetInstruction

  object SetInstruction {

    final case class AddTagsAll(tags: Set[String]) extends SetInstruction

    final case class SetIdAll[I](id: I)(implicit val idContract: IdContract[I]) extends SetInstruction

  }

  sealed trait SetElementInstruction

  object SetElementInstruction {

    final case class ElementAddTags(tags: Set[String]) extends SetElementInstruction

  }

}
