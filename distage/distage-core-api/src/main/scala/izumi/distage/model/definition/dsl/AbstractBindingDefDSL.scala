package izumi.distage.model.definition.dsl

import izumi.distage.constructors.{FactoryConstructor, TraitConstructor}
import izumi.distage.model.definition.*
import izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.*
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.{AddTagsAll, SetIdAll}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction.*
import izumi.distage.model.exceptions.dsl.InvalidFunctoidModifier
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, MultiSetImplId}
import izumi.distage.model.reflection.SetKeyMeta
import izumi.fundamentals.platform.language.{CodePositionMaterializer, SourceFilePosition}
import izumi.reflect.Tag

import scala.collection.mutable

trait AbstractBindingDefDSL[BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]] extends AbstractBindingDefDSLMacro[BindDSL] { self =>
  private final val mutableState: mutable.ArrayBuffer[BindingRef] = _initialState
  protected def _initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  private[definition] def _bindDSL[T](ref: SingletonRef): BindDSL[T]
  private[definition] def _bindDSLAfterFrom[T](ref: SingletonRef): BindDSLAfterFrom[T]
  private[definition] def _setDSL[T](ref: SetRef): SetDSL[T]

  private[definition] def frozenState: Iterator[Binding] = {
    mutableState.iterator.flatMap(_.interpret())
  }

  protected def _registered[T <: BindingRef](bindingRef: T): T = {
    mutableState += bindingRef
    bindingRef
  }

  final protected def _make[T: Tag](provider: Functoid[T])(implicit pos: CodePositionMaterializer): BindDSL[T] = {
    val ref = _registered(new SingletonRef(Bindings.provider[T](provider)))
    _bindDSL[T](ref)
  }

  /** @see [[https://izumi.7mind.io/distage/basics.html#auto-traits Auto-Traits feature]] */
  final protected def makeTrait[T: Tag: TraitConstructor]: BindDSLAfterFrom[T] = {
    val ref = _registered(new SingletonRef(Bindings.provider[T](TraitConstructor[T])))
    _bindDSLAfterFrom[T](ref)
  }

  /** @see [[https://izumi.7mind.io/distage/basics.html#auto-factories Auto-Factories feature]] */
  final protected def makeFactory[T: Tag: FactoryConstructor]: BindDSLAfterFrom[T] = {
    val ref = _registered(new SingletonRef(Bindings.provider[T](FactoryConstructor[T])))
    _bindDSLAfterFrom[T](ref)
  }

  /** @see [[https://izumi.7mind.io/distage/basics.html#subcontexts Subcontexts feature]] */
  final protected def makeSubcontext[T: Tag](submodule: ModuleBase): SubcontextDSL[T] = {
    val ref = _registered(new SubcontextRef(Bindings.subcontext[T](submodule, Functoid.identity[T], Set.empty)))
    new SubcontextDSL[T](ref)
  }
  /** @see [[https://izumi.7mind.io/distage/basics.html#subcontexts Subcontexts feature]] */
  final protected def makeSubcontext[T: Tag]: SubcontextDSL[T] = makeSubcontext[T](ModuleBase.empty)

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
    * import cats.implicits._, org.http4s.server.blaze._, org.http4s.implicits._
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
  final protected def many[T](implicit tag: Tag[Set[T]], pos: CodePositionMaterializer): SetDSL[T] = {
    val setRef = _registered(new SetRef(Bindings.emptySet[T]))
    _setDSL(setRef)
  }

  /** Same as `make[T].from(implicitly[T])` * */
  final protected def addImplicit[T: Tag](implicit instance: T, pos: CodePositionMaterializer): BindDSLAfterFrom[T] = {
    val ref = _registered(new SingletonRef(Bindings.binding(instance)))
    _bindDSLAfterFrom(ref)
  }

  /**
    * Create a dummy binding that throws an exception with an error message when it's created.
    *
    * Useful for prototyping.
    */
  final protected def todo[T: Tag](implicit pos: CodePositionMaterializer): BindDSLAfterFrom[T] = {
    val ref = _registered(new SingletonRef(Bindings.todo(DIKey.get[T])(pos)))
    _bindDSLAfterFrom(ref)
  }

