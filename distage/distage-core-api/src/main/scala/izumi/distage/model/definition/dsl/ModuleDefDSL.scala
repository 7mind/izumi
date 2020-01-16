package izumi.distage.model.definition.dsl

import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.definition.DIResource.{DIResourceBase, ResourceTag}
import izumi.distage.model.definition._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.MultiSetElementInstruction.MultiAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.AddTagsAll
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.{SetInstruction, SingletonInstruction, _}
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, SetDSL}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks.discard
import izumi.fundamentals.reflection.Tags.{Tag, TagK}

/**
  * DSL for defining module Bindings.
  *
  * Example:
  * {{{
  * class Program[F[_]: TagK: Monad] extends ModuleDef {
  *   make[TaglessProgram[F]]
  * }
  *
  * object TryInterpreters extends ModuleDef {
  *   make[Validation.Handler[Try]].from(tryValidationHandler)
  *   make[Interaction.Handler[Try]].from(tryInteractionHandler)
  * }
  *
  * // Combine modules into a full program
  * val TryProgram = new Program[Try] ++ TryInterpreters
  * }}}
  *
  * Singleton bindings:
  *   - `make[X]` = create X using its constructor
  *   - `make[X].from[XImpl]` = bind X to its subtype XImpl using XImpl's constructor
  *   - `make[X].from(myX)` = bind X to an already existing instance `myX`
  *   - `make[X].from { y: Y => new X(y) }` = bind X to an instance of X constructed by a given [[izumi.distage.model.providers.ProviderMagnet Provider]] function
  *   - `make[X].named("special")` = bind a named instance of X. It can then be summoned using [[Id]] annotation.
  *   - `make[X].using[X]("special")` = bind X to refer to another already bound named instance at key `[X].named("special")`
  *   - `make[X].fromEffect(X.create[F]: F[X])` = create X using a purely-functional effect `X.create` in `F` monad
  *   - `make[X].fromResource(X.resource[F]: Resource[F, X])` = create X using a [[DIResource]] specifying its creation and destruction lifecycle
  *
  * Set bindings:
  *   - `many[X].add[X1].add[X2]` = bind a [[Set]] of X, and add subtypes X1 and X2 created via their constructors to it.
  *                                 Sets can be bound in multiple different modules. All the elements of the same set in different modules will be joined together.
  *   - `many[X].add(x1).add(x2)` = add *instances* x1 and x2 to a `Set[X]`
  *   - `many[X].add { y: Y => new X1(y).add { y: Y => X2(y) }` = add instances of X1 and X2 constructed by a given [[izumi.distage.model.providers.ProviderMagnet Provider]] function
  *   - `many[X].named("special").add[X1]` = create a named set of X, all the elements of it are added to this named set.
  *   - `many[X].ref[XImpl]` = add a reference to an already **existing** binding of XImpl to a set of X's
  *   - `many[X].ref[X]("special")` = add a reference to an **existing** named binding of X to a set of X's
  *
  * Tags:
  *   - `make[X].tagged("t1", "t2)` = attach tags to X's binding.
  *   - `many[X].add[X1].tagged("x1tag")` = Tag a specific element of X. Tags of a Set and its elements are separate.
  *   - `many[X].tagged("xsettag")` = Tag the binding of Set of X with a tag. Tags of a Set and its elements are separate.
  *
  * Includes:
  *   - `include(that: ModuleDef)` = add all bindings in `that` module into `this` module
  *
  * @see [[izumi.fundamentals.reflection.Tags.TagK TagK]]
  * @see [[Id]]
  * @see [[ModuleDefDSL]]
  */
trait ModuleDefDSL
  extends AbstractBindingDefDSL[MakeDSL, SetDSL]
    with IncludesDSL
    with TagsDSL { this: ModuleBase =>

  override final def bindings: Set[Binding] = freeze

  private[this] final def freeze: Set[Binding] = {
    ModuleBase.tagwiseMerge(retaggedIncludes ++ frozenState)
      .map(_.addTags(frozenTags))
      .++(asIsIncludes)
  }

  override private[definition] final def _bindDSL[T](ref: SingletonRef): MakeDSL[T] =
    new MakeDSL[T](ref, ref.key)

  override private[definition] final def _setDSL[T](ref: SetRef): SetDSL[T] =
    new SetDSL[T](ref)

  /**
    * Create a dummy binding that throws an exception with an error message when it's created.
    *
    * Useful for prototyping.
    */
  final protected def todo[T: Tag](implicit pos: CodePositionMaterializer): Unit = discard {
    _registered(new SingletonRef(Bindings.todo(DIKey.get[T])(pos)))
  }
}

