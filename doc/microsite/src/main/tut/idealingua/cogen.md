# Code generator reference

We support the following concepts:

1. Enumerations
2. Algebraic Data Types
3. Type aliases
4. Mixins
5. Data Classes
6. Identifiers
7. Services

## Inheritance

1. We support two forms of inheritance: interface inheritance (`&` modifier) and structural mixins (`*` modifier)
2. The only difference between structural inheritance and interface inheritance is presence/absence of the base interface in the list of supertypes
3. Both Data Classes and Mixins support both forms of inheritance
4. Services, ADTs, Type Aliases, Identifiers and Enumerations does not support inheritance
5. We provide widening narrowing implicit functions as well as copy constructors for all the generated entities

### Example

```idealingua
mixin IntPair {
  x: i32
  y: i32
}

mixin Metadata {
  id: str
  name: str
}

mixin PointLike {
  + Metadata
  * IntPair
}

data Point {
  + Metadata
  * IntPair
}
```

#### Scala output

```scala
trait IntPair {
  def y: Int
  def x: Int
}

trait Metadata {
  def name: String
  def id: String
}

final case class Point(y: Int, name: String, x: Int, id: String) extends Metadata

trait PointLike extends Metadata {
  def y: Int
  def x: Int
  def name: String
  def id: String
}
```

# Constructs

*Notes:*

1. All the examples of generated code are given in minimal form
2. Generated code for codecs, implicit conversions and type info is omitted

## `mixin`: Mixin

```idealingua
 mixin Person {
   name: str
   surname: str
 }
```

### Scala output

```scala
trait Person {
  def name: String
  def surname: String
}
```

## `data`: Data Class

Differences between Mixins and Data Classes:

1. Data classes cannot be subclassed
2. Data classes are always rendered as case classes, Mixins are always rendered as pair of Interface and Implementation

```idealingua
 data HumanUser {
   + IdentifiedUser
   * Person
 }
```

### Scala output

```scala
final case class HumanUser(name: String, surname: String, id: UserId) extends IdentifiedUser with Person
```

## `adt`: Algebraic Data Type

```idealingua
 mixin Success {
   values: map[str, str]
 }

 mixin Failure {
   message: str
 }

 adt Result {
   Success
   Failure
 }
```

### Scala output

```scala
trait Failure extends Any { def message: String }

trait Success extends Any { def values: scala.collection.immutable.Map[String, String] }

sealed trait Result

object Result {
  type Element = Result

  case class Success(value: Success) extends Result
  case class Failure(value: Failure) extends Result
}

```

## `alias`: Type Alias

```idealingua
alias UserId = str
```

### Scala output

```scala
package object domain01 {
type UserId = String
}
```

## `enum`: Enumeration

```idealingua
enum Gender {
  MALE
  FEMALE
}
```

### Scala output

```scala
sealed trait Gender

object Gender {
  type Element = Gender
  def all: Seq[Gender] = Seq(MALE, FEMALE)
  def parse(value: String) = value match {
    case "MALE" => MALE
    case "FEMALE" => FEMALE
  }
  case object MALE extends Gender { override def toString: String = "MALE" }
  case object FEMALE extends Gender { override def toString: String = "FEMALE" }
}
```


## `id`: Identifier

Notes:

1. You can use only scalar builtin types for identifier fields
2. We provide both parser and sane `.toString` implementation
3. `.toString` uses the following format: `Name#urlencoded(part1):urlencoded(part2):...`
4. Fields are sorted by name before using in parser and `.toString`

```idealingua
id UserId {
  value: uid
  company: uid
}
```

### Scala output

```scala
final case class UserId(value: java.util.UUID, company: java.util.UUID) {
  override def toString: String = {
    import izumi.idealingua.runtime.model.IDLIdentifier._
    val suffix = Seq(this.company, this.value).map(part => escape(part.toString)).mkString(":")
    s"UserId#$suffix"
  }
}

object UserId {
  def parse(s: String): UserId = {
    import izumi.idealingua.runtime.model.IDLIdentifier._
    val withoutPrefix = s.substring(s.indexOf("#") + 1)
    val parts = withoutPrefix.split(":").map(part => unescape(part))
    UserId(parsePart[java.util.UUID](parts(0), classOf[java.util.UUID]), parsePart[java.util.UUID](parts(1), classOf[java.util.UUID]))
  }
}
```

