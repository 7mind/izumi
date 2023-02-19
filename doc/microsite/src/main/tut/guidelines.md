---
out: guidelines.html
---
Izumi Framework Best Practices
=====================

General workflow
----------------

When we start working on a new component, generally we follow these steps:

1. Define external data model of the component, namely:
   1. Inputs and outputs of the component methods, as `case` classes and `sealed` traits
   2. Expected (domain) error hierarchies, as one `sealed trait` and `final case` classes inheriting it
2. We define abstract interface (`trait`) of the component,
   1. We use an abstract bifunctor `F[+_, +_]` in order to encode method outputs
   2. Methods which are not expected to fail should have `Nothing` in the error (left) branch of the bifunctor
   3. It's okay to initially have `Nothing` everywhere and add specific error later, once we discover them
   4. Don't try to encode all the possible errors, especially irrecoverable ones. E.g. it's pointless to encode `OutOfMemoryError` as a possible method result.
      Only the expected domain errors should be encoded.
3. Create a stub implementation of the component. If the component is an integration point, its first implementation should be a fake
   1. Use `BIO` typeclasses as `F` implementation, if the component is intended to be run under ZIO or another bifunctor, use `Either` otherwise
   2. Try to use the minimal set of `BIO` typeclasses necessary for your component. It's fine to start by using `IO2` but you should remove unnecessary capabilites when possible.
   3. Keep the stub implementation inside of the companion object of the component interface
4. Add distage bindings for the component and its implementation
5. Create a stub of an abstract test suite for the interface
5. Work on the test and the implementation (the fake or the business code) simultaneously until you have them functional
   1. It's not very good but acceptable to use impure mutable fields in dummies instead of `Ref`s.
6. If the component is an integration point, start working on a real implementation
   1. Add integration checks and their bindings
   2. Add docker definitions and their bindings for managed scene if that's possible

### Example


```scala
final case class User(props: Map[String, String])
final case class IdentifiedUser(id: UUID, user: User)

sealed trait DatabaseError
final case class EntityNotFoundExists(id: UUID) extends DatabaseError

trait DatabaseLayer[F[+_, +_]] {
  def store(id: UUID, user: User): F[Nothing, Unit]
  def get(id: UUID): F[EntityNotFoundExists, IdentifiedUser]
}

object DatabaseLayer {
  final class DatabaseLayerDummyImpl[F[+_, +_] : IO2]() extends DatabaseLayer[F] {
    private val content = scala.collection.mutable.HashMap.empty[UUID, IdentifiedUser]

     def store(id: UUID, user: User): F[Nothing, Unit] = F.sync(content += IdentifiedUser(id, user))
     def get(id: UUID): F[EntityNotFoundExists, IdentifiedUser] = F.fromOption(EntityNotFoundExists(id))(content.get(id))
  }
}

final class DatabaseLayerPostgresImpl[F[+_, +_] : IO2]() extends DatabaseLayer[F] {
  // ...
}

class DatabaseModule[F[+_, +_] : TagKK] extends PluginDef {
  make[DatabaseLayer[F]].tagged(Repo.Dummy).from[DatabaseLayer.DatabaseLayerDummyImpl[F]]
  make[DatabaseLayer[F]].tagged(Repo.Prod).from[DatabaseLayerPostgresImpl[F]]
}

class DatabaseLayerTest extends Spec2[zio.IO] {
   override def config: TestConfig = super.config.copy(
      pluginConfig = PluginConfig.const(new DatabaseModule[zio.IO])
   )

   "database layer" should {
      "store users" in {
         (db: DatabaseLayer[zio.IO]) =>
            for {
              // ...
            } yield {
              assert(...)
            }
      }
   }
}
```

BIO, TF and error encoding
--------------------------

1. Always use for-comprehension
2. Always use minimal possible set of BIO typeclasses
3. You can't do much with irrecoverable errors. Let them be logged and fail the computation

### Error propagation

If one component uses another, it either should propagate its errors or handle them.
This makes things somewhat inconvenient, especially on Scala 2 which has no support for union types.

Essentially, on Scala 2 there is no perfect solution for error propagation.

The most comprehensive solution would look like:

```scala
sealed trait DatabaseError
final case class EntityNotFoundExists(id: UUID) extends DatabaseError

sealed trait BusinessError
sealed trait UserStoreMethodError {
   this: BusinessError =>
}
final case class UserInvalid(id: UUID) extends BusinessError with UserStoreMethodError
final case class InheritedDatabaseError(error: DatabaseError) extends BusinessError with UserStoreMethodError

trait BusinessLayer[F[+_, +_]] {
   def validateAndStore(id: UUID, user: User): F[UserStoreMethodError, Unit]
}
```

Essentially, this approach simulates the missing union types.

It kinda breaks encapsulation, because the errors specific only to one particular component implementation are being propagated into the abstract interface.
Also this approach, when followed strictly, is just way too verbose.

It's possible to modify it by just logging dependency errors and returning generic failure branch:

```scala
sealed trait BusinessError
sealed trait UserStoreMethodError {
   this: BusinessError =>
}
final case class UserInvalid(id: UUID) extends BusinessError with UserStoreMethodError
// when we return this, we would have to log the details first
final case class InheritedDatabaseError() extends BusinessError with UserStoreMethodError
```

In most of the cases you wouldn't want to create separate unions for every method (like `UserStoreMethodError`) and would just return the base failure (`BusinessError`) everywhere:

```scala
sealed trait BusinessError
final case class UserInvalid(id: UUID) extends BusinessError
final case class InheritedDatabaseError() extends BusinessError

trait BusinessLayer[F[+_, +_]] {
   def validateAndStore(id: UUID, user: User): F[BusinessError, Unit]
}
```

It's tempting to use exceptions everywhere either explicitly encoded or just hidden in old bad Java manner.
Don't do that. It's always possible to have errors encoded as values and, generally, it always pays back.

Only use exceptions and try-catch blocks when you need to integrate with legacy third-party code.

Dual Test Tactic and TDD
------------------------

1. When working on a dummy, don't try to replicate all the specificity of a real endpoint, unless that's really important.
Dummies should be cheap. There is no point in simulating all the possible errors and conditions specific to real integration points.
   1. Probably, you don't want to simulate database connection errors in a database layer dummy
   2. Though, if you work on a  UDP transport layer component, you might want to simulate packet loss in its dummy
2. Dual test tactic always pays back, always write dual tests for your integration points
3. With distage it's not expensive to have dual tests for your business logic. Do that too.
4. There is no reason to test all the possible combinations of production/dummy dependencies. Usually it's enough to have an all-production and an all-dummy test configuration.

Data modeling
-------------

1. Denormalize inputs and outputs. E.g. it's a common useful pattern to split "raw" "unsaved" entities and "stored" entities retrieved from the database
2. Make your models closed. All the classes should be `final`, all traits `sealed`.
3. Avoid storing untyped data (e.g. `JSON`) in your models at all costs

Integration points
------------------

1. Keep your integration points as dumb as possible, just wrap the APIs you integrate with
2. Generally, all the assertions, computations and decisions should happen in your business layer

Inheritance
-----------

1. There is very limited use of `class` inheritance. Usually one `class` shouldn't inherit from another. Make classes `final` by default. If you intend to use class inheritance, mark them as `open`, but do not leave them unmarked.
2. `abstract` classes might be useful but, generally, you should avoid having them too