object ModuleDefDSL {

  trait MakeDSLBase[T, AfterBind] {
    final def from[I <: T: Tag: AnyConstructor]: AfterBind =
      from(AnyConstructor[I].provider)

    final def from[I <: T: Tag](instance: => I): AfterBind =
      from(ProviderMagnet.lift(instance))

    final def fromValue[I <: T: Tag](instance: I): AfterBind =
      bind(ImplDef.InstanceImpl(SafeType.get[I], instance))

    /**
      * A function that receives its arguments from DI object graph, including named instances via [[izumi.distage.model.definition.Id]] annotation.
      *
      * The following syntaxes are supported by extractor macro:
      *
      * Inline lambda:
      *
      * {{{
      *   make[Unit].from {
      *     i: Int @Id("special") => ()
      *   }
      * }}}
      *
      * Method reference:
      * {{{
      *   def constructor(@Id("special") i: Int): Unit = ()
      *
      *   make[Unit].from(constructor _)
      *
      *   make[Unit].from(constructor(_))
      * }}}
      *
      * Function value (possibly with annotated signature):
      * {{{
      *   val constructor: (Int @Id("special"), String @Id("special")) => Unit = (_, _) => ()
      *
      *   make[Unit].from(constructor)
      * }}}
      *
      *
      * Annotation processing is done by a macro and macros are rarely perfect,
      * Prefer passing an inline lambda such as { x => y } or a method reference such as (method _) or (method(_))
      * Annotation info may be lost ONLY in a few cases detailed below, though:
      *  - If an annotated method has been hidden by an intermediate `val`
      *  - If an `.apply` method of a case class is passed when case class _parameters_ are annotated, not their types
      *
      * As such, prefer annotating parameter types, not parameters: `class X(i: Int @Id("special")) { ... }`
      *
      * When binding a case class to constructor, prefer passing `new X(_)` instead of `X.apply _` because `apply` will
      * not preserve parameter annotations from case class definitions:
      *
      * {{{
      *   case class X(@Id("special") i: Int)
      *
      *   make[X].from(X.apply _) // summons regular Int
      *   make[X].from(new X(_)) // summons special Int
      * }}}
      *
      * HOWEVER, if you annotate the types of parameters instead of their names, `apply` WILL work:
      *
      * {{{
      *   case class X(i: Int @Id("special"))
      *
      *   make[X].from(X.apply _) // summons special Int
      * }}}
      *
      * Using intermediate vals will lose annotations when converting a method into a function value,
      * prefer using annotated method directly as method reference `(method _)`:
      *
      * {{{
      *   def constructorMethod(@Id("special") i: Int): Unit = ()
      *
      *   val constructor = constructorMethod _
      *
      *   make[Unit].from(constructor) // Will summon regular Int, not a "special" Int from DI object graph
      * }}}
      *
      * @see [[izumi.distage.model.reflection.macros.ProviderMagnetMacro]]
      */
    final def from[I <: T: Tag](function: ProviderMagnet[I]): AfterBind =
      bind(ImplDef.ProviderImpl(SafeType.get[I], function.get))