  /**
    * Modify a value bound at `T`. Modifiers stack and are all
    * applied before `T` is added to the object graph;
    * only the final instance is observable.
    *
    * {{{
    *   import distage.{Injector, ModuleDef}
    *
    *   Injector().produceGet[Int](new ModuleDef {
    *     make[Int].from(1)
    *     modify[Int](_ + 1)
    *     modify[Int](_ + 1)
    *   }).use(i => println(s"Got `Int` $i"))
    *   // Got `Int` 3
    * }}}
    *
    * You can also modify while summoning additional dependencies:
    *
    * {{{
    *   modify[Int]("named").by(_.flatAp {
    *     (adder: Adder, multiplier: Multiplier) =>
    *       int: Int =>
    *         multiplier.multiply(adder.add(int, 1), 10)
    *   })
    * }}}
    */
  final protected def modify[T]: ModifyDSL[T, BindDSL, BindDSLAfterFrom, SetDSL] = new ModifyDSL[T, BindDSL, BindDSLAfterFrom, SetDSL](this)
  final private def _modify[T](key: DIKey.BasicKey)(f: Functoid[T] => Functoid[T])(implicit pos: CodePositionMaterializer): SingletonRef = {
    val (tpeKey: DIKey.TypeKey, maybeId) = key match {
      case tpeKey: DIKey.TypeKey => tpeKey -> None
      case idKey @ DIKey.IdKey(tpe, id, m) => DIKey.TypeKey(tpe, m) -> Some(Identifier.fromIdContract(id)(idKey.idContract))
    }
    val newProvider: Functoid[T] = f(Functoid.identityKey[T](key))
    val binding = SingletonBinding(tpeKey, ImplDef.ProviderImpl(newProvider.get.ret, newProvider.get), Set.empty, pos.get.position, isMutator = true)
    val ref = _registered(new SingletonRef(binding))
    maybeId.foreach(ref `append` SetId(_))
    ref
  }

  /**
    * Use this to create utility functions that add bindings mutably to the current module,
    * as opposed to creating new modules and [[IncludesDSL.include including]] them.
    *
    * Example:
    *
    * {{{
    *   import distage.{ClassConstructor, Tag, ModuleDef}
    *   import izumi.distage.model.definition.dsl.ModuleDefDSL
    *
    *   trait RegisteredComponent
    *   class RegisteredComponentImpl extends RegisteredComponent
    *
    *   def addAndRegister[T <: RegisteredComponent: Tag: ClassConstructor](implicit mutateModule: ModuleDefDSL#MutationContext): Unit = {
    *     new mutateModule.dsl {
    *       make[T]
    *
    *       many[RegisteredComponent]
    *         .weak[T]
    *     }
    *   }
    *
    *   new ModuleDef {
    *     addAndRegister[RegisteredComponentImpl]
    *   }
    * }}}
    */
  final class MutationContext {
    abstract class dsl extends AbstractBindingDefDSLMacro[BindDSL] {
      final protected def _make[T: Tag](provider: Functoid[T])(implicit pos: CodePositionMaterializer): BindDSL[T] = self._make[T](provider)
      final protected def makeTrait[T: Tag: TraitConstructor]: BindDSLAfterFrom[T] = self.makeTrait[T]
      final protected def makeFactory[T: Tag: FactoryConstructor]: BindDSLAfterFrom[T] = self.makeFactory[T]
      final protected def makeSubcontext[T: Tag](submodule: ModuleBase): SubcontextDSL[T] = self.makeSubcontext[T](submodule)
      final protected def makeSubcontext[T: Tag]: SubcontextDSL[T] = self.makeSubcontext[T]

      final protected def many[T](implicit tag: Tag[Set[T]], pos: CodePositionMaterializer): SetDSL[T] = self.many[T]

      final protected def addImplicit[T: Tag](implicit instance: T, pos: CodePositionMaterializer): BindDSLAfterFrom[T] = self.addImplicit[T]

      final protected def todo[T: Tag](implicit pos: CodePositionMaterializer): BindDSLAfterFrom[T] = self.todo[T]
      final protected def modify[T]: ModifyDSL[T, BindDSL, BindDSLAfterFrom, SetDSL] = self.modify[T]

