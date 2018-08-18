package com.github.pshirshov.izumi.idealingua.runtime.rpc

import com.github.pshirshov.izumi.fundamentals.platform.uuid.UUIDGen
import io.circe.{Decoder, Encoder, Json}
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

sealed trait RPCPacketKind

trait RPCPacketKindCirce {

  import _root_.io.circe.{Encoder, Decoder, KeyEncoder, KeyDecoder}

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
  def random(): RpcPacketId = RpcPacketId(UUIDGen.getTimeUUID.toString)

  implicit def dec0: Decoder[RpcPacketId] = Decoder.decodeString.map(RpcPacketId.apply)

  implicit def enc0: Encoder[RpcPacketId] = Encoder.encodeString.contramap(_.v)
}

case class RpcPacket
(
  kind: RPCPacketKind
  , data: Json

  , id: Option[RpcPacketId]
  , ref: Option[RpcPacketId]
  , service: Option[String]
  , method: Option[String]
  , headers: Map[String, String]
)

object RpcPacket {
  implicit def dec0: Decoder[RpcPacket] = deriveDecoder

  implicit def enc0: Encoder[RpcPacket] = deriveEncoder

  def rpcFail(ref: Option[RpcPacketId], cause: String): RpcPacket = {
    RpcPacket(RPCPacketKind.RpcFail, Map("cause" -> cause).asJson, None, ref, None, None, Map.empty)
  }

  def rpcResponse(ref: RpcPacketId, data: Json): RpcPacket = {
    RpcPacket(RPCPacketKind.RpcResponse, data, None, Some(ref), None, None, Map.empty)
  }

  def rpcRequest(method: IRTMethodId, data: Json): RpcPacket = {
    RpcPacket(
      RPCPacketKind.RpcRequest,
      data,
      Some(RpcPacketId.random()),
      None,
      Some(method.service.value),
      Some(method.methodId.value),
      Map.empty,
    )
  }
}


case class RpcFailureStringResponse
(
  kind: RPCPacketKind
  , data: String
  , cause: String
)

object RpcFailureStringResponse {
  implicit def dec0: Decoder[RpcFailureStringResponse] = deriveDecoder

  implicit def enc0: Encoder[RpcFailureStringResponse] = deriveEncoder
}