    /**
      * Bind by reference to another bound key
      *
      * Example:
      * {{{
      *   trait T
      *   class T1 extends T
      *
      *   make[T1]
      *   make[T].using[T1]
      * }}}
      *
      * Here, only T1 will be created.
      * A class that depends on `T` will receive an instance of T1
      */
    final def using[I <: T: Tag]: AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false))

    final def using[I <: T: Tag](name: String): AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = false))

    /**
      * Bind to a result of executing a purely-functional effect
      *
      * Example:
      * {{{
      *   import cats.effect.concurrent.Ref
      *   import cats.effect.IO
      *
      *   make[Ref[IO, Int]].named("globalMutableCounter").fromEffect(Ref[IO](0))
      * }}}
      */
    final def fromEffect[F[_]: TagK, I <: T: Tag](instance: F[I]): AfterBind =
      bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[F[I]], instance)))

    final def fromEffect[F[_]: TagK, I <: T: Tag](function: ProviderMagnet[F[I]]): AfterBind =
      bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[F[I]], function.get)))

    /**
      * Bind to result of executing an effect bound to a key at `F[I]`
      *
      * This will execute the effect again for every `refEffect` binding
      *
      * Example:
      * {{{
      *   import cats.effect.concurrent.Ref
      *   import cats.effect.IO
      *
      *   make[IO[Ref[IO, Int]]].named("counterFactory").from(Ref[IO](0))
      *
      *   // execute the effect bound above to key `DIKey.get[IO[Ref[IO, Int]]].named("counterFactory")` to create and bind a new Ref
      *   make[Ref[IO, Int]].named("globalCounter1")
      *     .refEffect[IO, Ref[IO, Int]]("counterFactory")
      *
      *   make[Ref[IO, Int]].named("globalCounter2")
      *     .refEffect[IO, Ref[IO, Int]]("counterFactory")
      *
      *   // globalCounter1 and globalCounter2 are two independent mutable references
      * }}}
      */
    final def refEffect[F[_]: TagK, I <: T: Tag]: AfterBind =
      refEffect[F, I, F[I]]

    final def refEffect[F[_]: TagK, I <: T: Tag](name: String): AfterBind =
      refEffect[F, I, F[I]](name)

    final def refEffect[F[_]: TagK, I <: T: Tag, EFF <: F[I]: Tag]: AfterBind =
      bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[EFF], DIKey.get[EFF], weak = false)))

    final def refEffect[F[_]: TagK, I <: T: Tag, EFF <: F[I]: Tag](name: String): AfterBind =
      bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[EFF], DIKey.get[EFF].named(name), weak = false)))

    /**
      * Bind to result of acquiring a resource
      *
      * The resource will be released when the [[izumi.distage.model.Locator]]
      * holding it is released. Typically, after `.use` is called on the result of
      * [[izumi.distage.model.Producer.produceF]]
      *
      * You can create resources with [[DIResource.make]], by inheriting from [[DIResource]]
      * or by converting an existing [[cats.effect.Resource]]
      *
      * You can bind a [[cats.effect.Resource]] directly:
      *
      * {{{
      *   import cats.effect._
      *
      *   val myResource: Resource[IO, Unit] = Resource.make(IO(println("Acquiring!")))(IO(println("Releasing!")))
      *
      *   make[Unit].from(myResource)
      * }}}
      *
      * @see - [[cats.effect.Resource]]: https://typelevel.org/cats-effect/datatypes/resource.html
      *      - [[DIResource]]
      */
    final def fromResource[R <: DIResourceBase[Any, T]: AnyConstructor](implicit tag: ResourceTag[R]): AfterBind = {
      fromResource[R](AnyConstructor[R].provider)
    }

    final def fromResource[R](instance: R with DIResourceBase[Any, T])(implicit tag: ResourceTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[R], instance)))
    }

    final def fromResource[R](function: ProviderMagnet[R with DIResourceBase[Any, T]])(implicit tag: ResourceTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], function.get)))
    }

    final def fromResource[R0, R <: DIResourceBase[Any, T]](function: ProviderMagnet[R0])(implicit adapt: DIResource.AdaptProvider.Aux[R0, R], tag: ResourceTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], adapt(function).get)))
    }

    /**
      * Bind to a result of acquiring a resource bound to a key at `R`
      *
      * This will acquire a NEW resource again for every `refResource` binding
      */
    final def refResource[R <: DIResourceBase[Any, T]](implicit tag: ResourceTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R], weak = false)))
    }

    final def refResource[R <: DIResourceBase[Any, T]](name: String)(implicit tag: ResourceTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R].named(name), weak = false)))
    }

    protected[this] def bind(impl: ImplDef): AfterBind
  }

  trait SetDSLBase[T, AfterAdd, AfterMultiAdd] {

    final def add[I <: T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): AfterAdd =
      add[I](AnyConstructor[I].provider)

    final def add[I <: T: Tag](instance: => I)(implicit pos: CodePositionMaterializer): AfterAdd =
      add(ProviderMagnet.lift(instance))

    final def addValue[I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.InstanceImpl(SafeType.get[I], instance), pos)

    final def add[I <: T: Tag](function: ProviderMagnet[I])(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ProviderImpl(function.get.ret, function.get), pos)

    /**
      * Bind by reference to another bound key
      *
      * Example:
      * {{{
      *   trait T
      *   trait T1 extends T
      *
      *   make[T1]
      *   many[T].ref[T1]
      * }}}
      *
      * Here, `T1` will be created only once.
      * A class that depends on `Set[T]` will receive a Set containing the same `T1` instance
      * as a class that depends on just a `T1`.
      */
    final def ref[I <: T: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false), pos)

    final def ref[I <: T: Tag](name: String)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = false), pos)

    final def weak[I <: T: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = true), pos)

    final def weak[I <: T: Tag](name: String)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = true), pos)

    /**
      * Add multiple values into this set at once
      *
      * Example:
      * {{{
      *   class T
      *
      *   many[T].addSet(Set(new T, new T, new T))
      * }}}
      **/
    final def addSet[I <: Set[_ <: T]: Tag](instance: => I)(implicit pos: CodePositionMaterializer): AfterMultiAdd =
      addSet(ProviderMagnet.lift(instance))

    final def addSetValue[I <: Set[_ <: T]: Tag](instance: I)(implicit pos: CodePositionMaterializer): AfterMultiAdd =
      multiSetAdd(ImplDef.InstanceImpl(SafeType.get[I], instance), pos)

    final def addSet[I <: Set[_ <: T]: Tag](function: ProviderMagnet[I])(implicit pos: CodePositionMaterializer): AfterMultiAdd =
      multiSetAdd(ImplDef.ProviderImpl(function.get.ret, function.get), pos)

    final def refSet[I <: Set[_ <: T]: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false), pos)

    final def refSet[I <: Set[_ <: T]: Tag](name: String)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = false), pos)

    final def weakSet[I <: Set[_ <: T]: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = true), pos)

    final def weakSet[I <: Set[_ <: T]: Tag](name: String)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = true), pos)

    final def addEffect[F[_]: TagK, I <: T: Tag](instance: F[I])(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[F[I]], instance)), pos)

    final def addEffect[F[_]: TagK, I <: T: Tag](function: ProviderMagnet[F[I]])(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[F[I]], function.get)), pos)

    final def refEffect[F[_]: TagK, I <: T: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[F[I]], DIKey.get[F[I]], weak = false)), pos)

    final def refEffect[F[_]: TagK, I <: T: Tag](name: String)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[F[I]], DIKey.get[F[I]].named(name), weak = false)), pos)

    final def addResource[R <: DIResourceBase[Any, T]: AnyConstructor](implicit tag: ResourceTag[R], pos: CodePositionMaterializer): AfterAdd =
      addResource[R](AnyConstructor[R].provider)

    final def addResource[R](instance: R with DIResourceBase[Any, T])(implicit tag: ResourceTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[R], instance)), pos)
    }

    final def addResource[R](function: ProviderMagnet[R with DIResourceBase[Any, T]])(implicit tag: ResourceTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], function.get)), pos)
    }

    final def addResource[R0, R <: DIResourceBase[Any, T]](function: ProviderMagnet[R0])(implicit adapt: DIResource.AdaptProvider.Aux[R0, R], tag: ResourceTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], adapt(function).get)), pos)
    }

    final def refResource[R <: DIResourceBase[Any, T]](implicit tag: ResourceTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R], weak = false)), pos)
    }

    final def refResource[R <: DIResourceBase[Any, T]](name: String)(implicit tag: ResourceTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R].named(name), weak = false)), pos)
    }

    protected[this] def multiSetAdd(newImpl: ImplDef, pos: CodePositionMaterializer): AfterMultiAdd
    protected[this] def appendElement(newImpl: ImplDef, pos: CodePositionMaterializer): AfterAdd
  }

  // DSL state machine

  /** These are the _only_ methods that can chained after `make`
    * such that make[T] will still generate the constructor for `T`
    *
    * See [[izumi.distage.constructors.macros.AnyConstructorMacro.anyConstructorOptionalMakeDSL]]
    *
    * If ANY other method is chained in the same expression
    * it's assumed that it will replace make[T]'s constructor,
    * so the constructor for `T` will NOT be generated.
    *
    * Please update this when adding new methods to [[MakeDSL]]!
    */
  private[distage] final lazy val MakeDSLNoOpMethodsWhitelist = Set("named", "namedByImpl", "tagged", "aliasTo")

  final class MakeDSL[T]
  (
    override protected val mutableState: SingletonRef,
    override protected val key: DIKey.TypeKey,
  ) extends MakeDSLMutBindBase[T] {

    def named[I](name: I)(implicit idContract: IdContract[I]): MakeNamedDSL[T] = {
      addOp(SetId(name, idContract))(new MakeNamedDSL[T](_, key.named(name)))
    }

    def namedByImpl: MakeNamedDSL[T] = {
      addOp(SetIdFromImplName())(new MakeNamedDSL[T](_, key.named(key.toString)))
    }

    def tagged(tags: BindingTag*): MakeDSL[T] = {
      addOp(AddTags(tags.toSet)) {
        new MakeDSL[T](_, key)
      }
    }

  }

  final class MakeNamedDSL[T]
  (
    override protected val mutableState: SingletonRef,
    override protected val key: DIKey.IdKey[_],
  ) extends MakeDSLMutBindBase[T] {

    def tagged(tags: BindingTag*): MakeNamedDSL[T] = {
      addOp(AddTags(tags.toSet)) {
        new MakeNamedDSL[T](_, key)
      }
    }

  }

  final class MakeDSLAfterFrom[I]
  (
    override protected val mutableState: SingletonRef,
    override protected val key: DIKey,
  ) extends MakeDSLMutBase[I]

  sealed trait MakeDSLMutBindBase[T] extends MakeDSLBase[T, MakeDSLAfterFrom[T]] with MakeDSLMutBase[T] {
    final def todo(implicit pos: CodePositionMaterializer): MakeDSLAfterFrom[T] = {
      val provider = ProviderMagnet.todoProvider(key)(pos).get
      addOp(SetImpl(ImplDef.ProviderImpl(provider.ret, provider)))(new MakeDSLAfterFrom[T](_, key))
    }

    protected[this] final def bind(impl: ImplDef): MakeDSLAfterFrom[T] =
      addOp(SetImpl(impl))(new MakeDSLAfterFrom[T](_, key))
  }

  sealed trait MakeDSLMutBase[T] {
    protected[this] def mutableState: SingletonRef
    protected[this] def key: DIKey

    def aliasTo[T1 >: T: Tag](implicit pos: CodePositionMaterializer): MakeDSLAfterFrom[T] = {
      addOp(AliasTo(DIKey.get[T1], pos.get.position))(new MakeDSLAfterFrom[T](_, key))
    }

    def aliasTo[T1 >: T: Tag](name: String)(implicit pos: CodePositionMaterializer): MakeDSLAfterFrom[T] = {
      addOp(AliasTo(DIKey.get[T1].named(name), pos.get.position))(new MakeDSLAfterFrom[T](_, key))
    }

    protected[this] final def addOp[R](op: SingletonInstruction)(newState: SingletonRef => R): R = {
      newState(mutableState.append(op))
    }
  }

  final class SetDSL[T]
  (
    protected val mutableState: SetRef,
  ) extends SetDSLMutBase[T] {

    def named[I](name: I)(implicit idContract: IdContract[I]): SetNamedDSL[T] = {
      addOp(SetInstruction.SetIdAll(name, idContract))(new SetNamedDSL[T](_))
    }

    /** These tags apply ONLY to EmptySet binding itself, not to set elements **/
    def tagged(tags: BindingTag*): SetDSL[T] = {
      addOp(AddTagsAll(tags.toSet))(new SetDSL[T](_))
    }
  }

  final class SetNamedDSL[T]
  (
    override protected val mutableState: SetRef,
  ) extends SetDSLMutBase[T] {

    def tagged(tags: BindingTag*): SetNamedDSL[T] = {
      addOp(AddTagsAll(tags.toSet))(new SetNamedDSL[T](_))
    }
  }

  final class SetElementDSL[T]
  (
    override protected val mutableState: SetRef,
    mutableCursor: SetElementRef,
  ) extends SetDSLMutBase[T] {

    def tagged(tags: BindingTag*): SetElementDSL[T] = {
      addOp(ElementAddTags(tags.toSet))
    }

    private[this] def addOp(op: SetElementInstruction): SetElementDSL[T] = {
      val newState = mutableCursor.append(op)
      new SetElementDSL[T](mutableState, newState)
    }
  }

  final class MultiSetElementDSL[T]
  (
    override protected val mutableState: SetRef,
    mutableCursor: MultiSetElementRef,
  ) extends SetDSLMutBase[T] {

    def tagged(tags: BindingTag*): MultiSetElementDSL[T] =
      addOp(MultiAddTags(tags.toSet))

    private[this] def addOp(op: MultiSetElementInstruction): MultiSetElementDSL[T] = {
      val newState = mutableCursor.append(op)
      new MultiSetElementDSL[T](mutableState, newState)
    }
  }

  sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T], MultiSetElementDSL[T]] {
    protected[this] def mutableState: SetRef

    protected[this] final def addOp[R](op: SetInstruction)(nextState: SetRef => R): R = {
      nextState(mutableState.appendOp(op))
    }

    override protected[this] final def appendElement(newElement: ImplDef, pos: CodePositionMaterializer): SetElementDSL[T] = {
      val mutableCursor = new SetElementRef(newElement, pos.get.position)
      new SetElementDSL[T](mutableState.appendElem(mutableCursor), mutableCursor)
    }

    override protected[this] final def multiSetAdd(newElements: ImplDef, pos: CodePositionMaterializer): MultiSetElementDSL[T] = {
      val mutableCursor = new MultiSetElementRef(newElements, pos.get.position)
      new MultiSetElementDSL[T](mutableState.appendMultiElem(mutableCursor), mutableCursor)
    }
  }

}
