# Code generator reference 

Notes:

1. All the examples are given in minimal form, without marshallers/type info/etc

We support the following concepts: 

1. Enumerations
2. Algebraic Data Types
3. Type aliases
4. Mixins
5. Data Classes
6. Identifiers
7. Services

## Inheritance

1. We support two forms of inheritance: interface inheritance (`+` modifier) and structural mixins (`*` modifier)
2. The only difference between structural inheritance and interface inheritance is presence/absence of base interface
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

case class Point(y: Int, name: String, x: Int, id: String) extends Metadata

trait PointLike extends Metadata {
  def y: Int
  def x: Int
  def name: String
  def id: String
}


object IntPair {
  implicit class IntPairExtensions(_value: IntPair) {
    def asIntPair(): IntPair.IntPairImpl = {
      IntPair.IntPairImpl(x = _value.x, y = _value.y)
    }
    def toIntPairImpl(): IntPair.IntPairImpl = {
      IntPair.IntPairImpl(x = _value.x, y = _value.y)
    }
    def toPointLikeImpl(pointlike: PointLike): PointLike.PointLikeImpl = {
      PointLike.PointLikeImpl(x = _value.x, y = _value.y, id = pointlike.id, name = pointlike.name)
    }
  }
  def apply(y: Int, x: Int) = IntPairImpl(y, x)
  case class IntPairImpl(y: Int, x: Int) extends IntPair
  object IntPairImpl {
    def apply(intpair: IntPair): IntPair.IntPairImpl = {
      IntPair.IntPairImpl(x = intpair.x, y = intpair.y)
    }
  }
}

object Metadata {
  implicit class MetadataExtensions(_value: Metadata) {
    def asMetadata(): Metadata.MetadataImpl = {
      Metadata.MetadataImpl(id = _value.id, name = _value.name)
    }
    def toPoint(intpair: IntPair): Point = {
      Point(id = _value.id, name = _value.name, x = intpair.x, y = intpair.y)
    }
    def toMetadataImpl(): Metadata.MetadataImpl = {
      Metadata.MetadataImpl(id = _value.id, name = _value.name)
    }
    def toPointLikeImpl(pointlike: PointLike, intpair: IntPair): PointLike.PointLikeImpl = {
      PointLike.PointLikeImpl(id = _value.id, name = _value.name, x = intpair.x, y = intpair.y)
    }
  }
  def apply(name: String, id: String) = MetadataImpl(name, id)
  case class MetadataImpl(name: String, id: String) extends Metadata
  object MetadataImpl {
    def apply(metadata: Metadata): Metadata.MetadataImpl = {
      Metadata.MetadataImpl(id = metadata.id, name = metadata.name)
    }
  }
}

object Point {
  implicit class PointExtensions(_value: Point) {
    def intoPointLikeImpl(): PointLike.PointLikeImpl = {
      PointLike.PointLikeImpl(y = _value.y, name = _value.name, x = _value.x, id = _value.id)
    }
  }
  def apply(metadata: Metadata, intpair: IntPair): Point = {
    Point(id = metadata.id, name = metadata.name, x = intpair.x, y = intpair.y)
  }
}

object PointLike {
  implicit class PointLikeExtensions(_value: PointLike) {
    def asPointLike(): PointLike.PointLikeImpl = {
      PointLike.PointLikeImpl(id = _value.id, name = _value.name, x = _value.x, y = _value.y)
    }
    def asIntPair(): IntPair.IntPairImpl = {
      IntPair.IntPairImpl(x = _value.x, y = _value.y)
    }
    def toPointLikeImpl(): PointLike.PointLikeImpl = {
      PointLike.PointLikeImpl(id = _value.id, name = _value.name, x = _value.x, y = _value.y)
    }
    def intoPoint(): Point = {
      Point(y = _value.y, name = _value.name, x = _value.x, id = _value.id)
    }
  }
  def apply(y: Int, name: String, x: Int, id: String) = PointLikeImpl(y, name, x, id)
  case class PointLikeImpl(y: Int, name: String, x: Int, id: String) extends PointLike
  object PointLikeImpl {
    implicit class PointLikeImplExtensions(_value: PointLike.PointLikeImpl) {
      def intoPoint(): Point = {
        Point(y = _value.y, name = _value.name, x = _value.x, id = _value.id)
      }
    }
    def apply(pointlike: PointLike, intpair: IntPair): PointLike.PointLikeImpl = {
      PointLike.PointLikeImpl(id = pointlike.id, name = pointlike.name, x = intpair.x, y = intpair.y)
    }
  }
}
       
