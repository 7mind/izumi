package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
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
  extends AbstractModuleDefDSL with IncludesDSL with TagsDSL {
  this: ModuleBase =>

  import AbstractModuleDefDSL._

  override def bindings: Set[Binding] = freeze

  protected[definition] final def freeze: Set[Binding] = {
    ModuleBase.tagwiseMerge(frozenState ++ asIsIncludes)
      .map(_.addTags(frozenTags))
      .++(retaggedIncludes)
  }

  /**
    * Create a dummy binding that throws an exception with an error message when it's created.
    *
    * Useful for prototyping.
    */
  final protected def todo[T: Tag](implicit pos: CodePositionMaterializer): Unit = discard {
    val binding = Bindings.todo(DIKey.get[T])(pos)

    mutableState += SingletonRef(binding)
  }
}


object ModuleDefDSL {

  import AbstractModuleDefDSL._


  // DSL state machine

  final class BindDSL[T]
  (
    protected val mutableState: SingletonRef
    , protected val binding: SingletonBinding[DIKey.TypeKey]
  ) extends BindDSLMutBase[T] {

    def named(name: String): BindNamedDSL[T] =
      replace(binding.copy(key = binding.key.named(name), tags = binding.tags)) {
        new BindNamedDSL[T](mutableState, _)
      }

    def tagged(tags: String*): BindDSL[T] =
      replace(binding.copy(tags = binding.tags ++ tags)) {
        new BindDSL[T](mutableState, _)
      }

    def todo(implicit pos: CodePositionMaterializer): Unit =
      replace(Bindings.todo(binding.key)(pos))(_ => ())

  }

  final class BindNamedDSL[T]
  (
    protected val mutableState: SingletonRef
    , protected val binding: Binding.SingletonBinding[DIKey]
  ) extends BindDSLMutBase[T] {

    def tagged(tags: String*): BindNamedDSL[T] =
      replace(binding.copy(tags = binding.tags ++ tags)) {
        new BindNamedDSL[T](mutableState, _)
      }

    def todo(implicit pos: CodePositionMaterializer): Unit =
      replace(Bindings.todo(binding.key)(pos))(_ => ())
  }

  sealed trait BindDSLMutBase[T] extends BindDSLBase[T, Unit] {
    protected def mutableState: SingletonRef

    protected def binding: SingletonBinding[DIKey]

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

    protected def replace[B <: Binding, S](newBinding: B)(newState: B => S): S = {
      mutableState.ref = newBinding
      newState(newBinding)
    }

    override protected def bind(impl: ImplDef): Unit =
      replace(binding.withImpl(impl))(_ => ())
  }

  final case class IdentSet[+D <: DIKey](key: D, tags: Set[String], pos: SourceFilePosition) {
    def sameIdent(binding: Binding): Boolean =
      key == binding.key && tags == binding.tags
  }

  final class SetDSL[T]
  (
    protected val mutableState: SetRef
    , protected val identifier: IdentSet[DIKey.TypeKey]
  ) extends SetDSLMutBase[T] {

    def named(name: String): SetNamedDSL[T] =
      replaceIdent(identifier.copy(key = identifier.key.named(name))) {
        new SetNamedDSL(mutableState, _)
      }

    /** tags only apply to EmptySet itself **/
    def tagged(tags: String*): SetDSL[T] =
      replaceIdent(identifier.copy(tags = identifier.tags ++ tags)) {
        new SetDSL[T](mutableState, _)
      }

  }

  final class SetNamedDSL[T]
  (
    protected val mutableState: SetRef
    , protected val identifier: IdentSet[DIKey]
  ) extends SetDSLMutBase[T] {

    def tagged(tags: String*): SetNamedDSL[T] =
      replaceIdent(identifier.copy(tags = identifier.tags ++ tags)) {
        new SetNamedDSL[T](mutableState, _)
      }

  }

  final class SetElementDSL[T]
  (
    protected val mutableState: SetRef
    , protected val mutableCursor: SingletonRef
    , protected val identifier: IdentSet[DIKey]
  ) extends SetElementDSLMutBase[T] {

    def tagged(tags: String*): SetElementDSL[T] =
      replaceCursor(mutableCursor.ref.addTags(Set(tags: _*)))

  }

  sealed trait SetElementDSLMutBase[T] extends SetDSLMutBase[T] {
    protected def mutableCursor: SingletonRef

    protected def replaceCursor(newBindingCursor: Binding): SetElementDSL[T] = {
      mutableCursor.ref = newBindingCursor

      new SetElementDSL[T](mutableState, mutableCursor, identifier)
    }
  }

  sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T]] {
    protected def mutableState: SetRef

    protected def identifier: IdentSet[DIKey]

    protected def replaceIdent[D <: IdentSet[DIKey], S](newIdent: D)(nextState: D => S): S = {
      mutableState.emptySetBinding.ref = EmptySetBinding(newIdent.key, newIdent.tags, newIdent.pos)
      mutableState.all.foreach(r => r.ref = r.ref.withTarget(newIdent.key))

      nextState(newIdent)
    }

    override protected def appendElement(newElement: ImplDef)(implicit pos: CodePositionMaterializer): SetElementDSL[T] = {
      val newBinding: Binding = SetElementBinding(identifier.key, newElement)
      val mutableCursor = SingletonRef(newBinding)

      mutableState.all += mutableCursor

      new SetElementDSL[T](mutableState, mutableCursor, identifier)
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





