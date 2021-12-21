package izumi.distage.model.definition

import izumi.distage.model.definition.dsl.ModuleDefDSL

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
  *   - `make[X].from[XImpl].modifyWithDependencies { (c: C, d: D) => (x: X) => c.method(x, d) }` = Create X using XImpl's constructor and modify its `X` by summoning additional `C` & `D` dependencies and applying `C.method` to `X`
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
  *   - `modify[X].withDependencies { (c: C, d: D) => (x: X) => c.method(x, d) }` = add a modifier to the value bound at `X`, summoning additional `C` & `D` dependencies and applying `C.method` to `X` (mutator application order is unspecified)
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
trait ModuleDef extends Module with ModuleDefDSL
