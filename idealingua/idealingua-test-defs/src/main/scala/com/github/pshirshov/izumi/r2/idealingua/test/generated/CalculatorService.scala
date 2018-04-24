package com.github.pshirshov.izumi.r2.idealingua.test.generated

import com.github.pshirshov.izumi.idealingua.runtime.circe._
import com.github.pshirshov.izumi.r2.idealingua.runtime.rpc._
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.semiauto._

import scala.language.higherKinds

trait CalculatorServiceClient[R[_]] extends IRTWithResultType[R] {
  def sum(a: Int, b: Int): Result[Int]
}


trait CalculatorService[R[_], C] extends IRTWithResultType[R] {
  def sum(ctx: C, a: Int, b: Int): Result[Int]
}


trait CalculatorServiceWrapped[R[_], C] extends IRTWithResultType[R] {

  import CalculatorServiceWrapped._

  def sum(ctx: C, input: SumInput): Result[SumOutput]
}

object CalculatorServiceWrapped
  extends IRTIdentifiableServiceDefinition
    with IRTWrappedServiceDefinition
    with IRTWrappedUnsafeServiceDefinition
    with IRTCirceWrappedServiceDefinition {

  sealed trait CalculatorServiceInput extends AnyRef with Product

  case class SumInput(a: Int, b: Int) extends CalculatorServiceInput

  sealed trait CalculatorServiceOutput extends Any with Product

  case class SumOutput(value: Int) extends AnyVal with CalculatorServiceOutput

  override type Input = CalculatorServiceInput
  override type Output = CalculatorServiceOutput


  override type ServiceServer[R[_], C] = CalculatorService[R, C]
  override type ServiceClient[R[_]] = CalculatorServiceClient[R]

  override def client[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R]): CalculatorServiceClient[R] = {
    new CalculatorServiceWrapped.PackingDispatcher.Impl[R](dispatcher)
  }


  override def clientUnsafe[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]): CalculatorServiceClient[R] = {
    client(new SafeToUnsafeBridge[R](dispatcher))
  }

  override def server[R[_] : IRTServiceResult, C](service: CalculatorService[R, C]): IRTDispatcher[IRTInContext[CalculatorServiceInput, C], CalculatorServiceOutput, R] = {
    new CalculatorServiceWrapped.UnpackingDispatcher.Impl[R, C](service)
  }


  override def serverUnsafe[R[_] : IRTServiceResult, C](service: CalculatorService[R, C]): IRTUnsafeDispatcher[C, R] = {
    new CalculatorServiceWrapped.UnpackingDispatcher.Impl[R, C](service)
  }


  object SumInput {
    implicit val encodeTestPayload: Encoder[SumInput] = deriveEncoder
    implicit val decodeTestPayload: Decoder[SumInput] = deriveDecoder
  }

  object SumOutput {
    implicit val encodeTestPayload: Encoder[SumOutput] = deriveEncoder
    implicit val decodeTestPayload: Decoder[SumOutput] = deriveDecoder
  }

  object CalculatorServiceInput {
    implicit val encodeTestPayload: Encoder[CalculatorServiceWrapped.CalculatorServiceInput] = deriveEncoder
    implicit val decodeTestPayload: Decoder[CalculatorServiceWrapped.CalculatorServiceInput] = deriveDecoder
  }

  object CalculatorServiceOutput extends CalculatorServiceWrapped.CalculatorServiceOutputCirce
  trait CalculatorServiceOutputCirce extends _root_.io.circe.java8.time.TimeInstances {
    import _root_.io.circe._
    import _root_.io.circe.generic.semiauto._
    implicit val encodeInTestService: Encoder[CalculatorServiceOutput] = deriveEncoder[CalculatorServiceOutput]
    implicit val decodeInTestService: Decoder[CalculatorServiceOutput] = deriveDecoder[CalculatorServiceOutput]
  }

