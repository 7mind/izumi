package izumi.idealingua.runtime.rpc

import izumi.fundamentals.platform.uuid.UUIDGen
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

sealed trait RPCPacketKind

trait RPCPacketKindCirce {

  import _root_.io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

  implicit val encodeTestEnum: Encoder[RPCPacketKind] = Encoder.encodeString.contramap(_.toString)
  implicit val decodeTestEnum: Decoder[RPCPacketKind] = Decoder.decodeString.map(RPCPacketKind.parse)
  implicit val encodeKeyTestEnum: KeyEncoder[RPCPacketKind] = KeyEncoder.encodeKeyString.contramap(_.toString)
  implicit val decodeKeyTestEnum: KeyDecoder[RPCPacketKind] = KeyDecoder.decodeKeyString.map(RPCPacketKind.parse)
}

object RPCPacketKind extends RPCPacketKindCirce {
  type Element = RPCPacketKind

  def all: Map[String, Element] = Seq(
    RpcRequest
    , RpcResponse
    , RpcFail

    , BuzzRequest
    , BuzzResponse
    , BuzzFailure

    , S2CStream
    , C2SStream

    , Fail
  ).map(e => e.toString -> e).toMap

  def parse(value: String): RPCPacketKind = all(value)

  final case object Fail extends RPCPacketKind {
    override def toString: String = "?:failure"
  }

  final case object RpcRequest extends RPCPacketKind {
    override def toString: String = "rpc:request"
  }

  final case object RpcResponse extends RPCPacketKind {
    override def toString: String = "rpc:response"
  }

  final case object RpcFail extends RPCPacketKind {
    override def toString: String = "rpc:failure"
  }

  final case object BuzzRequest extends RPCPacketKind {
    override def toString: String = "buzzer:request"
  }

  final case object BuzzResponse extends RPCPacketKind {
    override def toString: String = "buzzer:response"
  }

  final case object BuzzFailure extends RPCPacketKind {
    override def toString: String = "buzzer:failure"
  }

  final case object S2CStream extends RPCPacketKind {
    override def toString: String = "stream:s2c"
  }

  final case object C2SStream extends RPCPacketKind {
    override def toString: String = "stream:c2s"
  }

}

case class RpcPacketId(v: String)

object RpcPacketId {
  def random(): RpcPacketId = RpcPacketId(UUIDGen.getTimeUUID().toString)

  implicit def dec0: Decoder[RpcPacketId] = Decoder.decodeString.map(RpcPacketId.apply)

  implicit def enc0: Encoder[RpcPacketId] = Encoder.encodeString.contramap(_.v)
}

case class RpcPacket
(
  kind: RPCPacketKind
  , data: Option[Json]
  , id: Option[RpcPacketId]
  , ref: Option[RpcPacketId]
  , service: Option[String]
  , method: Option[String]
  , headers: Option[Map[String, String]]
)

object RpcPacket {
  implicit def dec0: Decoder[RpcPacket] = deriveDecoder

  implicit def enc0: Encoder[RpcPacket] = deriveEncoder

  def rpcCritical(data: String, cause: String): RpcPacket = {
    RpcPacket(RPCPacketKind.Fail, Some(Map("data" -> data, "cause" -> cause).asJson), None, None, None, None, None)
  }

  def rpcRequestRndId(method: IRTMethodId, data: Json): RpcPacket = {
    val rndId = RpcPacketId.random()

    RpcPacket(
      RPCPacketKind.RpcRequest,
      Some(data),
      Some(rndId),
      None,
      Some(method.service.value),
      Some(method.methodId.value),
      None,
    )
  }

  def rpcResponse(ref: RpcPacketId, data: Json): RpcPacket = {
    RpcPacket(RPCPacketKind.RpcResponse, Some(data), None, Some(ref), None, None, None)
  }

  def rpcFail(ref: Option[RpcPacketId], cause: String): RpcPacket = {
    RpcPacket(RPCPacketKind.RpcFail, Some(Map("cause" -> cause).asJson), None, ref, None, None, None)
  }

  def buzzerRequestRndId(method: IRTMethodId, data: Json): RpcPacket = {
    val rndId = RpcPacketId.random()

    RpcPacket(
      RPCPacketKind.BuzzRequest,
      Some(data),
      Some(rndId),
      None,
      Some(method.service.value),
      Some(method.methodId.value),
      None,
    )
  }

  def buzzerResponse(ref: RpcPacketId, data: Json): RpcPacket = {
    RpcPacket(RPCPacketKind.BuzzResponse, Some(data), None, Some(ref), None, None, None)

  }

  def buzzerFail(ref: Option[RpcPacketId], cause: String): RpcPacket = {
    RpcPacket(RPCPacketKind.BuzzFailure, Some(Map("cause" -> cause).asJson), None, ref, None, None, None)
  }
}
