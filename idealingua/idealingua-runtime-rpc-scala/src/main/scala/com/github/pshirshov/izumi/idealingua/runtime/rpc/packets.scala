package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{Decoder, Encoder, Json}
import io.circe._
import io.circe.generic.semiauto._
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

sealed trait RpcPacket {
  type PacketId = String
  def kind: RPCPacketKind
}

case class RpcPacketId(v: String) extends AnyVal

object RpcPacketId {
  implicit def dec0: Decoder[RpcPacketId] = Decoder.decodeString.map(RpcPacketId.apply)

  implicit def enc0: Encoder[RpcPacketId] = Encoder.encodeString.contramap(_.v)
}

case class RpcRequest
(
  kind: RPCPacketKind
  , service: String
  , method: String
  , id: RpcPacketId
  , data: Json
  , headers: Map[String, String]
) extends RpcPacket

object RpcRequest {
  implicit def dec0: Decoder[RpcRequest] = deriveDecoder

  implicit def enc0: Encoder[RpcRequest] = deriveEncoder
}

sealed trait AnyRpcResponse extends RpcPacket

case class RpcResponse
(
  kind: RPCPacketKind
  , ref: RpcPacketId
  , data: Json
) extends AnyRpcResponse

object RpcResponse {
  implicit def dec0: Decoder[RpcResponse] = deriveDecoder

  implicit def enc0: Encoder[RpcResponse] = deriveEncoder
}


case class RpcStringResponse
(
  kind: RPCPacketKind
  , data: String
  , cause: String
) extends AnyRpcResponse

object RpcStringResponse {
  implicit def dec0: Decoder[RpcStringResponse] = deriveDecoder

  implicit def enc0: Encoder[RpcStringResponse] = deriveEncoder
}
