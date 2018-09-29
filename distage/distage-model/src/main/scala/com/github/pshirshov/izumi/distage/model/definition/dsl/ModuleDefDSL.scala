package com.github.pshirshov.izumi.distage.model.definition.dsl

import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.{AddTagsAll, SetIdAll}
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction.{AddTags, SetId, SetImpl}
import com.github.pshirshov.izumi.distage.model.definition.dsl.AbstractBindingDefDSL._
import com.github.pshirshov.izumi.distage.model.definition.{Id => _, _}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.discard
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

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
  *   - `make[X].from(myX)` = bind X to instance `myX`
  *   - `make[X].from { y: Y => new X(y) }` = bind X to an instance of X constructed by a given [[com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet Provider]] function
  *   - `make[X].named("special")` = bind a named instance of X. It can then be summoned using [[Id]] annotation.
  *
  * Multibindings:
  *   - `many[X].add[X1].add[X2]` = bind a [[Set]] of X, and add subtypes X1 and X2 created via their constructors to it.
  * Sets can be bound in multiple different modules. All the elements of the same set in different modules will be joined together.
  * `many[X].add(x1).add(x2)` = add *instances* x1 and x2 to a `Set[X]`
  *   - `many[X].add { y: Y => new X1(y).add { y: Y => X2(y) }` = add instances of X1 and X2 constructed by a given [[com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet Provider]] function
  *   - `many[X].named("special").add[X1]` = create a named set of X, all the elements of it are added to this named set.
  *   - `many[X].ref[XImpl]` = add a reference to an already **existing** binding of XImpl to a set of X's
  *   - `many[X].ref[X]("special")` = add a reference to an **existing** named binding of X to a set of X's
  *
  * Tags:
  *   - `make[X].tagged("t1", "t2)` = attach tags to X's binding. Tags can be processed in a special way. See [[com.github.pshirshov.izumi.distage.roles.roles.RoleId]]
  *   - `many[X].add[X1].tagged("x1tag")` = Tag a specific element of X. The tags of sets and their elements are separate.
  *   - `many[X].tagged("xsettag")` = Tag the binding of empty Set of X with a tag. The tags of sets and their elements are separate.
  *
  * @see [[com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK TagK]]
  * @see [[Id]]
  */
trait ModuleDefDSL
  extends AbstractBindingDefDSL with IncludesDSL with TagsDSL {
  this: ModuleBase =>

  import AbstractBindingDefDSL._

  override def bindings: Set[Binding] = freeze

  private[this] final def freeze: Set[Binding] = {
    ModuleBase.tagwiseMerge(retaggedIncludes ++ frozenState)
      .map(_.addTags(frozenTags))
      .++(asIsIncludes)
  }

  override private[definition] type BindDSL[T] = ModuleDefDSL.BindDSL[T]
  override private[definition] type SetDSL[T] = ModuleDefDSL.SetDSL[T]

  override private[definition] def _bindDSL[T: Tag](ref: SingletonRef): BindDSL[T] =
    new ModuleDefDSL.BindDSL[T](ref, ref.initial.key)

  override private[definition] def _setDSL[T: Tag](ref: SetRef): SetDSL[T] =
    new ModuleDefDSL.SetDSL[T](ref)

  /**
    * Create a dummy binding that throws an exception with an error message when it's created.
    *
    * Useful for prototyping.
    */
  final protected def todo[T: Tag](implicit pos: CodePositionMaterializer): Unit = discard {
    registered(SingletonRef(Bindings.todo(DIKey.get[T])(pos)))
  }
}


object ModuleDefDSL {

  // DSL state machine

  final class BindDSL[T]
  (
    protected val mutableState: SingletonRef
  , protected val key: DIKey.TypeKey
  ) extends BindDSLMutBase[T] {

    def named(name: String): BindNamedDSL[T] =
      addOp(SetId(name))(new BindNamedDSL[T](_, key.named(name)))

    def tagged(tags: String*): BindDSL[T] =
      addOp(AddTags(Set(tags: _*))) {
        new BindDSL[T](_, key)
      }

  }

  final class BindNamedDSL[T]
  (
    protected val mutableState: SingletonRef
  , protected val key: DIKey.IdKey[_]
  ) extends BindDSLMutBase[T] {

    def tagged(tags: String*): BindNamedDSL[T] =
      addOp(AddTags(Set(tags: _*))) {
        new BindNamedDSL[T](_, key)
      }

  }

  sealed trait BindDSLMutBase[T] extends BindDSLBase[T, Unit] {
    protected def mutableState: SingletonRef

    protected def key: DIKey

    override protected def bind(impl: ImplDef): Unit =
      addOp(SetImpl(impl))(_ => ())

    def todo(implicit pos: CodePositionMaterializer): Unit = {
      val provider = ProviderMagnet.todoProvider(key)(pos).get

      addOp(SetImpl(ImplDef.ProviderImpl(provider.ret, provider)))(_ => ())
    }

    protected def addOp[R](op: SingletonInstruction)(newState: SingletonRef => R): R = {
      mutableState.ops += op

      newState(mutableState)
    }

