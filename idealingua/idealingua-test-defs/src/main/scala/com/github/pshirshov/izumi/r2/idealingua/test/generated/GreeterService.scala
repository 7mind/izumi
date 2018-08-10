//package com.github.pshirshov.izumi.r2.idealingua.test.generated
//
//import com.github.pshirshov.izumi.idealingua.runtime.circe._
//import com.github.pshirshov.izumi.idealingua.runtime.rpc._
//import io.circe.Decoder.Result
//import io.circe._
//import io.circe.generic.semiauto._
//
//import scala.language.higherKinds
//
//sealed trait HowBroken
//
//object HowBroken {
//
//  case object MissingServerHandler extends HowBroken
//
//  case object BrokenClientCode extends HowBroken
//
//}
//
////
////
//////trait GreeterServiceCtx[R[_], C] extends IRTWithResultType[R] with IRTWithContext[C] {
//////  def greet(context: C, name: String, surname: String): Just[String]
//////  def sayhi(context: C): Just[String]
//////}
////
////
////trait GreeterServiceWrapped[C] extends IRTResult2 {
////
////  import GreeterServiceWrapped._
////
////  def greet(ctx: C, input: GreetInput): Just[GreetOutput]
////}
//
//object GreeterServiceWrapped
//  extends IRTIdentifiableServiceDefinition
//    with IRTWrappedServiceDefinition
//    with IRTWrappedUnsafeServiceDefinition
//    with IRTCirceWrappedServiceDefinition {
//
//  sealed trait GreeterServiceInput extends AnyRef with Product
//
//  final case class GreetInput(name: String, surname: String) extends GreeterServiceInput
//
//  final case class SayHiInput() extends GreeterServiceInput
//
//  sealed trait GreeterServiceOutput extends AnyRef with Product
//
//  final case class GreetOutput(value: String) extends GreeterServiceOutput
//
//  final case class SayHiOutput(value: String) extends GreeterServiceOutput
//
//  final case class BrokenNoServerHandler() extends GreeterServiceInput
//
//  final case class BrokenClient() extends GreeterServiceInput
//
//
//  override type Input = GreeterServiceInput
//  override type Output = GreeterServiceOutput
//
//  override type ServiceServer[C] = GreeterService[C]
//  override type ServiceClient = GreeterServiceClient
//
//
//  override def client[R[_] : IRTResult](dispatcher: IRTDispatcher[GreeterServiceInput, GreeterServiceOutput]): GreeterServiceClient = {
//    new GreeterServiceWrapped.PackingDispatcher.Impl[R](dispatcher)
//  }
//
//
//  override def server[R[_] : IRTResult, C](service: GreeterService[C]): IRTDispatcher[IRTInContext[GreeterServiceInput, C], GreeterServiceOutput] = {
//    new GreeterServiceWrapped.UnpackingDispatcher.Impl[C](service)
//  }
//
//
//  override def serverUnsafe[R[_] : IRTResult, C](service: GreeterService[C]): IRTUnsafeDispatcher[C] = {
//    new GreeterServiceWrapped.UnpackingDispatcher.Impl[C](service)
//  }
//
//  override def clientUnsafe[R[_] : IRTResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product]]): GreeterServiceClient = {
//    client(new SafeToUnsafeBridge[R](dispatcher))
//  }
//
//  object SayHiInput {
//    implicit val encode: Encoder[SayHiInput] = deriveEncoder
//    implicit val decode: Decoder[SayHiInput] = deriveDecoder
//  }
//
//  object BrokenNoServerHandler {
//    implicit val encode: Encoder[BrokenNoServerHandler] = deriveEncoder
//    implicit val decode: Decoder[BrokenNoServerHandler] = deriveDecoder
//  }
//
//
//  object SayHiOutput {
//    implicit val encode: Encoder[SayHiOutput] = deriveEncoder
//    implicit val decode: Decoder[SayHiOutput] = deriveDecoder
//  }
//
//  object GreetInput {
//    implicit val encode: Encoder[GreetInput] = deriveEncoder
//    implicit val decode: Decoder[GreetInput] = deriveDecoder
//  }
//
//  object GreetOutput {
//    implicit val encode: Encoder[GreetOutput] = deriveEncoder
//    implicit val decode: Decoder[GreetOutput] = deriveDecoder
//  }
//
//  val serviceId = IRTServiceId("GreeterService")
//
//  trait PackingDispatcher[R[_]]
//    extends GreeterServiceClient
//      with ZioResult {
//    def dispatcher: IRTDispatcher[GreeterServiceInput, GreeterServiceOutput]
//
//
//    override def sayhi(): Just[String] = {
//      val packed = SayHiInput()
//      val dispatched = dispatcher.dispatch(packed)
//      _ServiceResult.map(dispatched) {
//        case o: SayHiOutput =>
//          o.value
//        case o =>
//          throw new IRTTypeMismatchException(s"Unexpected input in GreeterServiceDispatcherPacking.sayhi: $o", o, None)
//      }
//
//    }
//
//    def greet(name: String, surname: String): Just[String] = {
//      val packed = GreetInput(name, surname)
//      val dispatched = dispatcher.dispatch(packed)
//      _ServiceResult.map(dispatched) {
//        case o: GreetOutput =>
//          o.value
//        case o =>
//          throw new IRTTypeMismatchException(s"Unexpected input in GreeterServiceDispatcherPacking.greet: $o", o, None)
//      }
//    }
//
//    def broken(howBroken: HowBroken): Just[String] = {
//      val packed = howBroken match {
//        case HowBroken.MissingServerHandler => BrokenNoServerHandler()
//        case HowBroken.BrokenClientCode => BrokenClient()
//      }
//
//      val dispatched = dispatcher.dispatch(packed)
//      _ServiceResult.map(dispatched) {
//        case o: GreetOutput =>
//          o.value
//        case o =>
//          throw new IRTTypeMismatchException(s"Unexpected input in GreeterServiceDispatcherPacking.greet: $o", o, None)
//      }
//    }
//  }
//
//  class SafeToUnsafeBridge[R[_] : IRTResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]) extends IRTDispatcher[GreeterServiceInput, GreeterServiceOutput, R] with IRTWithResult[R] {
//    override protected def _ServiceResult: IRTResult[R] = implicitly
//
//    import IRTResult._
//
//    override def dispatch(input: GreeterServiceInput): Just[GreeterServiceOutput] = {
//      dispatcher.dispatch(IRTMuxRequest(input: Product, toMethodId(input)))
//        .map {
//          case IRTMuxResponse(t: GreeterServiceOutput, _) =>
//            t
//          case o =>
//            throw new IRTTypeMismatchException(s"Unexpected output in GreeterServiceSafeToUnsafeBridge.dispatch: $o", o, None)
//        }
//    }
//  }
//
//  object PackingDispatcher {
//
//    class Impl[R[_] : IRTResult](val dispatcher: IRTDispatcher[GreeterServiceInput, GreeterServiceOutput, R]) extends PackingDispatcher[R] {
//      override protected def _ServiceResult: IRTResult[R] = implicitly
//    }
//
//  }
//
//  trait UnpackingDispatcher[C]
//    extends GreeterServiceWrapped[C]
//      with IRTGeneratedUnpackingDispatcher[GreeterServiceInput, GreeterServiceOutput] {
//    def service: GreeterService[C]
//
//    def greet(context: Context, input: GreetInput): Just[GreetOutput] = {
//      assert(input != null)
//      val result = service.greet(context, input.name, input.surname)
//      _ServiceResult.map(result)(GreetOutput.apply)
//    }
//
//    def sayhi(context: Context, input: SayHiInput): Just[GreetOutput] = {
//      assert(input != null)
//      val result = service.sayhi(context)
//      _ServiceResult.map(result)(GreetOutput.apply)
//    }
//
//    def dispatch(input: IRTInContext[GreeterServiceInput, Context]): Just[GreeterServiceOutput] = {
//      input match {
//        case IRTInContext(v: GreetInput, c) =>
//          _ServiceResult.map(greet(c, v))(v => v) // upcast
//        case IRTInContext(v: SayHiInput, c) =>
//          _ServiceResult.map(sayhi(c, v))(v => v) // upcast
//        case IRTInContext(v: BrokenClient, c) =>
//          throw new RuntimeException()
//      }
//    }
//
//    def identifier: IRTServiceId = serviceId
//
//    protected def toMethodId(v: GreeterServiceInput): IRTMethod = GreeterServiceWrapped.toMethodId(v)
//
//    protected def toMethodId(v: GreeterServiceOutput): IRTMethod = GreeterServiceWrapped.toMethodId(v)
//
//    protected def toZeroargBody(v: IRTMethod): Option[GreeterServiceInput] = {
//      v match {
//        case IRTMethod(`serviceId`, IRTMethodId("sayhi")) =>
//          Some(SayHiInput())
//        case _ =>
//          None
//      }
//    }
//
//    import scala.reflect._
//
//    override protected def inputTag: ClassTag[GreeterServiceInput] = classTag
//
//    override protected def outputTag: ClassTag[GreeterServiceOutput] = classTag
//  }
//
//  def toMethodId(v: GreeterServiceInput): IRTMethod = {
//    v match {
//      case _: GreetInput => IRTMethod(serviceId, IRTMethodId("greet"))
//      case _: SayHiInput => IRTMethod(serviceId, IRTMethodId("sayhi"))
//      case _: BrokenNoServerHandler => IRTMethod(serviceId, IRTMethodId("broken"))
//    }
//  }
//
//  def toMethodId(v: GreeterServiceOutput): IRTMethod = {
//    v match {
//      case _: GreetOutput => IRTMethod(serviceId, IRTMethodId("greet"))
//      case _: SayHiOutput => IRTMethod(serviceId, IRTMethodId("sayhi"))
//    }
//  }
//
//
//  object UnpackingDispatcher {
//
//    class Impl[C](val service: GreeterService[C]) extends UnpackingDispatcher[C] {
//      override protected def _ServiceResult: IRTResult[R] = implicitly
//    }
//
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
//        case IRTReqBody(v: GreetInput) =>
//          v.asJson
//        case IRTReqBody(v: SayHiInput) =>
//          v.asJson
//        case IRTReqBody(v: BrokenNoServerHandler) =>
//          v.asJson
//      }
//    )
//
//    override def responseEncoders: List[PartialFunction[IRTResBody, Json]] = List(
//      {
//        case IRTResBody(v: GreetOutput) =>
//          v.asJson
//        case IRTResBody(v: SayHiOutput) =>
//          v.asJson
//      }
//    )
//
//
//    override def requestDecoders: List[PartialFunction[IRTCursorForMethod, Just[IRTReqBody]]] = List(
//      {
//        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("sayhi") =>
//          packet.as[SayHiInput].map(v => IRTReqBody(v))
//        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("greet") =>
//          packet.as[GreetInput].map(v => IRTReqBody(v))
//      }
//    )
//
//    override def responseDecoders: List[PartialFunction[IRTCursorForMethod, Just[IRTResBody]]] = List(
//      {
//        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("sayhi") =>
//          packet.as[SayHiOutput].map(v => IRTResBody(v))
//        case IRTCursorForMethod(m, packet) if m.service == serviceId && m.methodId == IRTMethodId("greet") =>
//          packet.as[GreetOutput].map(v => IRTResBody(v))
//      }
//    )
//  }
//
//}
