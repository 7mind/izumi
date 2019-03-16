package com.github.pshirshov.izumi.distage.model.definition.dsl

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.{AddTagsAll, SetIdAll}
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction._
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.{BindingRef, SetRef, SingletonInstruction, SingletonRef}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, BindingTag, Bindings, ImplDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, IdContract, Tag}
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

trait AbstractBindingDefDSL[BindDSL[_], SetDSL[_]] {
  private[this] final val mutableState: mutable.ArrayBuffer[BindingRef] = _initialState

  private[definition] def _initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

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
    val ref = registered(new SingletonRef(Bindings.binding[T]))
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
    * val objects = Injector().produce(HomeRouteModule ++ BlogRouteModule)
    * val server = objects.get[HttpServer]
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
//  final protected def setOf[T: Tag](implicit pos: CodePositionMaterializer): SetDSL[T] = {
  final protected def many[T: Tag](implicit pos: CodePositionMaterializer): SetDSL[T] = {
    val setRef = registered(new SetRef(Bindings.emptySet[T]))
    _setDSL(setRef)
  }

//  @deprecated("use .setOf", "14.03.2019")
//  final protected def many[T: Tag](implicit pos: CodePositionMaterializer): SetDSL[T] = setOf[T]

  /** Same as `make[T].from(implicitly[T])` **/
  final protected def addImplicit[T: Tag](implicit instance: T, pos: CodePositionMaterializer): Unit = {
    registered(new SingletonRef(Bindings.binding(instance))).discard()
  }

  /** Same as `make[T].named(name).from(implicitly[T])` **/
  final protected def addImplicit[T: Tag](name: String)(implicit instance: T, pos: CodePositionMaterializer): Unit = {
    registered(new SingletonRef(Bindings.binding(instance), mutable.Queue(SingletonInstruction.SetId(name)))).discard()
  }

}

object AbstractBindingDefDSL {

  trait BindingRef {
    def interpret: Seq[Binding]
  }

  final class SingletonRef(initial: SingletonBinding[DIKey.TypeKey], ops: mutable.Queue[SingletonInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret: Seq[ImplBinding] = Seq(
      ops.foldLeft(initial: ImplBinding) {
        (b, instr) =>
          instr match {
            case SetImpl(implDef) => b.withImplDef(implDef)
            case AddTags(tags) => b.addTags(tags)
            case s: SetId[_] => b.withTarget(DIKey.IdKey(b.key.tpe, s.id)(s.idContract))
            case _: SetIdFromImplName => b.withTarget(DIKey.IdKey(b.key.tpe, b.implementation.implType.tpe.toString.toLowerCase))
          }
      }
    )

    def key: DIKey.TypeKey = initial.key

    def append(op: SingletonInstruction): SingletonRef = {
      ops += op
      this
    }
  }

  private final class MultiSetHackId(private val long: Long) extends AnyVal {
    override def toString: String = s"multi.${long.toString}"
  }

  final class SetRef
  (
    initial: EmptySetBinding[DIKey.TypeKey]
    , setOps: mutable.Queue[SetInstruction] = mutable.Queue.empty
    , elems: mutable.Queue[SetElementRef] = mutable.Queue.empty
    , multiElems: mutable.Queue[MultiSetElementRef] = mutable.Queue.empty
  ) extends BindingRef {
    override def interpret: Seq[Binding] = {
      val emptySetBinding = setOps.foldLeft(initial: EmptySetBinding[DIKey.BasicKey]) {
        (b, instr) =>
          instr match {
            case AddTagsAll(tags) => b.addTags(tags)
            case s: SetIdAll[_] => b.withTarget(DIKey.IdKey(b.key.tpe, s.id)(s.idContract))
          }
      }

      val finalKey = emptySetBinding.key

      val elemBindings = elems.map(_.interpret(finalKey))
      val multiSetBindings = multiElems.flatMap(_.interpret(finalKey))

      emptySetBinding +: elemBindings ++: multiSetBindings
    }

    def appendElem(op: SetElementRef): SetRef = {
      elems += op
      this
    }

    def appendOp(op: SetInstruction): SetRef = {
      setOps += op
      this
    }

    def appendMultiElem(op: MultiSetElementRef): SetRef = {
      multiElems += op
      this
    }
  }

  final class SetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[SetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): SetElementBinding[DIKey.BasicKey] =
      ops.foldLeft(SetElementBinding(setKey, implDef, BindingTag.untaggedTags, pos)) {
        (b, instr) =>
          instr match {
            case ElementAddTags(tags) => b.addTags(tags)
          }
      }

    def append(op: SetElementInstruction): SetElementRef = {
      ops += op
      this
    }
  }

  final class MultiSetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[MultiSetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): Seq[Binding] = {
      // always positive: 0[32-bits of impldef hashcode][31 bit of this hashcode]
      val hopefullyRandomId = ((System.identityHashCode(implDef) & 0xffffffffL) << 31) | ((this.hashCode() & 0xffffffffL) >> 1)

      val bind = SingletonBinding(DIKey.IdKey(implDef.implType, new MultiSetHackId(hopefullyRandomId)), implDef, BindingTag.untaggedTags, pos)

      val refBind0 = SetElementBinding(setKey, ImplDef.ReferenceImpl(bind.key.tpe, bind.key, weak = false), BindingTag.untaggedTags, pos)

      val refBind = ops.foldLeft(refBind0) {
        (b, op) =>
          op match {
            case MultiSetElementInstruction.MultiAddTags(tags) => b.addTags(tags)
          }
      }

      Seq(bind, refBind)
    }

    def append(op: MultiSetElementInstruction): MultiSetElementRef = {
      ops += op
      this
    }
  }

  sealed trait SingletonInstruction

  object SingletonInstruction {

    final case class SetImpl(implDef: ImplDef) extends SingletonInstruction

    final case class AddTags(tags: Set[BindingTag]) extends SingletonInstruction

    final case class SetId[I](id: I)(implicit val idContract: IdContract[I]) extends SingletonInstruction

    final case class SetIdFromImplName() extends SingletonInstruction
  }

  sealed trait SetInstruction

  object SetInstruction {

    final case class AddTagsAll(tags: Set[BindingTag]) extends SetInstruction

    final case class SetIdAll[I](id: I)(implicit val idContract: IdContract[I]) extends SetInstruction

  }

  sealed trait SetElementInstruction

  object SetElementInstruction {

    final case class ElementAddTags(tags: Set[BindingTag]) extends SetElementInstruction

  }

  sealed trait MultiSetElementInstruction

  object MultiSetElementInstruction {

    final case class MultiAddTags(tags: Set[BindingTag]) extends MultiSetElementInstruction

  }

}