//  object CalculatorServiceOutput {
//    implicit val encodeTestPayload: Encoder[CalculatorServiceWrapped.CalculatorServiceOutput] = deriveEncoder
//    implicit val decodeTestPayload: Decoder[CalculatorServiceWrapped.CalculatorServiceOutput] = deriveDecoder
//  }

  val serviceId = IRTServiceId("CalculatorService")

  trait PackingDispatcher[R[_]]
    extends CalculatorServiceClient[R]
      with IRTWithResult[R] {
    def dispatcher: IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R]

    def sum(a: Int, b: Int): Result[Int] = {
      val packed = SumInput(a, b)
      val dispatched = dispatcher.dispatch(packed)
      _ServiceResult.map(dispatched) {
        case o: SumOutput =>
          o.value
        case o =>
          throw new IRTTypeMismatchException(s"Unexpected input in CalculatorServiceDispatcherPacking.sum: $o", o, None)
      }
    }
  }

  class SafeToUnsafeBridge[R[_] : IRTServiceResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]) extends IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R] with IRTWithResult[R] {
    override protected def _ServiceResult: IRTServiceResult[R] = implicitly

    import IRTServiceResult._

    override def dispatch(input: CalculatorServiceInput): Result[CalculatorServiceOutput] = {
      dispatcher.dispatch(IRTMuxRequest(input, toMethodId(input))).map {
        case IRTMuxResponse(t: CalculatorServiceOutput, _) =>
          t
        case o =>
          throw new IRTTypeMismatchException(s"Unexpected output in CalculatorServiceSafeToUnsafeBridge.dispatch: $o", o, None)
      }
    }
  }

  object PackingDispatcher {

    class Impl[R[_] : IRTServiceResult](val dispatcher: IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R]) extends PackingDispatcher[R] {
      override protected def _ServiceResult: IRTServiceResult[R] = implicitly
    }

  }

  trait UnpackingDispatcher[R[_], C]
    extends CalculatorServiceWrapped[R, C]
      with IRTDispatcher[IRTInContext[CalculatorServiceInput, C], CalculatorServiceOutput, R]
      with IRTUnsafeDispatcher[C, R]
      with IRTWithResult[R] {
    def service: CalculatorService[R, C]

    def sum(ctx: C, input: SumInput): Result[SumOutput] = {
      val result = service.sum(ctx, input.a, input.b)
      _ServiceResult.map(result)(SumOutput.apply)
    }

    def dispatch(input: IRTInContext[CalculatorServiceInput, C]): Result[CalculatorServiceOutput] = {
      input match {
        case IRTInContext(v: SumInput, c) =>
          _ServiceResult.map(sum(c, v))(v => v) // upcast
      }
    }

    override def identifier: IRTServiceId = serviceId

    private def toZeroargBody(v: IRTMethod): Option[CalculatorServiceInput] = {
      v match {
        case _ =>
          None
      }
    }

    private def dispatchZeroargUnsafe(input: IRTInContext[IRTMethod, C]): Option[Result[IRTMuxResponse[Product]]] = {
      toZeroargBody(input.value).map(b => _ServiceResult.map(dispatch(IRTInContext(b, input.context)))(v => IRTMuxResponse(v, toMethodId(v))))
    }

    override def dispatchUnsafe(input: IRTInContext[IRTMuxRequest[Product], C]): Option[Result[IRTMuxResponse[Product]]] = {
      input.value.v match {
        case v: CalculatorServiceInput =>
          Option(_ServiceResult.map(dispatch(IRTInContext(v, input.context)))(v => IRTMuxResponse(v, toMethodId(v))))

        case _ =>
          dispatchZeroargUnsafe(IRTInContext(input.value.method, input.context))
      }
    }
  }

  object UnpackingDispatcher {

    class Impl[R[_] : IRTServiceResult, C](val service: CalculatorService[R, C]) extends UnpackingDispatcher[R, C] {
      override protected def _ServiceResult: IRTServiceResult[R] = implicitly
    }

  }

  def toMethodId(v: CalculatorServiceInput): IRTMethod = {
    v match {
      case _: SumInput => IRTMethod(serviceId, IRTMethodId("sum"))
    }
  }

  def toMethodId(v: CalculatorServiceOutput): IRTMethod = {
    v match {
      case _: SumOutput => IRTMethod(serviceId, IRTMethodId("sum"))
    }
  }



  override def codecProvider: IRTMuxingCodecProvider = CodecProvider

  object CodecProvider extends IRTMuxingCodecProvider {

    import io.circe._
    import io.circe.syntax._

    override def requestEncoders: List[PartialFunction[IRTReqBody, Json]] = List(
      {
        case IRTReqBody(v: CalculatorServiceInput) =>
          v.asJson
      }
    )

    override def responseEncoders: List[PartialFunction[IRTResBody, Json]] = List(
      {
        case IRTResBody(v: CalculatorServiceOutput) =>
          v.asJson
      }
    )

    override def requestDecoders: List[PartialFunction[IRTCursorForMethod, Result[IRTReqBody]]] = List(
      {
        case IRTCursorForMethod(m, packet) if m.service == serviceId =>
          packet.as[CalculatorServiceInput].map(v => IRTReqBody(v))
      }
    )

    override def responseDecoders: List[PartialFunction[IRTCursorForMethod, Result[IRTResBody]]] = List(
      {
        case IRTCursorForMethod(m, packet) if m.service == serviceId =>
          packet.as[CalculatorServiceOutput].map(v => IRTResBody(v))
      }
    )
  }

}
