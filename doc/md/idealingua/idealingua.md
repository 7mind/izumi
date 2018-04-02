# Idealingua DML/IDL

[Code generation examples](cogen.md)

## Keywords and aliases

Keyword     | Aliases                | Explanation                                 |
------------| ---------------------- | ------------------------------------------- |
`domain `   | `package`, `namespace` | Namespace containing collection of entities |
`import`    |                        | References a domain by id                   |
`include`   |                        | Includes `*.model` file by name             |
`alias`     | `type`, `using`        | Type alias                                  |
`enum`      |                        | Enumeration                                 |
`mixin`     | `interface`            | Mixin, named collection of fields           |
`data`      | `dto`, `struct`        | Data                                        |
`adt`       | `choice`               | Algebraic Data Type                         |
`id`        |                        | Identifier, named collection of scalars     |
`service`   |                        | Service interface                           |
`def`       | `fn`, `fun`            | Method                                      |

## Embedded data types

Notes


1. When it's impossible to represent a numeric type with target language we use minimal numeric type with bigger range
2. When it's not possible to represent time type or UUID with target language we use string representation
  

### Scalar types

Type name   | Aliases                | Explanation                                 | Scala mapping                |
------------| ---------------------- | ------------------------------------------- | -----------------------------|
`bool`      | `boolean`              | Boolean                                     | `Boolean`                    |
`str`       | `string`               | String                                      | `String`                     |
`i08`       | `byte`, `int8`         | 8-bit integer                               | `Byte`                       |
`i16`       | `short`, `int16`       | 16-bit integer                              | `Short`                      |
`i32`       | `int`, `int32`         | 32-bit integer                              | `Int`                        |
`i64`       | `long`, `int64`        | 64-bit integer                              | `Long`                       |
`flt`       | `float`                | Single precision floating point             | `Float`                      |
`dbl`       | `double`               | Double precision floating point             | `Double`                     |
`uid`       | `uuid`                 | UUID                                        | `java.util.UUID`             |
`tsz`       | `dtl`, `datetimel`     | Timestamp with timezone                     | `java.time.ZonedDateTime`    |
`tsl`       | `dtz`, `datetimez`     | Local timestamp                             | `java.time.LocalDateTime`    |
`time`      | `time`                 | Time                                        | `java.time.LocalTime`        |
`date`      | `date`                 | Date                                        | `java.time.LocalDate`        |

### Generics

Type name    | Explanation                                 | Scala mapping  | 
------------ | ------------------------------------------- | -------------- |
`list[T]`    | List                                        | `List`         |
`map[K, V]`  | Map (only scalar keys supported)            | `Map`          |
`opt[T]`     | Optional value                              | `scala.Option` |
`set[T]`     | Set (no guarantees for traversal ordering)  | `Set`          |

### Circe serialization

#### Polymorphism and time

The following example demonstrates how polymorphism and time values are handled:

```scala
import java.time._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.generic.decoding._
import io.circe.generic.encoding._
import io.circe.java8.time._

sealed trait Polymorphic

case class TestPayload(
                        zonedDateTime: ZonedDateTime = ZonedDateTime.now()
                        , utcZonedDateTime: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))
                        , localDateTime: LocalDateTime = LocalDateTime.now()
                        , localTime: LocalTime = LocalTime.now()
                        , localDate: LocalDate = LocalDate.now()
                      ) extends Polymorphic

object TestPayload {
  implicit val encodeTestPayload: Encoder[TestPayload] = deriveEncoder[TestPayload]
  implicit val decodeTestPayload: Decoder[TestPayload] = deriveDecoder[TestPayload]
}

case class AnotherPayload(message: String) extends Polymorphic

object AnotherPayload {
  implicit val encodeAnotherPayload: Encoder[AnotherPayload] = deriveEncoder[AnotherPayload]
  implicit val decodeAnotherPayload: Decoder[AnotherPayload] = deriveDecoder[AnotherPayload]
}

object Polymorphic {
    implicit val encodePolymorphic: Encoder[Polymorphic] = Encoder.instance { c =>
    c match {
      case v: TestPayload =>
        Map("TestPayload" -> v).asJson
      case v: AnotherPayload =>
        Map("RealPayload" -> v).asJson
    }
  }
  implicit val decodePolymorphic: Decoder[Polymorphic] = Decoder.instance(c => {
    val fname = c.keys.flatMap(_.headOption).toSeq.head
    val value = c.downField(fname)
    fname match {
      case "TestPayload" =>
        value.as[TestPayload]
      case "RealPayload" =>
        value.as[AnotherPayload]
    }
  })
}

def test(t: Polymorphic): Unit = {
  val encoded = t.asJson.noSpaces
  println(s"Encoded:\n$encoded\n")
  val parsed = parse(encoded)
  println(s"Parsed:\n$parsed\n")
  val restored = parsed.map(_.as[Polymorphic])
  println(s"Restored:\n$restored\n")
}

test(TestPayload())
test(AnotherPayload("hi"))

```  