      /**
        * Avoid `discarded non-Unit value` warning.
        *
        * @see [[izumi.fundamentals.platform.language.Quirks.discard]]
        */
      @inline final def discard(): Unit = ()
    }
  }
  final protected implicit lazy val mutationContext: MutationContext = new MutationContext

}

object AbstractBindingDefDSL {

  sealed abstract class ModifyDSLBase[T] {

    def by(f: Functoid[T] => Functoid[T])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T]

    def apply(f: T => T)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.map(f))
    }

    def addDependency[B: Tag](implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(DIKey[B]))
    }

    def addDependency[B: Tag](name: Identifier)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(DIKey[B](name)))
    }

    def addDependency(key: DIKey)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(key))
    }

    def addDependencies(keys: Iterable[DIKey])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependencies(keys))
    }

    def annotateParameter[P: Tag](name: Identifier)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.annotateParameter[P](name))
    }

  }

  final class ModifyDSL[T, BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]](dsl: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL]) extends ModifyDSLBase[T] {

    def named(name: Identifier): ModifyNamedDSL[T, BindDSL, BindDSLAfterFrom, SetDSL] = {
      new ModifyNamedDSL(dsl, name)
    }

    override def by(f: Functoid[T] => Functoid[T])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      val key = DIKey.get[T]
      new ModifyTaggingDSL(dsl._modify(key)(f)(pos))
    }

  }

  final class ModifyNamedDSL[T, BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]](
    dsl: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL],
    name: Identifier,
  ) extends ModifyDSLBase[T] {

    override def by(f: Functoid[T] => Functoid[T])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      val key = DIKey.get[T].named(name)
      new ModifyTaggingDSL(dsl._modify(key)(f)(pos))
    }

  }

  final class ModifyTaggingDSL[T](private val mutableState: SingletonRef) extends AnyVal with AddDependencyDSL[T, ModifyTaggingDSL[T]] {

    def tagged(tags: BindingTag*): ModifyTaggingDSL[T] = {
      new ModifyTaggingDSL(mutableState.append(AddTags(tags.toSet)))
    }

    def by(f: Functoid[T] => Functoid[T]): ModifyTaggingDSL[T] = {
      new ModifyTaggingDSL[T](mutableState.append(Modify(f)))
    }

    def modify(f: T => T): ModifyTaggingDSL[T] = {
      by(_.mapSame(f))
    }

    @deprecated("Renamed to .modify", "1.2.0")
    def map(f: T => T): ModifyTaggingDSL[T] = {
      modify(f)
    }

    override protected def _modifyBy(f: Functoid[T] => Functoid[T]): ModifyTaggingDSL[T] = by(f)
  }

  trait AddDependencyDSL[T, Self] extends Any {
    protected def _modifyBy(f: Functoid[T] => Functoid[T]): Self

    def addDependency[B: Tag]: Self = {
      _modifyBy(_.addDependency[B])
    }

    def addDependency[B: Tag](name: Identifier): Self = {
      _modifyBy(_.addDependency[B](name))
    }

    def addDependency(key: DIKey): Self = {
      _modifyBy(_.addDependency(key))
    }

    def addDependencies(keys: Iterable[DIKey]): Self = {
      _modifyBy(_.addDependencies(keys))
    }

    def annotateParameter[P: Tag](name: Identifier): Self = {
      _modifyBy(_.annotateParameter[P](name))
    }
  }

  final class SubcontextDSL[T](override protected val mutableState: SubcontextRef) extends SubcontextDSLBase[T, SubcontextDSL[T]] {

    def named(name: Identifier): SubcontextNamedDSL[T] = {
      addOp(SubcontextInstruction.SetId(name))(new SubcontextNamedDSL[T](_))
    }

    override protected def toSame: SubcontextRef => SubcontextDSL[T] = new SubcontextDSL[T](_)
  }

  final class SubcontextNamedDSL[T](override protected val mutableState: SubcontextRef) extends SubcontextDSLBase[T, SubcontextNamedDSL[T]] {
    override protected def toSame: SubcontextRef => SubcontextNamedDSL[T] = new SubcontextNamedDSL[T](_)
  }

  sealed abstract class SubcontextDSLBase[T, Self] {
    protected def mutableState: SubcontextRef
    protected def toSame: SubcontextRef => Self

    final def tagged(tags: BindingTag*): Self = {
      addOp(SubcontextInstruction.AddTags(tags.toSet))(toSame)
    }

    final def withSubmodule(submodule: ModuleBase): Self = {
      addOp(SubcontextInstruction.AddSubmodule(submodule))(toSame)
    }

    final def extractWith(f: Functoid[T]): Self = {
      addOp(SubcontextInstruction.SetExtractor(f))(toSame)
    }

    final def localDependency[B: Tag]: Self = {
      localDependency(DIKey[B])
    }

    final def localDependency[B: Tag](name: Identifier): Self = {
      localDependency(DIKey[B](name))
    }

    final def localDependency(key: DIKey): Self = {
      localDependencies(key :: Nil)
    }

    final def localDependencies(keys: Iterable[DIKey]): Self = {
      addOp(SubcontextInstruction.AddLocalDependencies(keys))(toSame)
    }

    protected final def addOp[R](op: SubcontextInstruction)(newState: SubcontextRef => R): R = {
      newState(mutableState.append(op))
    }
  }

  trait BindingRef {
    def interpret(): collection.Seq[Binding]
  }

  final class SingletonRef(initial: SingletonBinding[DIKey.TypeKey], ops: mutable.Queue[SingletonInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret(): collection.Seq[ImplBinding] = {
      var b: SingletonBinding[DIKey.BasicKey] = initial
      var refs: List[SingletonBinding[DIKey.BasicKey]] = Nil
      val sortedOps = ops.sortBy {
        case _: SetImpl => 0
        case _: AddTags => 0
        case _: SetId => 0
        case _: SetIdFromImplName => 1
        case _: Modify[?] => 2
        case _: AliasTo => 3
      }
      sortedOps.foreach {
        case SetImpl(implDef) =>
          b = b.withImplDef(implDef)
        case AddTags(tags) =>
          b = b.addTags(tags)
        case SetId(contractedId) =>
          val key = DIKey.TypeKey(b.key.tpe).named(contractedId)
          b = b.withTarget(key)
        case SetIdFromImplName() =>
          b = b.withTarget(DIKey.IdKey(b.key.tpe, b.implementation.implType.tag.longNameWithPrefix.toLowerCase))
        case Modify(functoidModifier: (Functoid[t] => Functoid[u])) =>
          b.implementation match {
            case ImplDef.ProviderImpl(implType, function) =>
              val newProvider = functoidModifier(Functoid(function)).get
              if (newProvider.ret <:< implType) {
                b = b.withImplDef(ImplDef.ProviderImpl(implType, newProvider))
              } else {
                throw new InvalidFunctoidModifier(
                  s"Cannot apply invalid Functoid modifier $functoidModifier, new return type `${newProvider.ret}` is not a subtype of the old return type `${function.ret}` (${initial.origin})"
                )
              }
            case _ =>
              // add an independent mutator instead of modifying the original functoid, if no original functoid is available
              val newProvider = functoidModifier(Functoid.identityKey[t](b.key)).get
              val newRef = SingletonBinding(b.key, ImplDef.ProviderImpl(newProvider.ret, newProvider), Set.empty, b.origin, isMutator = true)
              refs = newRef :: refs
          }
        case AliasTo(key, pos) =>
          // it's ok to retrieve `tags`, `implType` & `key` from `b` because all changes to
          // `b` properties must come before first `aliasTo` operation in sorted ops set
          // when `aliased` is interpreted no more changes are going to happen
          val newRef = SingletonBinding(key, ImplDef.ReferenceImpl(b.implementation.implType, b.key, weak = false), b.tags, pos)
          refs = newRef :: refs
      }

      b :: refs.reverse
    }

    def key: DIKey.TypeKey = initial.key

    def append(op: SingletonInstruction): SingletonRef = {
      ops += op
      this
    }
  }

  final class SetRef(initial: EmptySetBinding[DIKey.TypeKey]) extends BindingRef {
    private val setOps: mutable.Queue[SetInstruction] = mutable.Queue.empty
    private val elems: mutable.Queue[SetElementRef] = mutable.Queue.empty
    private val multiElems: mutable.Queue[MultiSetElementRef] = mutable.Queue.empty

    override def interpret(): collection.Seq[Binding] = {
      val emptySetBinding = setOps.foldLeft(initial: EmptySetBinding[DIKey.BasicKey]) {
        (b, instr) =>
          instr match {
            case AddTagsAll(tags) => b.addTags(tags)
            case SetIdAll(id) => b.withTarget(DIKey.TypeKey(b.key.tpe).named(id))
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
      val elKey = DIKey.SetElementKey(setKey, implKey, SetKeyMeta.WithImpl(implDef))

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

  final class MultiSetElementRef(implDef: ImplDef, pos: SourceFilePosition) {
    private val ops: mutable.Queue[MultiSetElementInstruction] = mutable.Queue.empty

    def interpret(setKey: DIKey.BasicKey): Seq[Binding] = {
      val valueProxyKey = DIKey.IdKey(implDef.implType, MultiSetImplId(setKey, implDef))
      val valueProxyBinding = SingletonBinding(valueProxyKey, implDef, Set.empty, pos)

      val elementKey = DIKey.SetElementKey(setKey, valueProxyKey, SetKeyMeta.WithImpl(implDef))
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

  final class SubcontextRef(initial: SingletonBinding[DIKey.TypeKey], ops: mutable.Queue[SubcontextInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret(): collection.Seq[ImplBinding] = {
      require(initial.implementation.isInstanceOf[ImplDef.ContextImpl])
      var b: SingletonBinding[DIKey.BasicKey] = initial
      def bImpl(): ImplDef.ContextImpl = (b.implementation: @unchecked) match { case implDef: ImplDef.ContextImpl => implDef }
      ops.foreach {
        case SubcontextInstruction.AddTags(tags) =>
          b = b.addTags(tags)
        case SubcontextInstruction.SetId(contractedId) =>
          val key = DIKey.TypeKey(b.key.tpe).named(contractedId)
          b = b.withTarget(key)
        case SubcontextInstruction.SetExtractor(functoid) =>
          val i = bImpl()
          b = b.withImplDef(i.copy(implType = functoid.get.ret, extractingFunction = functoid.get))
        case SubcontextInstruction.AddLocalDependencies(localDependencies) =>
          val i = bImpl()
          b = b.withImplDef(i.copy(externalKeys = i.externalKeys ++ localDependencies))
        case SubcontextInstruction.AddSubmodule(submodule) =>
          val i = bImpl()
          b = b.withImplDef(i.copy(module = i.module ++ submodule))
      }
      Seq(b)
    }

    def append(op: SubcontextInstruction): SubcontextRef = {
      ops += op
      this
    }
  }

  sealed trait SingletonInstruction
  object SingletonInstruction {
    final case class SetImpl(implDef: ImplDef) extends SingletonInstruction
    final case class AddTags(tags: Set[BindingTag]) extends SingletonInstruction
    final case class SetId(id: Identifier) extends SingletonInstruction
    final case class SetIdFromImplName() extends SingletonInstruction
    final case class Modify[T](functoidModifier: Functoid[T] => Functoid[T]) extends SingletonInstruction
    final case class AliasTo(key: DIKey.BasicKey, pos: SourceFilePosition) extends SingletonInstruction
  }

  sealed trait SetInstruction
  object SetInstruction {
    final case class AddTagsAll(tags: Set[BindingTag]) extends SetInstruction
    final case class SetIdAll(id: Identifier) extends SetInstruction
  }

  sealed trait SetElementInstruction
  object SetElementInstruction {
    final case class ElementAddTags(tags: Set[BindingTag]) extends SetElementInstruction
  }

  sealed trait MultiSetElementInstruction
  object MultiSetElementInstruction {
    final case class MultiAddTags(tags: Set[BindingTag]) extends MultiSetElementInstruction
  }

  sealed trait SubcontextInstruction
  object SubcontextInstruction {
    final case class AddTags(tags: Set[BindingTag]) extends SubcontextInstruction
    final case class SetId(id: Identifier) extends SubcontextInstruction
    final case class SetExtractor[T](functoid: Functoid[T]) extends SubcontextInstruction
    final case class AddLocalDependencies(localDependencies: Iterable[DIKey]) extends SubcontextInstruction
    final case class AddSubmodule(submodule: ModuleBase) extends SubcontextInstruction
  }

}
