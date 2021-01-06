package izumi.distage.model.definition.dsl

import izumi.distage.constructors.{AnyConstructor, HasConstructor}
import izumi.distage.model.definition.Lifecycle.{LifecycleTag, TrifunctorHasLifecycleTag}
import izumi.distage.model.definition._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.MultiSetElementInstruction.MultiAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.{SetInstruction, SingletonInstruction, _}
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, MakeDSLUnnamedAfterFrom, SetDSL}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.functional.bio.Local3
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.reflect.{Tag, TagK, TagK3}
import zio._

import scala.collection.immutable.HashSet

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
  *   - `make[X].from { y: Y => new X(y) }` = bind X to an instance of X constructed by a given [[izumi.distage.model.providers.Functoid Functoid]] requesting an Y parameter
  *   - `make[X].from { y: Y @Id("special") => new X(y) }` = bind X to an instance of X constructed by a given [[izumi.distage.model.providers.Functoid Functoid]], requesting a named "special" Y parameter
  *   - `make[X].from { y: Y => new X(y) }`.annotateParameter[Y]("special") = bind X to an instance of X constructed by a given [[izumi.distage.model.providers.Functoid Functoid]], requesting a named "special" Y parameter
  *   - `make[X].named("special")` = bind a named instance of X. It can then be summoned using [[Id]] annotation.
  *   - `make[X].using[X]("special")` = bind X to refer to another already bound named instance at key `[X].named("special")`
  *   - `make[X].fromEffect(X.create[F]: F[X])` = create X using a purely-functional effect `X.create` in `F` monad
  *   - `make[X].fromResource(X.resource[F]: Lifecycle[F, X])` = create X using a `Lifecycle` value specifying its creation and destruction lifecycle
  *   - `make[X].from[XImpl].modify(fun(_))` = Create X using XImpl's constructor and apply `fun` to the result
  *   - `make[X].from[XImpl].modifyBy(_.flatAp { (c: C, d: D) => (x: X) => c.method(x, d) })` = Create X using XImpl's constructor and modify its `Functoid` using the provided lambda - in this case by summoning additional `C` & `D` dependencies and applying `C.method` to `X`
  *
  * Set bindings:
  *   - `many[X].add[X1].add[X2]` = bind a `Set` of X, and add subtypes X1 and X2 created via their constructors to it.
  *                                 Sets can be bound in multiple different modules. All the elements of the same set in different modules will be joined together.
  *   - `many[X].add(x1).add(x2)` = add *instances* x1 and x2 to a `Set[X]`
  *   - `many[X].add { y: Y => new X1(y).add { y: Y => X2(y) }` = add instances of X1 and X2 constructed by a given [[izumi.distage.model.providers.Functoid Provider]] function
  *   - `many[X].named("special").add[X1]` = create a named set of X, all the elements of it are added to this named set.
  *   - `many[X].ref[XImpl]` = add a reference to an already **existing** binding of XImpl to a set of X's
  *   - `many[X].ref[X]("special")` = add a reference to an **existing** named binding of X to a set of X's
  *
  * Mutators:
  *   - `modify[X](fun(_))` = add a modifier applying `fun` to the value bound at `X` (mutator application order is unspecified)
  *   - `modify[X].by(_.flatAp { (c: C, d: D) => (x: X) => c.method(x, d) })` = add a modifier, applying the provided lambda to a `Functoid` retrieving `X` - in this case by summoning additional `C` & `D` dependencies and applying `C.method` to `X`
  *
  * Tags:
  *   - `make[X].tagged("t1", "t2)` = attach tags to X's binding.
  *   - `many[X].add[X1].tagged("x1tag")` = Tag a specific element of X. The tags of sets and their elements are separate.
  *   - `many[X].tagged("xsettag")` = Tag the binding of empty Set of X with a tag. The tags of sets and their elements are separate.
  *
  * Includes:
  *   - `include(that: ModuleDef)` = add all bindings in `that` module into `this` module
  *
  * @see [[izumi.reflect.TagK TagK]]
  * @see [[Id]]
  * @see [[ModuleDefDSL]]
  */
trait ModuleDefDSL extends AbstractBindingDefDSL[MakeDSL, MakeDSLUnnamedAfterFrom, SetDSL] with IncludesDSL with TagsDSL { this: ModuleBase =>
  override final def bindings: Set[Binding] = freeze()
  override final def iterator: Iterator[Binding] = freezeIterator()
  override final def keysIterator: Iterator[DIKey] = freezeIterator().map(_.key)

