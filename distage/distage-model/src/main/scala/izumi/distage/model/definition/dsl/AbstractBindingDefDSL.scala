package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.{AddTagsAll, SetIdAll}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.{BindingRef, SetRef, SingletonInstruction, SingletonRef}
import izumi.distage.model.definition.{Binding, BindingTag, Bindings, ImplDef}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, IdContract, Tag, SafeType}
import izumi.fundamentals.platform.jvm.SourceFilePosition
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.reflection.CodePositionMaterializer

import scala.collection.mutable

trait AbstractBindingDefDSL[BindDSL[_], SetDSL[_]] {
  private[this] final val mutableState: mutable.ArrayBuffer[BindingRef] = _initialState

  private[definition] def _initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  private[definition] def _bindDSL[T: Tag](ref: SingletonRef): BindDSL[T]

  private[definition] def _setDSL[T: Tag](ref: SetRef): SetDSL[T]

  private[definition] def frozenState: collection.Seq[Binding] = {
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
    * Set bindings are useful for implementing event listeners, plugins, hooks, http routes, etc.
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
    * Set bindings defined in different modules will be merged together into a single Set.
    * You can summon a created Set by type `Set[T]`:
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
  final protected def many[T: Tag](implicit pos: CodePositionMaterializer): SetDSL[T] = {
    val setRef = registered(new SetRef(Bindings.emptySet[T]))
    _setDSL(setRef)
  }

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
    def interpret: collection.Seq[Binding]
  }

  final class SingletonRef(initial: SingletonBinding[DIKey.TypeKey], ops: mutable.Queue[SingletonInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret: collection.Seq[ImplBinding] = Seq(
      ops.foldLeft(initial: ImplBinding) {
        (b, instr) =>
          instr match {
            case SetImpl(implDef) => b.withImplDef(implDef)
            case AddTags(tags) => b.addTags(tags)
            case s: SetId[_] => b.withTarget(DIKey.IdKey(b.key.tpe, s.id)(s.idContract))
            case _: SetIdFromImplName => b.withTarget(DIKey.IdKey(b.key.tpe, b.implementation.implType.use(_.toString.toLowerCase)))
          }
      }
    )

    def key: DIKey.TypeKey = initial.key

    def append(op: SingletonInstruction): SingletonRef = {
      ops += op
      this
    }
  }

  final class SetRef
  (
    initial: EmptySetBinding[DIKey.TypeKey]
    , setOps: mutable.Queue[SetInstruction] = mutable.Queue.empty
    , elems: mutable.Queue[SetElementRef] = mutable.Queue.empty
    , multiElems: mutable.Queue[MultiSetElementRef] = mutable.Queue.empty
  ) extends BindingRef {
    override def interpret: collection.Seq[Binding] = {
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

//  private def key(basic: DIKey.TypeKey, dis: String, d: ImplDef): DIKey.BasicKey = {
//    d match {
//      case implDef: ImplDef.DirectImplDef =>
//        implDef match {
//          case _: ImplDef.TypeImpl=>
//            basic
//          case _ =>
//            basic.named(dis)
//        }
//      case _: ImplDef.RecursiveImplDef =>
//        basic.named(dis)
//    }
//  }

  final class SetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[SetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): SetElementBinding = {
      val implKey = DIKey.TypeKey(implDef.implType)
      //val refkey = key(implKey, pos.toString, implDef)
      val refkey = implKey.named(DIKey.SetLocId(pos.toString))
      val elKey = DIKey.SetElementKey(setKey, refkey)

      ops.foldLeft(SetElementBinding(elKey, implDef, Set.empty, pos)) {
        (b, instr) =>
          instr match {
            case ElementAddTags(tags) => b.addTags(tags)
          }
      }
    }



    def append(op: SetElementInstruction): SetElementRef = {
      ops += op
      this
    }
  }

//  final class SetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[SetElementInstruction] = mutable.Queue.empty) {
//    def interpret(setKey: DIKey.BasicKey): SetElementBinding = {
//      val implKey = DIKey.TypeKey(implDef.implType).named(pos.toString)
//      val elKey = DIKey.SetElementKey(setKey, implKey)
//      ops.foldLeft(SetElementBinding(elKey, implDef, Set.empty, pos)) {
//        (b, instr) =>
//          instr match {
//            case ElementAddTags(tags) => b.addTags(tags)
//          }
//      }
//    }
//
//
//
//    def append(op: SetElementInstruction): SetElementRef = {
//      ops += op
//      this
//    }
//  }

  final class MultiSetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[MultiSetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): Seq[Binding] = {
      val ukey = DIKey.IdKey(implDef.implType, DIKey.SetLocId(implDef.toString))

      val bind = SingletonBinding(ukey, implDef, Set.empty, pos)

      val elKey = DIKey.SetElementKey(setKey, ukey)
      val refBind0 = SetElementBinding(elKey, ImplDef.ReferenceImpl(bind.key.tpe, bind.key, weak = false), Set.empty, pos)

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
