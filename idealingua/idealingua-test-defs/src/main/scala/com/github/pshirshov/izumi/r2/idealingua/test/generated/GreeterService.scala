package com.github.pshirshov.izumi.r2.idealingua.test.generated

import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTResult._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

import scala.language.higherKinds

trait GreeterServiceServer[Or[_, _], C] {
  type Just[T] = Or[Nothing, T]

  def greet(ctx: C, name: String, surname: String): Just[String]

  def sayhi(ctx: C): Just[String]

  def nothing(ctx: C): Just[String]

  def alternative(ctx: C): Or[Long, String]
}

trait GreeterServiceClient[Or[_, _]] {
  type Just[T] = Or[Nothing, T]

  def greet(name: String, surname: String): Just[String]

  def sayhi(): Just[String]

  def nothing(): Just[Unit]

  def alternative(): Or[Long, String]
}

class GreeterServiceClientWrapped[R[+_, +_] : IRTResult](dispatcher: IRTDispatcher[R])
  extends GreeterServiceClient[R] {

  val R: IRTResult[R] = implicitly[IRTResult[R]]

  override def greet(name: String, surname: String): R.Just[String] = {
    dispatcher
      .dispatch(IRTMuxRequest(IRTReqBody(GreeterServiceMethods.greet.Input(name, surname)), GreeterServiceMethods.greet.id))
      .redeem({ err => R.terminate(err) }, { case IRTMuxResponse(IRTResBody(v: GreeterServiceMethods.greet.Output), method) if method == GreeterServiceMethods.greet.id =>
        R.point(v.value)
      case v =>
        R.terminate(new RuntimeException(s"wtf: $v, ${v.getClass}"))
      })

  }


  override def alternative(): R.Or[Long, String] = {
    dispatcher.dispatch(IRTMuxRequest(IRTReqBody(GreeterServiceMethods.alternative.Input()), GreeterServiceMethods.alternative.id))
      .redeem({
        err => R.terminate(err)
      }, {
        case IRTMuxResponse(IRTResBody(v), method) if method == GreeterServiceMethods.alternative.id =>
          v match {
            case va : GreeterServiceMethods.alternative.AlternativeOutput.Failure =>
              R.fail(va.value)
            case va : GreeterServiceMethods.alternative.AlternativeOutput.Success =>
              R.point(va.value)
            case _ =>
              R.terminate(new RuntimeException(s"wtf: $v, ${v.getClass}"))
          }
        case _ =>
          R.terminate(new RuntimeException())
      })
  }

  override def sayhi(): R.Just[String] = ???

  override def nothing(): R.Just[Unit] = ???
}

object GreeterServiceClientWrapped {
  class Codecs[R[+_, +_] : IRTResult] extends IRTWrappedClient[R] {
    val allCodecs: Map[IRTMethodId, IRTCirceMarshaller[R]] = {
      Map(
        GreeterServiceMethods.greet.id -> new GreeterServerMarshallers.greet[R]
        , GreeterServiceMethods.alternative.id -> new GreeterServerMarshallers.alternative[R]
      )
    }
  }
}

class GreeterServiceServerWrapped[F[+_, +_] : IRTResult, C](service: GreeterServiceServer[F, C])
  extends IRTWrappedService[F, C] {

  val F: IRTResult[F] = implicitly[IRTResult[F]]

  object greet extends IRTMethodWrapper[F, C] {

    import GreeterServiceMethods.greet._

    override val signature: GreeterServiceMethods.greet.type = GreeterServiceMethods.greet
    override val marshaller: GreeterServerMarshallers.greet[F] = new GreeterServerMarshallers.greet[F]

    override def invoke(ctx: C, input: Input): Just[Output] = {
      service.greet(ctx, input.name, input.surname)
        .map(v => Output(v))
    }
  }

  object alternative extends IRTMethodWrapper[F, C] {

    import GreeterServiceMethods.alternative._

    override val signature: GreeterServiceMethods.alternative.type = GreeterServiceMethods.alternative
    override val marshaller: GreeterServerMarshallers.alternative[F] = new GreeterServerMarshallers.alternative[F]

    override def invoke(ctx: C, input: Input): Just[Output] = {
      service.alternative(ctx)
        .redeem(err => F.point(AlternativeOutput.Failure(err)), succ => F.point(AlternativeOutput.Success(succ)))
    }
  }


  override def serviceId: IRTServiceId = GreeterServiceMethods.serviceId

  val allMethods: Map[IRTMethodId, IRTMethodWrapper[F, C]] = {
    Seq(
      greet
      , alternative
    )
      .map(m => m.signature.id -> m).toMap
  }
}




object GreeterServiceMethods {
  val serviceId: IRTServiceId = IRTServiceId("GreeterService")

  object greet extends IRTMethodSignature {
    val id: IRTMethodId = IRTMethodId(serviceId, IRTMethodName("greet"))

    case class Input(name: String, surname: String)

    object Input {
      implicit val encode: Encoder[Input] = deriveEncoder
      implicit val decode: Decoder[Input] = deriveDecoder
    }

    case class Output(value: String)

    object Output {
      implicit val encode: Encoder[Output] = deriveEncoder
      implicit val decode: Decoder[Output] = deriveDecoder
    }

  }

  object alternative extends IRTMethodSignature {
    val id: IRTMethodId = IRTMethodId(serviceId, IRTMethodName("alternative"))

    type Output = AlternativeOutput

    case class Input()

    sealed trait AlternativeOutput extends Product

    object AlternativeOutput {

      final case class Failure(value: Long) extends AlternativeOutput

      final case class Success(value: String) extends AlternativeOutput

      implicit val encode: Encoder[AlternativeOutput] = deriveEncoder
      implicit val decode: Decoder[AlternativeOutput] = deriveDecoder
    }

    object Input {
      implicit val encode: Encoder[Input] = deriveEncoder
      implicit val decode: Decoder[Input] = deriveDecoder
    }

  }

}

object GreeterServerMarshallers {

  class greet[R[+_, +_] : IRTResult] extends IRTCirceMarshaller[R] {

    import GreeterServiceMethods.greet._


    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Output) => value.asJson
    }

    override def decodeRequest: PartialFunction[IRTJsonBody, Just[IRTReqBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded(packet.as[Input].map(v => IRTReqBody(v)))
    }

    override def decodeResponse: PartialFunction[IRTJsonBody, Just[IRTResBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded(packet.as[Output].map(v => IRTResBody(v)))
    }

    override protected def decoded[V](result: Either[DecodingFailure, V]): Just[V] = implicitly[IRTResult[R]].maybe(result)
  }

  class alternative[R[+_, +_] : IRTResult] extends IRTCirceMarshaller[R] {

    import GreeterServiceMethods.alternative._

    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Output) => value.asJson
    }

    override def decodeRequest: PartialFunction[IRTJsonBody, Just[IRTReqBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded(packet.as[Input].map(v => IRTReqBody(v)))
    }

    override def decodeResponse: PartialFunction[IRTJsonBody, Just[IRTResBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded(packet.as[Output].map(v => IRTResBody(v)))
    }

    override protected def decoded[V](result: Either[DecodingFailure, V]): Just[V] = implicitly[IRTResult[R]].maybe(result)
  }

}