## `service`: Service

```idealingua
id RecordId {
  value: uid
}

mixin WithRecordId {
  id: RecordId
}

mixin WithResult {
  result: shared.rpc#Result
}

mixin UserData {
  email: str
}

mixin PrivateUserData {
  balance: f64
}

service UserService {
  def deleteUser(WithRecordId): (WithResult)
  def createUser(UserData, PrivateUserData): (WithRecordId, WithResult)
}

```

Notes:

1. Service signature cannot accept anything except of Mixins (improvements planned)
2. `ServerDispatcher` allows you to route wrapped result type to an appropriate method of an abstract implementation
3. `ClientDispatcher` just passes input to an abstract receiver
4. `ClientWrapper` allows you to transform unwrapped method signatures into wrapping instances
5. `ServerWrapped` provides you an unwrapping service implementation
6. `ServiceUnwrapped` provides you a way to implement services with signatures unwrapped

### Scala output

```scala
import _root_.izumi.idealingua.model._
import _root_.izumi.idealingua.runtime._

class UserServiceServerDispatcher[R[+_], S <: UserService[R]](val service: S) extends transport.AbstractServerDispatcher[R, S] {
  import UserService._
  override def dispatch(input: UserService.InUserService): R[UserService.OutUserService] = input match {
    case value: UserService.InDeleteUser =>
      service.deleteUser(value)
    case value: UserService.InCreateUser =>
      service.createUser(value)
  }
}

class UserServiceClientDispatcher[R[+_], S <: UserService[R]](dispatcher: transport.AbstractClientDispatcher[R, S]) extends UserService[R] {
  import UserService._
  def deleteUser(input: UserService.InDeleteUser): Result[UserService.OutDeleteUser] = dispatcher.dispatch(input, classOf[UserService.OutDeleteUser])
  def createUser(input: UserService.InCreateUser): Result[UserService.OutCreateUser] = dispatcher.dispatch(input, classOf[UserService.OutCreateUser])
}

class UserServiceClientWrapper[R[+_], S <: UserService[R]](val service: S) extends model.IDLClientWrapper[R, S] {
  import UserService._
  def deleteUser(id: RecordId): Result[UserService.OutDeleteUser] = {
    service.deleteUser(UserService.InDeleteUser(id = id))
  }
  def createUser(balance: Double, email: String): Result[UserService.OutCreateUser] = {
    service.createUser(UserService.InCreateUser(balance = balance, email = email))
  }
}

trait UserServiceUnwrapped[R[+_], S <: UserService[R]] extends model.IDLServiceExploded[R, S] {
  import UserService._
  def deleteUser(id: RecordId): Result[UserService.OutDeleteUser]
  def createUser(balance: Double, email: String): Result[UserService.OutCreateUser]
}

class UserServiceServerWrapper[R[+_], S <: UserService[R]](val service: UserServiceUnwrapped[R, S]) extends UserService[R] {
  import UserService._
  def deleteUser(input: UserService.InDeleteUser): Result[UserService.OutDeleteUser] = service.deleteUser(id = input.id)
  def createUser(input: UserService.InCreateUser): Result[UserService.OutCreateUser] = service.createUser(balance = input.balance, email = input.email)
}

trait UserService[R[_]] extends izumi.idealingua.runtime.model.IDLService[R] {
  import UserService._
  override type InputType = UserService.InUserService
  override type OutputType = UserService.OutUserService

  override def inputClass: Class[UserService.InUserService] = classOf[UserService.InUserService]
  override def outputClass: Class[UserService.OutUserService] = classOf[UserService.OutUserService]

  def deleteUser(input: UserService.InDeleteUser): Result[UserService.OutDeleteUser]
  def createUser(input: UserService.InCreateUser): Result[UserService.OutCreateUser]
}

object UserService {
  sealed trait InUserService extends Any
  sealed trait OutUserService extends Any

  case class InDeleteUser(id: RecordId) extends UserService.InUserService with WithRecordId
  case class OutDeleteUser(result: shared.rpc.Result) extends AnyVal with UserService.OutUserService with WithResult
  case class InCreateUser(balance: Double, email: String) extends UserService.InUserService with UserData with PrivateUserData
  case class OutCreateUser(result: shared.rpc.Result, id: RecordId) extends UserService.OutUserService with WithRecordId with WithResult
}

```
