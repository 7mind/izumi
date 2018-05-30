package com.github.pshirshov.izumi.r2.idealingua.test.generated

import com.github.pshirshov.izumi.idealingua.runtime.circe._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.semiauto._

import scala.language.higherKinds

trait GreeterServiceClient[R[_]] extends IRTWithResultType[R] {
  def greet(name: String, surname: String): Result[String]
  def sayhi(): Result[String]

}

trait GreeterService[R[_], C] extends IRTWithResultType[R] {
  def greet(ctx: C, name: String, surname: String): Result[String]
  def sayhi(ctx: C): Result[String]
}

//trait GreeterServiceCtx[R[_], C] extends IRTWithResultType[R] with IRTWithContext[C] {
//  def greet(context: C, name: String, surname: String): Result[String]
//  def sayhi(context: C): Result[String]
//}





trait GreeterServiceWrapped[R[_], C] extends IRTWithResultType[R] with IRTWithContext[C] {

  import GreeterServiceWrapped._

  def greet(ctx: C, input: GreetInput): Result[GreetOutput]
}

object GreeterServiceWrapped
  extends IRTIdentifiableServiceDefinition
    with IRTWrappedServiceDefinition
    with IRTWrappedUnsafeServiceDefinition
    with IRTCirceWrappedServiceDefinition {

  sealed trait GreeterServiceInput extends AnyRef with Product

  final case class GreetInput(name: String, surname: String) extends GreeterServiceInput
  final case class SayHiInput() extends GreeterServiceInput

  sealed trait GreeterServiceOutput extends AnyRef with Product

  final case class GreetOutput(value: String) extends GreeterServiceOutput
  final case class SayHiOutput(value: String) extends GreeterServiceOutput


  override type Input = GreeterServiceInput
  override type Output = GreeterServiceOutput

  override type ServiceServer[R[_], C] = GreeterService[R, C]
  override type ServiceClient[R[_]] = GreeterServiceClient[R]


  override def client[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[GreeterServiceInput, GreeterServiceOutput, R]): GreeterServiceClient[R] = {
    new GreeterServiceWrapped.PackingDispatcher.Impl[R](dispatcher)
  }


  override def server[R[_] : IRTServiceResult, C](service: GreeterService[R, C]): IRTDispatcher[IRTInContext[GreeterServiceInput, C], GreeterServiceOutput, R] = {
    new GreeterServiceWrapped.UnpackingDispatcher.Impl[R, C](service)
  }


  override def serverUnsafe[R[_] : IRTServiceResult, C](service: GreeterService[R, C]): IRTUnsafeDispatcher[C, R] = {
    new GreeterServiceWrapped.UnpackingDispatcher.Impl[R, C](service)
  }

  override def clientUnsafe[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]): GreeterServiceClient[R] = {
    client(new SafeToUnsafeBridge[R](dispatcher))
  }

  object SayHiInput {
    implicit val encode: Encoder[SayHiInput] = deriveEncoder
    implicit val decode: Decoder[SayHiInput] = deriveDecoder
  }

  object SayHiOutput {
    implicit val encode: Encoder[SayHiOutput] = deriveEncoder
    implicit val decode: Decoder[SayHiOutput] = deriveDecoder
  }

  object GreetInput {
    implicit val encode: Encoder[GreetInput] = deriveEncoder
    implicit val decode: Decoder[GreetInput] = deriveDecoder
  }

  object GreetOutput {
    implicit val encode: Encoder[GreetOutput] = deriveEncoder
    implicit val decode: Decoder[GreetOutput] = deriveDecoder
  }

  val serviceId =  IRTServiceId("GreeterService")

  trait PackingDispatcher[R[_]]
    extends GreeterServiceClient[R]
      with IRTWithResult[R] {
    def dispatcher: IRTDispatcher[GreeterServiceInput, GreeterServiceOutput, R]


    override def sayhi(): Result[String] = {
      val packed = SayHiInput()
      val dispatched = dispatcher.dispatch(packed)
      _ServiceResult.map(dispatched) {
        case o: SayHiOutput =>
          o.value
        case o =>
          throw new IRTTypeMismatchException(s"Unexpected input in GreeterServiceDispatcherPacking.sayhi: $o", o, None)
      }

    }

    def greet(name: String, surname: String): Result[String] = {
      val packed = GreetInput(name, surname)
      val dispatched = dispatcher.dispatch(packed)
      _ServiceResult.map(dispatched) {
        case o: GreetOutput =>
          o.value
        case o =>
          throw new IRTTypeMismatchException(s"Unexpected input in GreeterServiceDispatcherPacking.greet: $o", o, None)
      }
    }
  }

  class SafeToUnsafeBridge[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]) extends IRTDispatcher[GreeterServiceInput, GreeterServiceOutput, R] with IRTWithResult[R] {
    override protected def _ServiceResult: IRTServiceResult[R] = implicitly

    import IRTServiceResult._

    override def dispatch(input: GreeterServiceInput): Result[GreeterServiceOutput] = {
      dispatcher.dispatch(IRTMuxRequest(input : Product, toMethodId(input))).map {
        case IRTMuxResponse(t: GreeterServiceOutput, _) =>
          t
        case o =>
          throw new IRTTypeMismatchException(s"Unexpected output in GreeterServiceSafeToUnsafeBridge.dispatch: $o", o, None)
      }
    }
  }

  object PackingDispatcher {

    class Impl[R[_] : IRTServiceResult](val dispatcher: IRTDispatcher[GreeterServiceInput, GreeterServiceOutput, R]) extends PackingDispatcher[R] {
      override protected def _ServiceResult: IRTServiceResult[R] = implicitly
    }

  }

  trait UnpackingDispatcher[R[_], C]
    extends GreeterServiceWrapped[R, C]
      with IRTDispatcher[IRTInContext[GreeterServiceInput, C], GreeterServiceOutput, R]
      with IRTUnsafeDispatcher[C, R]
      with IRTWithResult[R] {
    def service: GreeterService[R, C]

    def greet(context: Context, input: GreetInput): Result[GreetOutput] = {
      assert(input != null)
      val result = service.greet(context, input.name, input.surname)
      _ServiceResult.map(result)(GreetOutput.apply)
    }

    def sayhi(context: Context, input: SayHiInput): Result[GreetOutput] = {
      assert(input != null)
      val result = service.sayhi(context)
      _ServiceResult.map(result)(GreetOutput.apply)
    }

    def dispatch(input: IRTInContext[GreeterServiceInput, Context]): Result[GreeterServiceOutput] = {
      input match {
        case IRTInContext(v: GreetInput, c) =>
          _ServiceResult.map(greet(c, v))(v => v) // upcast
        case IRTInContext(v: SayHiInput, c) =>
          _ServiceResult.map(sayhi(c, v))(v => v) // upcast
      }
    }

    override def identifier: IRTServiceId = serviceId

    private def toZeroargBody(v: IRTMethod): Option[GreeterServiceInput] = {
      v match {
        case IRTMethod(`serviceId`, IRTMethodId("sayhi")) =>
          Some(SayHiInput())
        case _ =>
          None
      }
    }

    private def dispatchZeroargUnsafe(input: IRTInContext[IRTMethod, C]): Option[Result[IRTMuxResponse[Product]]] = {
      toZeroargBody(input.value).map(b => _ServiceResult.map(dispatch(IRTInContext(b, input.context)))(v => IRTMuxResponse(v, toMethodId(v))))
    }


    override def dispatchUnsafe(input: IRTInContext[IRTMuxRequest[Product], Context]): Option[Result[IRTMuxResponse[Product]]] = {
      input.value.v match {
        case v: GreeterServiceInput =>
          Option(_ServiceResult.map(dispatch(IRTInContext(v, input.context)))(v => IRTMuxResponse(v, toMethodId(v))))

        case _ =>
          dispatchZeroargUnsafe(IRTInContext(input.value.method, input.context))
      }
    }
  }

  def toMethodId(v: GreeterServiceInput): IRTMethod  = {
    v match {
      case _: GreetInput => IRTMethod(serviceId, IRTMethodId("greet"))
      case _: SayHiInput => IRTMethod(serviceId, IRTMethodId("sayhi"))
    }
  }

  def toMethodId(v: GreeterServiceOutput): IRTMethod  = {
    v match {
      case _: GreetOutput => IRTMethod(serviceId, IRTMethodId("greet"))
      case _: SayHiOutput => IRTMethod(serviceId, IRTMethodId("sayhi"))
    }
  }



  object UnpackingDispatcher {

    class Impl[R[_] : IRTServiceResult, C](val service: GreeterService[R, C]) extends UnpackingDispatcher[R, C] {
      override protected def _ServiceResult: IRTServiceResult[R] = implicitly
    }

  }


  override def codecProvider: IRTMuxingCodecProvider = CodecProvider

  object CodecProvider extends IRTMuxingCodecProvider {

    import io.circe._
    import io.circe.syntax._

    override def requestEncoders: List[PartialFunction[IRTReqBody, Json]] = List(
      {
        case IRTReqBody(v: GreetInput) =>
          v.asJson
        case IRTReqBody(v: SayHiInput) =>
          v.asJson
      }
    )

    override def responseEncoders: List[PartialFunction[IRTResBody, Json]] = List(
      {
        case IRTResBody(v: GreetOutput) =>
          v.asJson
        case IRTResBody(v: SayHiOutput) =>
          v.asJson
      }
    )


    override def requestDecoders: List[PartialFunction[IRTCursorForMethod, Result[IRTReqBody]]] = List(
      {
        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("sayhi") =>
          packet.as[SayHiInput].map(v => IRTReqBody(v))
        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("greet") =>
          packet.as[GreetInput].map(v => IRTReqBody(v))
      }
    )

    override def responseDecoders: List[PartialFunction[IRTCursorForMethod, Result[IRTResBody]]] =  List(
      {
        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("sayhi") =>
          packet.as[SayHiOutput].map(v => IRTResBody(v))
        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("greet") =>
          packet.as[GreetOutput].map(v => IRTResBody(v))
      }
    )
  }
}