``` 


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

object Person {
  implicit class PersonExtensions(_value: Person) {
    def asPerson(): Person.PersonImpl = {
      Person.PersonImpl(name = _value.name, surname = _value.surname)
    }
    def toPersonImpl(): Person.PersonImpl = {
      Person.PersonImpl(name = _value.name, surname = _value.surname)
    }
  }
  
  def apply(name: String, surname: String) = PersonImpl(name, surname)
  
  case class PersonImpl(name: String, surname: String) extends Person
  
  object PersonImpl {
    def apply(person: Person): Person.PersonImpl = {
      Person.PersonImpl(name = person.name, surname = person.surname)
    }
  }
}
       
``` 

## `data`: Data Class

Differences between Mixins and Data Classes:

1. Data class cannot define fields
2. Data class cannot be subclassed
3. Data class is always redered as DTO/case class, Mixin is always rendered as pair of an Interface and an Implementation  

```idealingua
 data HumanUser {
   + IdentifiedUser
   * Person
 }
```

### Scala output

```scala
case class HumanUser(name: String, surname: String, id: UserId) extends IdentifiedUser with Person

object HumanUser {
  def apply(identifieduser: IdentifiedUser, person: Person): HumanUser = {
    HumanUser(id = identifieduser.id, name = person.name, surname = person.surname)
  }
}
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

// companions stripped

sealed trait Result 

object Result {
 
  import scala.language.implicitConversions
  
  type Element = Result
  
  case class Success(value: Success) extends Result
  
  object Success {
  }

  implicit def intoSuccess(value: rpc.Success): Result = Result.Success(value)
  implicit def fromSuccess(value: Result.Success): rpc.Success = value.value

  case class Failure(value: Failure) extends Result

  object Failure {
  }
  
  implicit def intoFailure(value: rpc.Failure): Result = Result.Failure(value)
  implicit def fromFailure(value: Result.Failure): rpc.Failure = value.value
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

```idealingua
 id RecordId {
   value: uid
 }
```

Note: implementation is unfinished, we don't provide parsers right now

### Scala output

```scala
case class RecordId(value: java.util.UUID) extends AnyVal {
  override def toString: String = {
    val suffix = this.productIterator.map(part => IDLIdentifier.escape(part.toString)).mkString(":")
    s"RecordId#$suffix"
  }
}

object RecordId {
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
  balance: dbl
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
import _root_.com.github.pshirshov.izumi.idealingua.model._
import _root_.com.github.pshirshov.izumi.idealingua.runtime._

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

trait UserService[R[_]] extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLService[R] {
  import UserService._
  override type InputType = UserService.InUserService
  override type OutputType = UserService.OutUserService
  
  override def inputClass: Class[UserService.InUserService] = classOf[UserService.InUserService]
  override def outputClass: Class[UserService.OutUserService] = classOf[UserService.OutUserService]
  
  def deleteUser(input: UserService.InDeleteUser): Result[UserService.OutDeleteUser]
  def createUser(input: UserService.InCreateUser): Result[UserService.OutCreateUser]
}

object UserService extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLServiceCompanion {
  sealed trait InUserService extends Any with com.github.pshirshov.izumi.idealingua.runtime.model.IDLInput
  sealed trait OutUserService extends Any with com.github.pshirshov.izumi.idealingua.runtime.model.IDLOutput

  case class InDeleteUser(id: RecordId) extends UserService.InUserService with WithRecordId
  object InDeleteUser {
    implicit class InDeleteUserExtensions(_value: UserService.InDeleteUser) {
      def intoWithRecordIdImpl(): WithRecordId.WithRecordIdImpl = {
        WithRecordId.WithRecordIdImpl(id = _value.id)
      }
    }
    def apply(withrecordid: WithRecordId): UserService.InDeleteUser = {
      UserService.InDeleteUser(id = withrecordid.id)
    }
  }
  
  case class OutDeleteUser(result: shared.rpc.Result) extends AnyVal with UserService.OutUserService with WithResult
  object OutDeleteUser {
    implicit class OutDeleteUserExtensions(_value: UserService.OutDeleteUser) {
      def intoWithResultImpl(): WithResult.WithResultImpl = {
        WithResult.WithResultImpl(result = _value.result)
      }
    }
    def apply(withresult: WithResult): UserService.OutDeleteUser = {
      UserService.OutDeleteUser(result = withresult.result)
    }
  }
  
  case class InCreateUser(balance: Double, email: String) extends UserService.InUserService with UserData with PrivateUserData
  object InCreateUser {
    def apply(userdata: UserData, privateuserdata: PrivateUserData): UserService.InCreateUser = {
      UserService.InCreateUser(balance = privateuserdata.balance, email = userdata.email)
    }
  }

  case class OutCreateUser(result: shared.rpc.Result, id: RecordId) extends UserService.OutUserService with WithRecordId with WithResult

  object OutCreateUser {
    def apply(withrecordid: WithRecordId, withresult: WithResult): UserService.OutCreateUser = {
      UserService.OutCreateUser(id = withrecordid.id, result = withresult.result)
    }
  }
}
             
``` 