This example produces the following output:

```
TestPayload:
Encoded:
{"TestPayload":{"zonedDateTime":"2018-04-02T22:34:31.367649+01:00[Europe/Dublin]","utcZonedDateTime":"2018-04-02T21:34:31.367744Z[UTC]","localDateTime":"2018-04-02T22:34:31.36778","localTime":"22:34:31.367813","localDate":"2018-04-02"}}

Parsed:
Right({
  "TestPayload" : {
    "zonedDateTime" : "2018-04-02T22:34:31.367649+01:00[Europe/Dublin]",
    "utcZonedDateTime" : "2018-04-02T21:34:31.367744Z[UTC]",
    "localDateTime" : "2018-04-02T22:34:31.36778",
    "localTime" : "22:34:31.367813",
    "localDate" : "2018-04-02"
  }
})

Restored:
Right(Right(TestPayload(2018-04-02T22:34:31.367649+01:00[Europe/Dublin],2018-04-02T21:34:31.367744Z[UTC],2018-04-02T22:34:31.367780,22:34:31.367813,2018-04-02)))


AnotherPayload:
Encoded:
{"RealPayload":{"message":"hi"}}

Parsed:
Right({
  "RealPayload" : {
    "message" : "hi"
  }
})

Restored:
Right(Right(AnotherPayload(hi)))
```

Notes:
1. Same convention is applied for all the polymorhic values, like ADTs and Interfaces.
2. Data classes cannot be polymorphic 

#### Identifiers

Identifiers codec just invokes `.toString` and `.parse` to serialize/deserialize Identifiers.

Identifier format:

`Name#urlencoded(part1):urlencoded(part2):...`

Please check [identifier codegen example](cogen.md#id-identifier) for additional details.

Full example:

```
case class TestIdentifer(userId: String, context: String) {
  override def toString: String = {
    import com.github.pshirshov.izumi.idealingua.runtime.model.IDLIdentifier._
    val suffix = this.productIterator.map(part => escape(part.toString)).mkString(":")
    s"TestIdentifer#$suffix"
  }
}

trait TestIdentiferCirce {
  import _root_.io.circe.{ Encoder, Decoder }
  implicit val encodeTestIdentifer: Encoder[TestIdentifer] = Encoder.encodeString.contramap(_.toString)
  implicit val decodeTestIdentifer: Decoder[TestIdentifer] = Decoder.decodeString.map(TestIdentifer.parse)
}

object TestIdentifer extends TestIdentiferCirce {
  def parse(s: String): TestIdentifer = {
    import com.github.pshirshov.izumi.idealingua.runtime.model.IDLIdentifier._
    val withoutPrefix = s.substring(s.indexOf("#") + 1)
    val parts = withoutPrefix.split(":").map(part => unescape(part))
    TestIdentifer(parsePart[String](parts(0), classOf[String]), parsePart[String](parts(1), classOf[String]))
  }
  implicit class TestIdentiferExtensions(_value: TestIdentifer)
}
```

#### Enumerations

Identifiers codec just invokes `.toString` and `.parse` same way as it implemented for Identifiers.