    //    trait Replace[A] {
    //      def apply[B, R](f: A => B)(cont: Replace[B] => R): R
    //    }
    //    object Replace {
    //      def apply[A](elem: A): Replace[A] = new Replace[A] {
    //        override def apply[B, R](f: A => B)(cont: Replace[B] => R): R =
    //          cont(Replace(f(elem)))
    //      }
    //    }
    //
    //    val v: Replace[binding.type] = Replace(binding: binding.type)
  }

  final class SetDSL[T]
  (
    protected val mutableState: SetRef
  ) extends SetDSLMutBase[T] {

    def named(name: String): SetNamedDSL[T] =
      addOp(SetIdAll(name))(new SetNamedDSL[T](_))

    /** These tags apply ONLY to EmptySet binding itself, not to set elements **/
    def tagged(tags: String*): SetDSL[T] =
      addOp(AddTagsAll(Set(tags: _*)))(new SetDSL[T](_))

  }

  final class SetNamedDSL[T]
  (
    protected val mutableState: SetRef
  ) extends SetDSLMutBase[T] {

    def tagged(tags: String*): SetNamedDSL[T] =
      addOp(AddTagsAll(Set(tags: _*)))(new SetNamedDSL[T](_))

  }

  final class SetElementDSL[T]
  (
    protected val mutableState: SetRef
    , protected val mutableCursor: SetElementRef
  ) extends SetElementDSLMutBase[T] {

    def tagged(tags: String*): SetElementDSL[T] =
      addOp(ElementAddTags(Set(tags: _*)))

  }

  sealed trait SetElementDSLMutBase[T] extends SetDSLMutBase[T] {
    protected def mutableCursor: SetElementRef

    protected def addOp(op: SetElementInstruction): SetElementDSL[T] = {
      mutableCursor.ops += op

      new SetElementDSL[T](mutableState, mutableCursor)
    }
  }

  sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T]] {
    protected def mutableState: SetRef

    protected def addOp[R](op: SetInstruction)(nextState: SetRef => R): R = {
      mutableState.setOps += op

      nextState(mutableState)
    }

    override protected def appendElement(newElement: ImplDef)(implicit pos: CodePositionMaterializer): SetElementDSL[T] = {
      val mutableCursor = SetElementRef(newElement, pos.get.position)

      mutableState.elems += mutableCursor

      new SetElementDSL[T](mutableState, mutableCursor)
    }
  }

  trait BindDSLBase[T, AfterBind] {
    final def from[I <: T : Tag]: AfterBind =
      bind(ImplDef.TypeImpl(SafeType.get[I]))

    final def from[I <: T : Tag](instance: I): AfterBind =
      bind(ImplDef.InstanceImpl(SafeType.get[I], instance))

    /**
      * A function that receives its arguments from DI context, including named instances via [[com.github.pshirshov.izumi.distage.model.definition.Id]] annotation.
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
      * Function value with annotated signature:
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
      *     case class X(i: Int @Id("special"))
      *
      *     make[X].from(X.apply _) // summons special Int
      * }}}
      *
      * Using intermediate vals` will lose annotations when converting a method into a function value,
      * prefer using annotated method directly as method reference `(method _)`:
      *
      * {{{
      *   def constructorMethod(@Id("special") i: Int): Unit = ()
      *
      *   val constructor = constructorMethod _
      *
      *   make[Unit].from(constructor) // Will summon regular Int, not a "special" Int from DI context
      * }}}
      *
      * @see [[com.github.pshirshov.izumi.distage.model.reflection.macros.ProviderMagnetMacro]]
      **/
    final def from[I <: T : Tag](f: ProviderMagnet[I]): AfterBind =
      bind(ImplDef.ProviderImpl(SafeType.get[I], f.get))

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
    final def using[I <: T : Tag]: AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false))

    final def using[I <: T : Tag](name: String): AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = false))

    protected def bind(impl: ImplDef): AfterBind
  }

  trait SetDSLBase[T, AfterAdd] {
    /**
      * Bind by reference to another bound key
      *
      * Example:
      * {{{
      *   trait T
      *
      *   make[T]
      *   make[Set[T]].ref[T1]
      * }}}
      *
      * Here, `T` will be created only once.
      * A class that depends on `Set[T]` will receive a set containing the same `T` instance
      * as in a class that depends on just `T`.
      */
    final def ref[I <: T : Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false))

    final def ref[I <: T : Tag](name: String)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = false))

    final def weak[I <: T : Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = true))

    final def weak[I <: T : Tag](name: String)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = true))

    final def add[I <: T : Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.TypeImpl(SafeType.get[I]))

    final def add[I <: T : Tag](instance: I)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.InstanceImpl(SafeType.get[I], instance))

    final def add[I <: T : Tag](f: ProviderMagnet[I])(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ProviderImpl(f.get.ret, f.get))

    protected def appendElement(newImpl: ImplDef)(implicit pos: CodePositionMaterializer): AfterAdd
  }

}





