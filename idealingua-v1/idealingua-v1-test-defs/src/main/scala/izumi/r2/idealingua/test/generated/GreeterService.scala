package izumi.r2.idealingua.test.generated

import izumi.functional.bio.BIO
import izumi.idealingua.runtime.rpc._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

trait GreeterServiceServer[Or[+ _, + _], C] {
  type Just[T] = Or[Nothing, T]

  def greet(ctx: C, name: String, surname: String): Just[String]

  def sayhi(ctx: C): Just[String]

  def nothing(ctx: C): Just[String]

  def alternative(ctx: C): Or[Long, String]
}

trait GreeterServiceClient[Or[+ _, + _]] {
  type Just[T] = Or[Nothing, T]

  def greet(name: String, surname: String): Just[String]

  def sayhi(): Just[String]

  def nothing(): Just[Unit]

  def alternative(): Or[Long, String]
}

class GreeterServiceClientWrapped[F[+ _, + _] : BIO](dispatcher: IRTDispatcher[F])
  extends GreeterServiceClient[F] {

  val R: BIO[F] = implicitly
  import izumi.r2.idealingua.test.generated.{GreeterServiceMethods => _M}

  override def greet(name: String, surname: String): R.Just[String] = {
    R.redeem(dispatcher.dispatch(IRTMuxRequest(IRTReqBody(GreeterServiceMethods.greet.Input(name, surname)), GreeterServiceMethods.greet.id)))( { err => R.terminate(err) }, {
      case IRTMuxResponse(IRTResBody(v: _M.greet.Output), method) if method == GreeterServiceMethods.greet.id =>
        R.pure(v.value)
      case v =>
        R.terminate(new RuntimeException(s"wtf: $v, ${v.getClass}"))
    })
  }

  override def alternative(): R.Or[Long, String] = {
    R.redeem(dispatcher.dispatch(IRTMuxRequest(IRTReqBody(GreeterServiceMethods.alternative.Input()), GreeterServiceMethods.alternative.id)))({
        err => R.terminate(err)
      }, {
        case IRTMuxResponse(IRTResBody(v), method) if method == GreeterServiceMethods.alternative.id =>
          v match {
            case va: GreeterServiceMethods.alternative.AlternativeOutput.Failure =>
              R.fail(va.value)
            case va: GreeterServiceMethods.alternative.AlternativeOutput.Success =>
              R.pure(va.value)
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

object GreeterServiceClientWrapped extends IRTWrappedClient {
  val allCodecs: Map[IRTMethodId, IRTCirceMarshaller] = {
    Map(
      GreeterServiceMethods.greet.id -> GreeterServerMarshallers.greet
      , GreeterServiceMethods.alternative.id -> GreeterServerMarshallers.alternative
    )
  }
}

class GreeterServiceServerWrapped[F[+ _, + _] : BIO, C](service: GreeterServiceServer[F, C])
  extends IRTWrappedService[F, C] {

  val F: BIO[F] = implicitly

  object greet extends IRTMethodWrapper[F, C] {

    import GreeterServiceMethods.greet._

    override val signature: GreeterServiceMethods.greet.type = GreeterServiceMethods.greet
    override val marshaller: GreeterServerMarshallers.greet.type = GreeterServerMarshallers.greet

    override def invoke(ctx: C, input: Input): Just[Output] = {
      F.map(service.greet(ctx, input.name, input.surname))(v => Output(v))
    }
  }

  object alternative extends IRTMethodWrapper[F, C] {

    import GreeterServiceMethods.alternative._

    override val signature: GreeterServiceMethods.alternative.type = GreeterServiceMethods.alternative
    override val marshaller: GreeterServerMarshallers.alternative.type = GreeterServerMarshallers.alternative

    override def invoke(ctx: C, input: Input): Just[Output] = {
      F.redeem(service.alternative(ctx))(err => F.pure(AlternativeOutput.Failure(err)), succ => F.pure(AlternativeOutput.Success(succ)))
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

  object greet extends IRTCirceMarshaller {

    import GreeterServiceMethods.greet._


    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Output) => value.asJson
    }

    override def decodeRequest[Or[+ _, + _] : BIO]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTReqBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded[Or, IRTReqBody](packet.as[Input].map(v => IRTReqBody(v)))
    }

    override def decodeResponse[Or[+ _, + _] : BIO]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTResBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded[Or, IRTResBody](packet.as[Output].map(v => IRTResBody(v)))
    }
  }

  object alternative extends IRTCirceMarshaller {

    import GreeterServiceMethods.alternative._

    override def encodeRequest: PartialFunction[IRTReqBody, Json] = {
      case IRTReqBody(value: Input) => value.asJson
    }

    override def encodeResponse: PartialFunction[IRTResBody, Json] = {
      case IRTResBody(value: Output) => value.asJson
    }

    override def decodeRequest[Or[+ _, + _] : BIO]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTReqBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded[Or, IRTReqBody](packet.as[Input].map(v => IRTReqBody(v)))
    }

    override def decodeResponse[Or[+ _, + _] : BIO]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTResBody]] = {
      case IRTJsonBody(m, packet) if m == id =>
        decoded[Or, IRTResBody](packet.as[Output].map(v => IRTResBody(v)))
    }

  }

}
