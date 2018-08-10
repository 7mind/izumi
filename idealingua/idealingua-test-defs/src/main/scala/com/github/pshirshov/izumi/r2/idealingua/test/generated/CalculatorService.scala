//package com.github.pshirshov.izumi.r2.idealingua.test.generated
//
//import com.github.pshirshov.izumi.idealingua.runtime.circe._
//import com.github.pshirshov.izumi.idealingua.runtime.rpc._
//import io.circe.Decoder.Result
//import io.circe._
//import io.circe.generic.semiauto._
//
//import scala.language.higherKinds
//import scala.reflect.ClassTag
//
//trait CalculatorServiceClient[R[_]] extends IRTWithResultType[R] {
//  def sum(a: Int, b: Int): Result[Int]
//}
//
//
//trait CalculatorService[R[_], C] extends IRTWithResultType[R] {
//  def sum(ctx: C, a: Int, b: Int): Result[Int]
//}
//
//
//trait CalculatorServiceWrapped[R[_], C] extends IRTWithResultType[R] {
//
//  import CalculatorServiceWrapped._
//
//  def sum(ctx: C, input: SumInput): Result[SumOutput]
//}
//
//object CalculatorServiceWrapped
//  extends IRTIdentifiableServiceDefinition
//    with IRTWrappedServiceDefinition
//    with IRTWrappedUnsafeServiceDefinition
//    with IRTCirceWrappedServiceDefinition {
//
//  sealed trait CalculatorServiceInput extends AnyRef with Product
//
//  final case class SumInput(a: Int, b: Int) extends CalculatorServiceInput
//
//  sealed trait CalculatorServiceOutput extends Any with Product
//
//  final case class SumOutput(value: Int) extends AnyVal with CalculatorServiceOutput
//
//  override type Input = CalculatorServiceInput
//  override type Output = CalculatorServiceOutput
//
//
//  override type ServiceServer[R[_], C] = CalculatorService[R, C]
//  override type ServiceClient[R[_]] = CalculatorServiceClient[R]
//
//  override def client[R[_] : IRTResult](dispatcher: IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R]): CalculatorServiceClient[R] = {
//    new CalculatorServiceWrapped.PackingDispatcher.Impl[R](dispatcher)
//  }
//
//
//  override def clientUnsafe[R[_] : IRTResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]): CalculatorServiceClient[R] = {
//    client(new SafeToUnsafeBridge[R](dispatcher))
//  }
//
//  override def server[R[_] : IRTResult, C](service: CalculatorService[R, C]): IRTDispatcher[IRTInContext[CalculatorServiceInput, C], CalculatorServiceOutput, R] = {
//    new CalculatorServiceWrapped.UnpackingDispatcher.Impl[R, C](service)
//  }
//
//
//  override def serverUnsafe[R[_] : IRTResult, C](service: CalculatorService[R, C]): IRTUnsafeDispatcher[C, R] = {
//    new CalculatorServiceWrapped.UnpackingDispatcher.Impl[R, C](service)
//  }
//
//
//  object SumInput {
//    implicit val encodeTestPayload: Encoder[SumInput] = deriveEncoder
//    implicit val decodeTestPayload: Decoder[SumInput] = deriveDecoder
//  }
//
//  object SumOutput {
//    implicit val encodeTestPayload: Encoder[SumOutput] = deriveEncoder
//    implicit val decodeTestPayload: Decoder[SumOutput] = deriveDecoder
//  }
//
//  val serviceId = IRTServiceId("CalculatorService")
//
//  trait PackingDispatcher[R[_]]
//    extends CalculatorServiceClient[R]
//      with IRTWithResult[R] {
//    def dispatcher: IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R]
//
//    def sum(a: Int, b: Int): Result[Int] = {
//      val packed = SumInput(a, b)
//      val dispatched = dispatcher.dispatch(packed)
//      _ServiceResult.map(dispatched) {
//        case o: SumOutput =>
//          o.value
//        case o =>
//          throw new IRTTypeMismatchException(s"Unexpected input in CalculatorServiceDispatcherPacking.sum: $o", o, None)
//      }
//    }
//  }
//
//  class SafeToUnsafeBridge[R[_] : IRTResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]) extends IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R] with IRTWithResult[R] {
//    override protected def _ServiceResult: IRTResult[R] = implicitly
//
//    import IRTResult._
//
//    override def dispatch(input: CalculatorServiceInput): Result[CalculatorServiceOutput] = {
//      dispatcher.dispatch(IRTMuxRequest(input, toMethodId(input))).map {
//        case IRTMuxResponse(t: CalculatorServiceOutput, _) =>
//          t
//        case o =>
//          throw new IRTTypeMismatchException(s"Unexpected output in CalculatorServiceSafeToUnsafeBridge.dispatch: $o", o, None)
//      }
//    }
//  }
//
//  object PackingDispatcher {
//
//    class Impl[R[_] : IRTResult](val dispatcher: IRTDispatcher[CalculatorServiceInput, CalculatorServiceOutput, R]) extends PackingDispatcher[R] {
//      override protected def _ServiceResult: IRTResult[R] = implicitly
//    }
//
//  }
//
//  trait UnpackingDispatcher[R[_], C]
//    extends CalculatorServiceWrapped[R, C]
//      with IRTGeneratedUnpackingDispatcher[C, R, CalculatorServiceInput, CalculatorServiceOutput]
//      with IRTWithResult[R] {
//    def service: CalculatorService[R, C]
//
//    def sum(ctx: C, input: SumInput): Result[SumOutput] = {
//      val result = service.sum(ctx, input.a, input.b)
//      _ServiceResult.map(result)(SumOutput.apply)
//    }
//
//    def dispatch(input: IRTInContext[CalculatorServiceInput, C]): Result[CalculatorServiceOutput] = {
//      input match {
//        case IRTInContext(v: SumInput, c) =>
//          _ServiceResult.map(sum(c, v))(v => v) // upcast
//      }
//    }
//
//    def identifier: IRTServiceId = serviceId
//
//
//    protected def toMethodId(v: CalculatorServiceInput): IRTMethod = CalculatorServiceWrapped.toMethodId(v)
//
//    protected def toMethodId(v: CalculatorServiceOutput): IRTMethod = CalculatorServiceWrapped.toMethodId(v)
//
//
//    import scala.reflect._
//
//    override protected def inputTag: ClassTag[CalculatorServiceInput] = classTag
//
//    override protected def outputTag: ClassTag[CalculatorServiceOutput] = classTag
//
//    protected def toZeroargBody(v: IRTMethod): Option[CalculatorServiceInput] = {
//      v match {
//        case _ =>
//          None
//      }
//    }
//  }
//
//  object UnpackingDispatcher {
//
//    class Impl[R[_] : IRTResult, C](val service: CalculatorService[R, C]) extends UnpackingDispatcher[R, C] {
//      override protected def _ServiceResult: IRTResult[R] = implicitly
//    }
//
//  }
//
//  def toMethodId(v: CalculatorServiceInput): IRTMethod = {
//    v match {
//      case _: SumInput => IRTMethod(serviceId, IRTMethodId("sum"))
//    }
//  }
//
//  def toMethodId(v: CalculatorServiceOutput): IRTMethod = {
//    v match {
//      case _: SumOutput => IRTMethod(serviceId, IRTMethodId("sum"))
//    }
//  }
//
//
//  override def codecProvider: IRTMuxingCodecProvider = CodecProvider
//
//  object CodecProvider extends IRTMuxingCodecProvider {
//
//    import io.circe._
//    import io.circe.syntax._
//
//    override def requestEncoders: List[PartialFunction[IRTReqBody, Json]] = List(
//      {
//        case IRTReqBody(v: SumInput) =>
//          v.asJson
//      }
//    )
//
//    override def responseEncoders: List[PartialFunction[IRTResBody, Json]] = List(
//      {
//        case IRTResBody(v: SumOutput) =>
//          v.asJson
//      }
//    )
//
//    override def requestDecoders: List[PartialFunction[IRTCursorForMethod, Result[IRTReqBody]]] = List(
//      {
//        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId.value == "sum" =>
//          packet.as[SumInput].map(v => IRTReqBody(v))
//      }
//    )
//
//    override def responseDecoders: List[PartialFunction[IRTCursorForMethod, Result[IRTResBody]]] = List(
//      {
//        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId.value == "sum" =>
//          packet.as[SumOutput].map(v => IRTResBody(v))
//      }
//    )
//  }
//
//}
