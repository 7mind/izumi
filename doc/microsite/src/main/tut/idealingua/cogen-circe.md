# Circe serialization reference

## Polymorphism and time

Notes:

1. Data classes cannot be polymorphic 

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

trait Polymorphic

final case class TestPayload(
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

final case class AnotherPayload(message: String) extends Polymorphic

object AnotherPayload {
  implicit val encodeAnotherPayload: Encoder[AnotherPayload] = deriveEncoder[AnotherPayload]
  implicit val decodeAnotherPayload: Decoder[AnotherPayload] = deriveDecoder[AnotherPayload]
}

object Polymorphic {
    implicit val encodePolymorphic: Encoder[Polymorphic] = Encoder.instance { c =>
    c match {
      case v: TestPayload =>
        Map("com.test#TestPayload" -> v).asJson
      case v: AnotherPayload =>
        Map("com.test#RealPayload" -> v).asJson
    }
  }
  implicit val decodePolymorphic: Decoder[Polymorphic] = Decoder.instance(c => {
    val fname = c.keys.flatMap(_.headOption).toSeq.head
    val value = c.downField(fname)
    fname match {
      case "com.test#TestPayload" =>
        value.as[TestPayload]
      case "com.test#RealPayload" =>
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



## Algebraic types

Notes:

1. Works same way as polymorphic types
2. Use short names instead of fully qualified names
3. You may introduce a local alias for an algebraic type member: `adt MyAdt { domain1.A as A1 | A}`. This allows you to resolve name conflicts 

Cogen example for ADTs:

```scala
import java.time._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.generic.decoding._
import io.circe.generic.encoding._
import io.circe.java8.time._

sealed trait Algebraic

object NS1 {

  case class Payload(localTime: LocalTime = LocalTime.now()) extends Algebraic

  object Payload {
    implicit val encodeTestPayload: Encoder[Payload] = deriveEncoder[Payload]
    implicit val decodeTestPayload: Decoder[Payload] = deriveDecoder[Payload]
  }

}

object NS2 {

  case class AnotherPayload(message: String) extends Algebraic

  object AnotherPayload {
    implicit val encodeAnotherPayload: Encoder[AnotherPayload] = deriveEncoder[AnotherPayload]
    implicit val decodeAnotherPayload: Decoder[AnotherPayload] = deriveDecoder[AnotherPayload]
  }

}
object Algebraic {
  implicit val encodePolymorphic: Encoder[Algebraic] = deriveEncoder[Algebraic]
  implicit val decodePolymorphic: Decoder[Algebraic] = deriveDecoder[Algebraic]
}

def test(t: Algebraic): Unit = {
  val encoded = t.asJson.noSpaces
  println(s"Encoded:\n$encoded\n")
  val parsed = parse(encoded)
  println(s"Parsed:\n$parsed\n")
  val restored = parsed.map(_.as[Algebraic])
  println(s"Restored:\n$restored\n")
}

test(NS1.Payload())
test(NS2.AnotherPayload("hi"))
```

Output:
 
```
Encoded:
{"Payload":{"localTime":"18:13:31.942072"}}

Parsed:
Right({
  "Payload" : {
    "localTime" : "18:13:31.942072"
  }
})

Restored:
Right(Right(Payload(18:13:31.942072)))

Encoded:
{"AnotherPayload":{"message":"hi"}}

Parsed:
Right({
  "AnotherPayload" : {
    "message" : "hi"
  }
})

Restored:
Right(Right(AnotherPayload(hi)))
```

#### Identifiers

Identifiers codec just invokes `.toString` and `.parse` to serialize/deserialize Identifiers.

Please check @ref[identifier codegen example](cogen.md#id-identifier) for additional details.

Full example:

```scala
final case class CompanyId(value: java.util.UUID) {
  override def toString: String = {
    import izumi.idealingua.runtime.model.IDLIdentifier._
    val suffix = Seq(this.value).map(part => escape(part.toString)).mkString(":")
    s"CompanyId#$suffix"
  }
}

trait CompanyIdCirce {
  import _root_.io.circe.{ Encoder, Decoder }
  implicit val encodeCompanyId: Encoder[CompanyId] = Encoder.encodeString.contramap(_.toString)
  implicit val decodeCompanyId: Decoder[CompanyId] = Decoder.decodeString.map(CompanyId.parse)
}

object CompanyId extends CompanyIdCirce {
  def parse(s: String): CompanyId = {
    import izumi.idealingua.runtime.model.IDLIdentifier._
    val withoutPrefix = s.substring(s.indexOf("#") + 1)
    val parts = withoutPrefix.split(":").map(part => unescape(part))
    CompanyId(parsePart[java.util.UUID](parts(0), classOf[java.util.UUID]))
  }
  implicit class CompanyIdExtensions(_value: CompanyId)
}
```

## Enumerations

Identifiers codec just invokes `.toString` and `.parse` same way as it implemented for Identifiers.
