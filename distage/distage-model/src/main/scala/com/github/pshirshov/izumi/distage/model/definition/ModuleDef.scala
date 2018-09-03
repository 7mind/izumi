package com.github.pshirshov.izumi.distage.model.definition

/**
  * DSL for defining module Bindings.
  *
  * Example:
  * {{{
  * class Program[F: TagK: Monad] extends ModuleDef {
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
  *                                 Sets can be bound in multiple different modules. All the elements of the same set in different modules will be joined together.
  *     `many[X].add(x1).add(x2)` = add *instances* x1 and x2 to a `Set[X]`
  *   - `many[X].add { y: Y => new X1(y).add { y: Y => X2(y) }` = add instances of X1 and X2 constructed by a given [[com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet Provider]] function
  *   - `many[X].named("special").add[X1]` = create a named set of X, all the elements of it are added to this named set.
  *   - `many[X].ref[XImpl]` = add a reference to an already **existing** binding of XImpl to a set of X's
  *   - `many[X].ref[X]("special")` = add a reference to an **existing** named binding of X to a set of X's
  *
  * Tags:
  *   - `make[X].tagged("t1", "t2)` = attach tags to X's binding. Tags can be processed in a special way. See [[RoleId]]
  *   - `many[X].add[X1].tagged("x1tag")` = Tag a specific element of X. The tags of sets and their elements are separate.
  *   - `many[X].tagged("xsettag")` = Tag the binding of empty Set of X with a tag. The tags of sets and their elements are separate.
  *
  * @see [[com.github.pshirshov.izumi.fundamentals.reflection.WithTags#TagK TagK]]
  * @see [[Id]]
  * @see [[ModuleDefDSL]]
  */
trait ModuleDef extends Module with ModuleDefDSL

object ModuleDef {
  implicit val moduleDefApi: ModuleMake[ModuleDef] = b =>
    new ModuleDef {
      override val bindings: Set[Binding] = b
    }
}
