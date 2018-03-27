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
  def name: String
  def x: Int
  def id: String
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
trait Person extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType {
  def name: String
  def surname: String
}

object Person extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
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
  
  object PersonImpl extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
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

object HumanUser extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
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
trait Failure extends Any with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType { def message: String }

trait Success extends Any with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType { def values: scala.collection.immutable.Map[String, String] }

// companions stripped

sealed trait Result extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLAdtElement

object Result extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLAdt {
 
  import scala.language.implicitConversions
  
  type Element = Result
  
  case class Success(value: _root_.shared.rpc.Success) extends Result
  
  object Success extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLAdtElementCompanion {
  }

  implicit def intoSuccess(value: _root_.shared.rpc.Success): Result = Result.Success(value)
  implicit def fromSuccess(value: Result.Success): _root_.shared.rpc.Success = value.value

  case class Failure(value: _root_.shared.rpc.Failure) extends Result

  object Failure extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLAdtElementCompanion {
  }
  
  implicit def intoFailure(value: _root_.shared.rpc.Failure): Result = Result.Failure(value)
  implicit def fromFailure(value: Result.Failure): _root_.shared.rpc.Failure = value.value
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
sealed trait Gender extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLEnumElement

object Gender extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLEnum {
  type Element = Gender
  override def all: Seq[Gender] = Seq(MALE, FEMALE)
  override def parse(value: String) = value match {
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
case class RecordId(value: java.util.UUID) extends AnyVal with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType with com.github.pshirshov.izumi.idealingua.runtime.model.IDLIdentifier {
  override def toString: String = {
    val suffix = this.productIterator.map(part => com.github.pshirshov.izumi.idealingua.runtime.model.IDLIdentifier.escape(part.toString)).mkString(":")
    s"RecordId#$suffix"
  }
}

object RecordId extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
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

class UserServiceServerDispatcher[R[+_], S <: UserService[R]](val service: S) extends transport.AbstractServerDispatcher[R, S] with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType {
  import UserService._
  override def dispatch(input: UserService.InUserService): R[UserService.OutUserService] = input match {
    case value: UserService.InDeleteUser =>
      service.deleteUser(value)
    case value: UserService.InCreateUser =>
      service.createUser(value)
  }
}

class UserServiceClientDispatcher[R[+_], S <: UserService[R]](dispatcher: transport.AbstractClientDispatcher[R, S]) extends UserService[R] with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType {
  import UserService._
  def deleteUser(input: UserService.InDeleteUser): Result[UserService.OutDeleteUser] = dispatcher.dispatch(input, classOf[UserService.OutDeleteUser])
  def createUser(input: UserService.InCreateUser): Result[UserService.OutCreateUser] = dispatcher.dispatch(input, classOf[UserService.OutCreateUser])
}

class UserServiceClientWrapper[R[+_], S <: UserService[R]](val service: S) extends model.IDLClientWrapper[R, S] with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType {
  import UserService._
  def deleteUser(id: RecordId): Result[UserService.OutDeleteUser] = {
    service.deleteUser(UserService.InDeleteUser(id = id))
  }
  def createUser(balance: Double, email: String): Result[UserService.OutCreateUser] = {
    service.createUser(UserService.InCreateUser(balance = balance, email = email))
  }
}

trait UserServiceUnwrapped[R[+_], S <: UserService[R]] extends model.IDLServiceExploded[R, S] with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType {
  import UserService._
  def deleteUser(id: RecordId): Result[UserService.OutDeleteUser]
  def createUser(balance: Double, email: String): Result[UserService.OutCreateUser]
}

class UserServiceServerWrapper[R[+_], S <: UserService[R]](val service: UserServiceUnwrapped[R, S]) extends UserService[R] with com.github.pshirshov.izumi.idealingua.runtime.model.IDLGeneratedType {
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
  object InDeleteUser extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
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
  object OutDeleteUser extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
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
  object InCreateUser extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
    def apply(userdata: UserData, privateuserdata: PrivateUserData): UserService.InCreateUser = {
      UserService.InCreateUser(balance = privateuserdata.balance, email = userdata.email)
    }
  }

  case class OutCreateUser(result: shared.rpc.Result, id: RecordId) extends UserService.OutUserService with WithRecordId with WithResult

  object OutCreateUser extends com.github.pshirshov.izumi.idealingua.runtime.model.IDLTypeCompanion {
    def apply(withrecordid: WithRecordId, withresult: WithResult): UserService.OutCreateUser = {
      UserService.OutCreateUser(id = withrecordid.id, result = withresult.result)
    }
  }
}
             
``` 