  private[this] final def freeze(): Set[Binding] = {
    HashSet.newBuilder
      .++= {
        freezeIterator()
      }.result()
  }
  private[this] final def freezeIterator(): Iterator[Binding] = {
    val frozenTags0 = frozenTags
    retaggedIncludes
      .++(frozenState)
      .map(_.addTags(frozenTags0))
      .++(asIsIncludes)
  }

  override private[definition] final def _bindDSL[T](ref: SingletonRef): MakeDSL[T] = new MakeDSL[T](ref, ref.key)
  override private[definition] final def _bindDSLAfterFrom[T](ref: SingletonRef): MakeDSLUnnamedAfterFrom[T] = new MakeDSLUnnamedAfterFrom[T](ref, ref.key)
  override private[definition] final def _setDSL[T](ref: SetRef): SetDSL[T] = new SetDSL[T](ref)

  /**
    * Create a dummy binding that throws an exception with an error message when it's created.
    *
    * Useful for prototyping.
    */
  final protected[this] def todo[T: Tag](implicit pos: CodePositionMaterializer): Unit = {
    _registered(new SingletonRef(Bindings.todo(DIKey.get[T])(pos))).discard()
  }
}

object ModuleDefDSL {

  trait MakeDSLBase[T, AfterBind] {
    final def from[I <: T: AnyConstructor]: AfterBind =
      from(AnyConstructor[I])

