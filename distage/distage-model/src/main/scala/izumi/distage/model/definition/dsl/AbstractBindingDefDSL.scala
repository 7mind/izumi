package izumi.distage.model.definition.dsl

import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.DIResource.{DIResourceBase, ResourceTag}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.MultipleInstruction.ImplWithReference
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.{AddTagsAll, SetIdAll}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL._
import izumi.distage.model.definition.{Binding, BindingTag, Bindings, ImplDef}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, IdContract, SafeType, Tag, TagK}
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.language.{CodePositionMaterializer, SourceFilePosition}

import scala.collection.mutable

trait AbstractBindingDefDSL[BindDSL[_], MultipleDSL[_], SetDSL[_]] {
  private[this] final val mutableState: mutable.ArrayBuffer[BindingRef] = _initialState

  private[definition] def _initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  private[definition] def _bindDSL[T: Tag](ref: SingletonRef): BindDSL[T]

  private[definition] def _multipleDSL[T: Tag](ref: MultipleRef): MultipleDSL[T]

  private[definition] def _setDSL[T: Tag](ref: SetRef): SetDSL[T]

  private[definition] def frozenState: collection.Seq[Binding] = {
    mutableState.flatMap(_.interpret)
  }

  private[definition] def registered[T <: BindingRef](bindingRef: T): T = {
    mutableState += bindingRef
    bindingRef
  }

  final protected def make[T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): BindDSL[T] = {
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

  final protected def bind[T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    bind[T](AnyConstructor[T].provider)
  }

  final protected def bind[T: Tag](instance: => T)(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    bind(ProviderMagnet.lift(instance))
  }

  final protected def bindValue[T: Tag](instance: T)(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    bindImpl[T](ImplDef.InstanceImpl(SafeType.get[T], instance))
  }

  final protected def bind[T: Tag](function: ProviderMagnet[T])(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    bindImpl[T](ImplDef.ProviderImpl(SafeType.get[T], function.get))
  }

  final protected def bindEffect[F[_] : TagK, T: Tag](instance: F[T])(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    bindImpl[T](ImplDef.EffectImpl(SafeType.get[T], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[F[T]], instance)))
  }

  final protected def bindEffect[F[_] : TagK, T: Tag](function: ProviderMagnet[F[T]])(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    bindImpl[T](ImplDef.EffectImpl(SafeType.get[T], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[F[T]], function.get)))
  }

  final protected def bindResource[R <: DIResourceBase[Any, Any]: AnyConstructor](implicit tag: ResourceTag[R], pos: CodePositionMaterializer): MultipleDSL[tag.A] = {
    bindResource[R](AnyConstructor[R].provider)
  }

  final protected def bindResource[R <: DIResourceBase[Any, Any]](instance: R with DIResourceBase[Any, Any])(implicit tag: ResourceTag[R], pos: CodePositionMaterializer): MultipleDSL[tag.A] = {
    import tag._
    bindImpl[A](ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[R], instance)))
  }

  final protected def bindResource[R <: DIResourceBase[Any, Any]](function: ProviderMagnet[R with DIResourceBase[Any, Any]])(implicit tag: ResourceTag[R], pos: CodePositionMaterializer): MultipleDSL[tag.A] = {
    import tag._
    bindImpl[A](ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], function.get)))
  }

  final protected def bindResource[R0, R <: DIResourceBase[Any, Any]](function: ProviderMagnet[R0])(implicit
                                                                                                    adapt: ProviderMagnet[R0] => ProviderMagnet[R with DIResourceBase[Any, Any]],
                                                                                                    tag: ResourceTag[R],
                                                                                                    pos: CodePositionMaterializer): MultipleDSL[tag.A] = {
    import tag._
    bindImpl[A](ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], adapt(function).get)))
  }

  private[this] def bindImpl[T: Tag](implDef: ImplDef)(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    val ref = registered(new MultipleRef(SingletonBinding(DIKey.get[T], implDef, Set.empty, pos.get.position), pos.get.position))
    _multipleDSL[T](ref)
  }
}

object AbstractBindingDefDSL {

  trait BindingRef {
    def interpret: collection.Seq[Binding]
  }

  final class SingletonRef(initial: SingletonBinding[DIKey.TypeKey], ops: mutable.Queue[SingletonInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret: collection.Seq[ImplBinding] = Seq(
      ops.foldLeft(initial: SingletonBinding[DIKey.BasicKey]) {
        (b, instr) =>
          instr match {
            case SetImpl(implDef) => b.withImplDef(implDef)
            case AddTags(tags) => b.addTags(tags)
            case s: SetId[_] =>
              val key = DIKey.IdKey(b.key.tpe, s.id)(s.idContract)
              b.withTarget(key)
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

  final class MultipleRef(initial: SingletonBinding[DIKey.TypeKey], pos: SourceFilePosition, ops: mutable.Queue[MultipleInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret: collection.Seq[ImplBinding] = {
      val (init, tags, refs) = ops.foldLeft((initial: SingletonBinding[DIKey.BasicKey], Set.empty[BindingTag], Seq.empty[SingletonBinding[DIKey]])) {
        case ((base, tagAcc, refs), instr) =>
        instr match {
          case s: MultipleInstruction.SetId[_] => (base.withTarget(DIKey.IdKey(base.key.tpe, s.id)(s.idContract)), tagAcc, refs)
          case MultipleInstruction.AddTags(tags) => (base, tagAcc ++ tags, refs)
          case ImplWithReference(key) =>
            (base, tagAcc, SingletonBinding(key, ImplDef.ReferenceImpl(base.implementation.implType, base.key, weak = false), Set.empty, pos) +: refs)
        }
      }

      init.addTags(tags) :: refs.reverse.map(_.addTags(tags)).toList
    }

    def key: DIKey.TypeKey = initial.key

    def append(op: MultipleInstruction): MultipleRef = {
      ops += op
      this
    }
  }

  final class SetRef
  (
    initial: EmptySetBinding[DIKey.TypeKey],
    setOps: mutable.Queue[SetInstruction] = mutable.Queue.empty,
    elems: mutable.Queue[SetElementRef] = mutable.Queue.empty,
    multiElems: mutable.Queue[MultiSetElementRef] = mutable.Queue.empty,
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

  final class SetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[SetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): SetElementBinding = {
      val implKey = DIKey.TypeKey(implDef.implType)
      val elKey = DIKey.SetElementKey(setKey, implKey, Some(implDef))

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

  final class MultiSetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[MultiSetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): Seq[Binding] = {
      val valueProxyKey = DIKey.IdKey(implDef.implType, DIKey.MultiSetImplId(setKey, implDef))
      val valueProxyBinding = SingletonBinding(valueProxyKey, implDef, Set.empty, pos)

      val elementKey = DIKey.SetElementKey(setKey, valueProxyKey, Some(implDef))
      val refBind0 = SetElementBinding(elementKey, ImplDef.ReferenceImpl(valueProxyBinding.key.tpe, valueProxyBinding.key, weak = false), Set.empty, pos)

      val refBind = ops.foldLeft(refBind0) {
        (b, op) =>
          op match {
            case MultiSetElementInstruction.MultiAddTags(tags) => b.addTags(tags)
          }
      }

      Seq(valueProxyBinding, refBind)
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

  sealed trait MultipleInstruction
  object MultipleInstruction {
    final case class AddTags(tags: Set[BindingTag]) extends MultipleInstruction
    final case class SetId[I](id: I)(implicit val idContract: IdContract[I]) extends MultipleInstruction
    final case class ImplWithReference(key: DIKey) extends MultipleInstruction
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
