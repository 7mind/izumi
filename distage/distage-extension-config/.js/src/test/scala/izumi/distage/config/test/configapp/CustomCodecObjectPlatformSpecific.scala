package izumi.distage.config.test.configapp

import io.circe.Decoder

trait CustomCodecObjectPlatformSpecific {
  implicit val circeDecoder: Decoder[CustomCodecObject] = Decoder.decodeString.map {
    case "eaaxacaca" => new CustomCodecObject(453)
    case "a" => new CustomCodecObject(45)
    case _ => new CustomCodecObject(1)
  }
}