    final def from[I <: T: Tag](function: => I): AfterBind =
      from(Functoid.lift(function))

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
      *
      * {{{
      *   def constructor(@Id("special") i: Int): Unit = ()
      *
      *   make[Unit].from(constructor _)
      *
      *   make[Unit].from(constructor(_))
      * }}}
      *
      * Function value with an annotated signature:
      *
      * {{{
      *   val constructor: (Int @Id("special"), String @Id("special")) => Unit = (_, _) => ()
      *
      *   make[Unit].from(constructor)
      * }}}
      *
      * Using intermediate vals will lose annotations when converting a method into a function value,
      * Prefer passing inline lambdas such as `{ x => y }` or method references such as `(method _)` or `(method(_))`.:
      *
      * {{{
      *   def constructorMethod(@Id("special") i: Int): Unit = ()
      *
      *   val constructor = constructorMethod _
      *
      *   make[Unit].from(constructor) // SURPRISE: Will summon regular Int, not a "special" Int from DI object graph
      *   make[Unit].from(constructorMethod _) // Will work correctly: summon "special" Int
      * }}}
      *
      * Prefer annotating parameter types, not parameters: `class X(i: Int @Id("special")) { ... }`
      *
      * {{{
      *   case class X(i: Int @Id("special"))
      *
      *   make[X].from(X.apply _) // summons special Int
      * }}}
      *
      * Functoid forms an applicative functor via its  [[izumi.distage.model.providers.Functoid.pure]] & [[izumi.distage.model.providers.Functoid#map2]] methods
      *
      * @see [[izumi.distage.model.reflection.macros.FunctoidMacro]]]
      * @see Functoid is based on the Magnet Pattern: [[http://spray.io/blog/2012-12-13-the-magnet-pattern/]]
      * @see Essentially Functoid is a function-like entity with additional properties, so it's funny name is reasonable enough: [[https://en.wiktionary.org/wiki/-oid#English]]
      */
    final def from[I <: T](function: Functoid[I]): AfterBind =
      bind(ImplDef.ProviderImpl(function.get.ret, function.get))

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

    final def using[I <: T: Tag](name: Identifier): AfterBind =
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

    final def fromEffect[F[_]: TagK, I <: T: Tag](function: Functoid[F[I]]): AfterBind =
      bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ProviderImpl(function.get.ret, function.get)))

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

    final def refEffect[F[_]: TagK, I <: T: Tag](name: Identifier): AfterBind =
      refEffect[F, I, F[I]](name)

    final def refEffect[F[_]: TagK, I <: T: Tag, EFF <: F[I]: Tag]: AfterBind =
      bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[EFF], DIKey.get[EFF], weak = false)))

    final def refEffect[F[_]: TagK, I <: T: Tag, EFF <: F[I]: Tag](name: Identifier): AfterBind =
      bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[EFF], DIKey.get[EFF].named(name), weak = false)))

    /**
      * Bind to result of acquiring a resource
      *
      * The resource will be released when the [[izumi.distage.model.Locator]]
      * holding it is released. Typically, after `.use` is called on the result of
      * [[izumi.distage.model.Injector#produce]]
      *
      * You can create resources with [[Lifecycle.make]], by inheriting from [[Lifecycle]]
      * or by converting an existing [[cats.effect.Resource]]
      *
      * You can bind a [[cats.effect.Resource]] directly:
      *
      * {{{
      *   import cats.effect._
      *
      *   val myResource: Resource[IO, Unit] = Resource.make(IO(println("Acquiring!")))(IO(println("Releasing!")))
      *
      *   make[Unit].fromResource(myResource)
      * }}}
      *
      * @see - [[cats.effect.Resource]]: https://typelevel.org/cats-effect/datatypes/resource.html
      *      - [[Lifecycle]]
      */
    final def fromResource[R <: Lifecycle[Any, T]: AnyConstructor](implicit tag: LifecycleTag[R]): AfterBind = {
      fromResource(AnyConstructor[R])
    }

    final def fromResource[R](instance: R with Lifecycle[Any, T])(implicit tag: LifecycleTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[R], instance)))
    }

    final def fromResource[R](function: Functoid[R with Lifecycle[Any, T]])(implicit tag: LifecycleTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], function.get)))
    }

    final def fromResource[R0, R <: Lifecycle[Any, T]](
      function: Functoid[R0]
    )(implicit adapt: Lifecycle.AdaptFunctoid.Aux[R0, R],
      tag: LifecycleTag[R],
    ): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], adapt(function).get)))
    }

    /**
      * Bind to a result of acquiring a resource bound to a key at `R`
      *
      * This will acquire a NEW resource again for every `refResource` binding
      */
    final def refResource[R <: Lifecycle[Any, T]](implicit tag: LifecycleTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R], weak = false)))
    }

    final def refResource[R <: Lifecycle[Any, T]](name: Identifier)(implicit tag: LifecycleTag[R]): AfterBind = {
      import tag._
      bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R].named(name), weak = false)))
    }

    /**
      * Create a dummy binding that throws an exception with an error message when it's created.
      *
      * Useful for prototyping.
      */
    def todo(implicit pos: CodePositionMaterializer): AfterBind = {
      val provider = Functoid.todoProvider(key)(pos).get
      bind(ImplDef.ProviderImpl(provider.ret, provider))
    }

    protected[this] def bind(impl: ImplDef): AfterBind
    protected[this] def key: DIKey
  }

  trait SetDSLBase[T, AfterAdd, AfterMultiAdd] {

    final def add[I <: T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): AfterAdd =
      add[I](AnyConstructor[I])

    final def add[I <: T: Tag](function: => I)(implicit pos: CodePositionMaterializer): AfterAdd =
      add(Functoid.lift(function))

    final def add[I <: T](function: Functoid[I])(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ProviderImpl(function.get.ret, function.get), pos)

    final def addValue[I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.InstanceImpl(SafeType.get[I], instance), pos)

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

    final def ref[I <: T: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = false), pos)

    final def weak[I <: T: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = true), pos)

    final def weak[I <: T: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = true), pos)

    final def addEffect[F[_]: TagK, I <: T: Tag](instance: F[I])(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[F[I]], instance)), pos)

    final def addEffect[F[_]: TagK, I <: T: Tag](function: Functoid[F[I]])(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ProviderImpl(function.get.ret, function.get)), pos)

    final def refEffect[F[_]: TagK, I <: T: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[F[I]], DIKey.get[F[I]], weak = false)), pos)

    final def refEffect[F[_]: TagK, I <: T: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[F[I]], DIKey.get[F[I]].named(name), weak = false)), pos)

    final def addResource[R <: Lifecycle[Any, T]: AnyConstructor](implicit tag: LifecycleTag[R], pos: CodePositionMaterializer): AfterAdd =
      addResource[R](AnyConstructor[R])

    final def addResource[R](instance: R with Lifecycle[Any, T])(implicit tag: LifecycleTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.InstanceImpl(SafeType.get[R], instance)), pos)
    }

    final def addResource[R](function: Functoid[R with Lifecycle[Any, T]])(implicit tag: LifecycleTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], function.get)), pos)
    }

    final def addResource[R0, R <: Lifecycle[Any, T]](
      function: Functoid[R0]
    )(implicit adapt: Lifecycle.AdaptFunctoid.Aux[R0, R],
      tag: LifecycleTag[R],
      pos: CodePositionMaterializer,
    ): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], adapt(function).get)), pos)
    }

    final def refResource[R <: Lifecycle[Any, T]](implicit tag: LifecycleTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R], weak = false)), pos)
    }

    final def refResource[R <: Lifecycle[Any, T]](name: Identifier)(implicit tag: LifecycleTag[R], pos: CodePositionMaterializer): AfterAdd = {
      import tag._
      appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ReferenceImpl(SafeType.get[R], DIKey.get[R].named(name), weak = false)), pos)
    }

    /**
      * Add multiple values into this set at once
      *
      * Example:
      * {{{
      *   class T
      *
      *   many[T].addSet(Set(new T, new T, new T))
      * }}}
      */
    final def addSet[I <: Set[_ <: T]: Tag](function: => I)(implicit pos: CodePositionMaterializer): AfterMultiAdd =
      addSet(Functoid.lift(function))

    final def addSet[I <: Set[_ <: T]](function: Functoid[I])(implicit pos: CodePositionMaterializer): AfterMultiAdd =
      multiSetAdd(ImplDef.ProviderImpl(function.get.ret, function.get), pos)

    final def addSetValue[I <: Set[_ <: T]: Tag](instance: I)(implicit pos: CodePositionMaterializer): AfterMultiAdd =
      multiSetAdd(ImplDef.InstanceImpl(SafeType.get[I], instance), pos)

    final def refSet[I <: Set[_ <: T]: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false), pos)

    final def refSet[I <: Set[_ <: T]: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = false), pos)

    final def weakSet[I <: Set[_ <: T]: Tag](implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = true), pos)

    final def weakSet[I <: Set[_ <: T]: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name), weak = true), pos)

    protected[this] def multiSetAdd(newImpl: ImplDef, pos: CodePositionMaterializer): AfterMultiAdd
    protected[this] def appendElement(newImpl: ImplDef, pos: CodePositionMaterializer): AfterAdd
  }

  object MakeDSLBase {
    implicit final class MakeFromZIOHas[T, AfterBind](protected val dsl: MakeDSLBase[T, AfterBind]) extends AnyVal with MakeFromHasLowPriorityOverloads[T, AfterBind] {
      def fromHas[R: HasConstructor, E: Tag, I <: T: Tag](effect: ZIO[R, E, I]): AfterBind = {
        dsl.fromEffect[IO[E, ?], I](HasConstructor[R].map(effect.provide))
      }
      def fromHas[R: HasConstructor, E: Tag, I <: T: Tag](function: Functoid[ZIO[R, E, I]]): AfterBind = {
        dsl.fromEffect[IO[E, ?], I](function.map2(HasConstructor[R])(_.provide(_)))
      }

      def fromHas[R: HasConstructor, E: Tag, I <: T: Tag](resource: ZManaged[R, E, I]): AfterBind = {
        dsl.fromResource(HasConstructor[R].map(resource.provide))
      }
      def fromHas[R: HasConstructor, E: Tag, I <: T: Tag](function: Functoid[ZManaged[R, E, I]])(implicit d1: DummyImplicit): AfterBind = {
        dsl.fromResource(function.map2(HasConstructor[R])(_.provide(_)))
      }

      def fromHas[R: HasConstructor, E: Tag, I <: T: Tag](layer: ZLayer[R, E, Has[I]]): AfterBind = {
        dsl.fromResource(HasConstructor[R].map(layer.build.map(_.get).provide))
      }
      def fromHas[R: HasConstructor, E: Tag, I <: T: Tag](function: Functoid[ZLayer[R, E, Has[I]]])(implicit d1: DummyImplicit, d2: DummyImplicit): AfterBind = {
        dsl.fromResource(function.map2(HasConstructor[R])(_.build.map(_.get).provide(_)))
      }

      /**
        * Adds a dependency on `Local3[F]`
        *
        * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
        * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
        */
      def fromHas[R1 <: Lifecycle[Any, T]: AnyConstructor](implicit tag: TrifunctorHasLifecycleTag[R1, T]): AfterBind = {
        import tag._
        val provider: Functoid[Lifecycle[F[Any, E, ?], A]] =
          AnyConstructor[R1].zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]](tagLocal3)) {
            case ((resource, r), f) => provideLifecycle(f)(resource, r)
          }
        dsl.fromResource(provider)
      }
    }
    sealed trait MakeFromHasLowPriorityOverloads[T, AfterBind] extends Any {
      protected[this] def dsl: MakeDSLBase[T, AfterBind]

      /** Adds a dependency on `Local3[F]` */
      final def fromHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](effect: F[R, E, I]): AfterBind = {
        dsl.fromEffect[F[Any, E, ?], I](HasConstructor[R].map2(Functoid.identity[Local3[F]]) {
          (r, F: Local3[F]) => F.provide(effect)(r)
        })
      }

      /** Adds a dependency on `Local3[F]` */
      final def fromHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](function: Functoid[F[R, E, I]]): AfterBind = {
        dsl.fromEffect[F[Any, E, ?], I](function.zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]]) {
          case ((effect, r), f) => f.provide(effect)(r)
        })
      }

      /**
        * Adds a dependency on `Local3[F]`
        *
        * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
        * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
        */
      final def fromHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](resource: Lifecycle[F[R, E, ?], I]): AfterBind = {
        dsl.fromResource[Lifecycle[F[Any, E, ?], I]](HasConstructor[R].map2(Functoid.identity[Local3[F]]) {
          (r: R, F: Local3[F]) => provideLifecycle(F)(resource, r)
        })
      }

      /**
        * Adds a dependency on `Local3[F]`
        *
        * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
        * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
        */
      final def fromHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](
        function: Functoid[Lifecycle[F[R, E, ?], I]]
      )(implicit d1: DummyImplicit
      ): AfterBind = {
        dsl.fromResource[Lifecycle[F[Any, E, ?], I]](function.zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]]) {
          case ((resource, r), f) => provideLifecycle(f)(resource, r)
        })
      }
    }
  }

  object SetDSLBase {
    implicit final class AddFromZIOHas[T, AfterAdd](protected val dsl: SetDSLBase[T, AfterAdd, _]) extends AnyVal with AddFromHasLowPriorityOverloads[T, AfterAdd] {
      def addHas[R: HasConstructor, E: Tag, I <: T: Tag](effect: ZIO[R, E, I])(implicit pos: CodePositionMaterializer): AfterAdd = {
        dsl.addEffect[IO[E, ?], I](HasConstructor[R].map(effect.provide))
      }
      def addHas[R: HasConstructor, E: Tag, I <: T: Tag](function: Functoid[ZIO[R, E, I]])(implicit pos: CodePositionMaterializer): AfterAdd = {
        dsl.addEffect[IO[E, ?], I](function.map2(HasConstructor[R])(_.provide(_)))
      }

      def addHas[R: HasConstructor, E: Tag, I <: T: Tag](resource: ZManaged[R, E, I])(implicit pos: CodePositionMaterializer): AfterAdd = {
        dsl.addResource(HasConstructor[R].map(resource.provide))
      }
      def addHas[R: HasConstructor, E: Tag, I <: T: Tag](
        function: Functoid[ZManaged[R, E, I]]
      )(implicit pos: CodePositionMaterializer,
        d1: DummyImplicit,
      ): AfterAdd = {
        dsl.addResource(function.map2(HasConstructor[R])(_.provide(_)))
      }

      def addHas[R: HasConstructor, E: Tag, I <: T: Tag](layer: ZLayer[R, E, Has[I]])(implicit pos: CodePositionMaterializer): AfterAdd = {
        dsl.addResource(HasConstructor[R].map(layer.build.map(_.get).provide))
      }
      def addHas[R: HasConstructor, E: Tag, I <: T: Tag](
        function: Functoid[ZLayer[R, E, Has[I]]]
      )(implicit pos: CodePositionMaterializer,
        d1: DummyImplicit,
        d2: DummyImplicit,
      ): AfterAdd = {
        dsl.addResource(function.map2(HasConstructor[R])(_.build.map(_.get).provide(_)))
      }

      /**
        * Adds a dependency on `Local3[F]`
        *
        * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
        * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
        */
      final def addHas[R1 <: Lifecycle[Any, T]: AnyConstructor](implicit tag: TrifunctorHasLifecycleTag[R1, T], pos: CodePositionMaterializer): AfterAdd = {
        import tag._
        val provider: Functoid[Lifecycle[F[Any, E, ?], A]] =
          AnyConstructor[R1].zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]](tagLocal3)) {
            case ((resource, r), f) => provideLifecycle(f)(resource, r)
          }
        dsl.addResource(provider)
      }
    }
    sealed trait AddFromHasLowPriorityOverloads[T, AfterAdd] extends Any {
      protected[this] def dsl: SetDSLBase[T, AfterAdd, _]

      /** Adds a dependency on `Local3[F]` */
      final def addHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](effect: F[R, E, I])(implicit pos: CodePositionMaterializer): AfterAdd = {
        dsl.addEffect[F[Any, E, ?], I](HasConstructor[R].map2(Functoid.identity[Local3[F]]) {
          (r, F: Local3[F]) => F.provide(effect)(r)
        })
      }

      /** Adds a dependency on `Local3[F]` */
      final def addHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](
        function: Functoid[F[R, E, I]]
      )(implicit pos: CodePositionMaterializer
      ): AfterAdd = {
        dsl.addEffect[F[Any, E, ?], I](function.zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]]) {
          case ((effect, r), f) => f.provide(effect)(r)
        })
      }

      /**
        * Adds a dependency on `Local3[F]`
        *
        * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
        * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
        */
      final def addHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](
        resource: Lifecycle[F[R, E, ?], I]
      )(implicit pos: CodePositionMaterializer
      ): AfterAdd = {
        dsl.addResource[Lifecycle[F[Any, E, ?], I]](HasConstructor[R].map2(Functoid.identity[Local3[F]]) {
          (r: R, F: Local3[F]) => provideLifecycle(F)(resource, r)
        })
      }

      /**
        * Adds a dependency on `Local3[F]`
        *
        * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
        * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
        */
      final def addHas[F[-_, +_, +_]: TagK3, R: HasConstructor, E: Tag, I <: T: Tag](
        function: Functoid[Lifecycle[F[R, E, ?], I]]
      )(implicit pos: CodePositionMaterializer,
        d1: DummyImplicit,
      ): AfterAdd = {
        dsl.addResource[Lifecycle[F[Any, E, ?], I]](function.zip(HasConstructor[R]).map2(Functoid.identity[Local3[F]]) {
          case ((resource, r), f) => provideLifecycle(f)(resource, r)
        })
      }

    }
  }

  @inline private[this] def provideLifecycle[F[-_, +_, +_], R, E, A](F: Local3[F])(resource: Lifecycle[F[R, E, ?], A], r: R): Lifecycle[F[Any, E, ?], A] = {
    new Lifecycle[F[Any, E, ?], A] {
      override type InnerResource = resource.InnerResource
      override def acquire: F[Any, E, InnerResource] = F.provide(resource.acquire)(r)
      override def release(rr: InnerResource): F[Any, E, Unit] = F.provide(resource.release(rr))(r)
      override def extract[B >: A](rr: InnerResource): Either[F[Any, E, A], A] = resource.extract(rr).left.map(F.provide(_)(r))
    }
  }

  // DSL state machine

  /** These are the _only_ (not `from`-like) methods that can chained after `make`
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
  private[distage] final lazy val MakeDSLNoOpMethodsWhitelist = Set(
    "named",
    "namedByImpl",
    "tagged",
    "aliased",
    "annotateParameter",
    "modify",
    "modifyBy",
    "addDependency",
    "addDependencies",
  )

  final class MakeDSL[T](
    override protected val mutableState: SingletonRef,
    override protected val key: DIKey.TypeKey,
  ) extends MakeDSLMutBase[T, MakeDSL[T]]
    with MakeDSLBase[T, MakeDSLUnnamedAfterFrom[T]] {

    def named(name: Identifier): MakeNamedDSL[T] = {
      addOp(SetId(name))(new MakeNamedDSL[T](_, key.named(name)))
    }

    def namedByImpl: MakeNamedDSL[T] = {
      addOp(SetIdFromImplName())(new MakeNamedDSL[T](_, key))
    }

    override protected[this] def bind(impl: ImplDef): MakeDSLUnnamedAfterFrom[T] = {
      addOp(SetImpl(impl))(new MakeDSLUnnamedAfterFrom[T](_, key))
    }

    override protected[this] def toSame: SingletonRef => MakeDSL[T] = {
      new MakeDSL[T](_, key)
    }

  }

  final class MakeNamedDSL[T](
    override protected val mutableState: SingletonRef,
    override protected val key: DIKey.BasicKey,
  ) extends MakeDSLMutBase[T, MakeNamedDSL[T]]
    with MakeDSLBase[T, MakeDSLNamedAfterFrom[T]] {

    override protected[this] def bind(impl: ImplDef): MakeDSLNamedAfterFrom[T] = {
      addOp(SetImpl(impl))(new MakeDSLNamedAfterFrom[T](_, key))
    }

    override protected[this] def toSame: SingletonRef => MakeNamedDSL[T] = {
      new MakeNamedDSL[T](_, key)
    }

  }

  final class MakeDSLUnnamedAfterFrom[T](
    override protected val mutableState: SingletonRef,
    override protected val key: DIKey.TypeKey,
  ) extends MakeDSLMutBase[T, MakeDSLUnnamedAfterFrom[T]] {

    def named(name: Identifier): MakeDSLNamedAfterFrom[T] = {
      addOp(SetId(name))(new MakeDSLNamedAfterFrom[T](_, key.named(name)))
    }

    def namedByImpl: MakeDSLNamedAfterFrom[T] = {
      addOp(SetIdFromImplName())(new MakeDSLNamedAfterFrom[T](_, key))
    }

    override protected[this] def toSame: SingletonRef => MakeDSLUnnamedAfterFrom[T] = {
      new MakeDSLUnnamedAfterFrom[T](_, key)
    }

  }

  final class MakeDSLNamedAfterFrom[T](
    override protected val mutableState: SingletonRef,
    override protected val key: DIKey.BasicKey,
  ) extends MakeDSLMutBase[T, MakeDSLNamedAfterFrom[T]] {
    override protected[this] def toSame: SingletonRef => MakeDSLNamedAfterFrom[T] = {
      new MakeDSLNamedAfterFrom[T](_, key)
    }
  }

  sealed trait MakeDSLMutBase[T, Self <: MakeDSLMutBase[T, Self]] {
    protected[this] def mutableState: SingletonRef
    protected[this] def key: DIKey.BasicKey

    protected[this] def toSame: SingletonRef => Self

    final def tagged(tags: BindingTag*): Self = {
      addOp(AddTags(tags.toSet))(toSame)
    }

    final def modify[I <: T: Tag](f: T => I): Self = {
      addOp(Modify[T](_.map(f)))(toSame)
    }

    final def modifyBy(f: Functoid[T] => Functoid[T]): Self = {
      addOp(Modify(f))(toSame)
    }

    final def addDependency[B: Tag]: Self = {
      modifyBy(_.addDependency(DIKey.get[B]))
    }

    final def addDependency(key: DIKey): Self = {
      modifyBy(_.addDependency(key))
    }

    final def addDependencies(keys: Iterable[DIKey]): Self = {
      modifyBy(_.addDependencies(keys))
    }

    final def annotateParameter[P: Tag](name: Identifier): Self = {
      addOp(annotateParameterOp[P](name))(toSame)
    }

    final def aliased[T1 >: T: Tag](implicit pos: CodePositionMaterializer): Self = {
      addOp(AliasTo(DIKey.get[T1], pos.get.position))(toSame)
    }

    final def aliased[T1 >: T: Tag](name: Identifier)(implicit pos: CodePositionMaterializer): Self = {
      addOp(AliasTo(DIKey.get[T1].named(name), pos.get.position))(toSame)
    }

    protected[this] final def addOp[R](op: SingletonInstruction)(newState: SingletonRef => R): R = {
      newState(mutableState.append(op))
    }

    private[this] final def annotateParameterOp[P: Tag](name: Identifier): Modify[T] = {
      Modify[T] {
        old =>
          val paramTpe = SafeType.get[P]
          val newProvider = old.get.replaceKeys {
            case DIKey.TypeKey(tpe, m) if tpe == paramTpe =>
              DIKey.IdKey(paramTpe, name.id, m)(name.idContract)
            case k => k
          }
          Functoid(newProvider)
      }
    }
  }

  final class SetDSL[T](
    protected val mutableState: SetRef
  ) extends SetDSLMutBase[T] {

    def named(name: Identifier): SetNamedDSL[T] = {
      addOp(SetInstruction.SetIdAll(name))(new SetNamedDSL[T](_))
    }

  }

  final class SetNamedDSL[T](
    override protected val mutableState: SetRef
  ) extends SetDSLMutBase[T]

  final class SetElementDSL[T](
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

  final class MultiSetElementDSL[T](
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
