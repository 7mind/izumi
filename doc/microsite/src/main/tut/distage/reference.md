Syntax Summary
==============

@scaladoc[ModuleDefDSL](izumi.distage.model.definition.ModuleDefDSL) syntax:

```
Singleton bindings:
  - `make[X]` = create X using its constructor
  - `make[X].from[XImpl]` = bind X to its subtype XImpl using XImpl's constructor
  - `make[X].from(myX)` = bind X to an already existing instance `myX`
  - `make[X].from { y: Y => new X(y) }` = bind X to an instance of X constructed by a given [[izumi.distage.model.providers.Functoid Provider]] function
  - `make[X].fromEffect(X.create[F]: F[X])` = create X using a purely-functional effect `X.create` in `F` monad
  - `make[X].fromResource(X.resource[F]: Resource[F, X])` = create X using a [[DIResource]] specifying its creation and destruction lifecycle
  - `make[X].named("special")` = bind a named instance of X. It can then be summoned using [[Id]] annotation.
  - `make[X].using[X]` = bind X to refer to another already bound instance of `X`
  - `make[X].using[X]("special")` = bind X to refer to another already bound named instance at key `[X].named("special")`
  - `make[ImplXYZ].aliased[X].aliased[Y].aliased[Z]` = bind ImplXYZ and bind X, Y, Z to refer to the bound instance of ImplXYZ

Set bindings:
  - `many[X].add[X1].add[X2]` = bind a [[Set]] of X, and add subtypes X1 and X2 created via their constructors to it.
                                Sets can be bound in multiple different modules. All the elements of the same set in different modules will be joined together.
  - `many[X].add(x1).add(x2)` = add *instances* x1 and x2 to a `Set[X]`
  - `many[X].add { y: Y => new X1(y).add { y: Y => X2(y) }` = add instances of X1 and X2 constructed by a given [[izumi.distage.model.providers.Functoid Provider]] function
  - `many[X].named("special").add[X1]` = create a named set of X, all the elements of it are added to this named set.
  - `many[X].ref[XImpl]` = add a reference to an already **existing** binding of XImpl to a set of X's
  - `many[X].ref[X]("special")` = add a reference to an **existing** named binding of X to a set of X's

Tags:
  - `make[X].tagged("t1", "t2)` = attach tags to X's binding. Tags can be processed in a special way. See [[izumi.distage.roles.model.RoleId]]
  - `many[X].add[X1].tagged("x1tag")` = Tag a specific element of X. The tags of sets and their elements are separate.
  - `many[X].tagged("xsettag")` = Tag the binding of empty Set of X with a tag. The tags of sets and their elements are separate.

Includes:
  - `include(that: ModuleDef)` = add all bindings in `that` module into `this` module
```
