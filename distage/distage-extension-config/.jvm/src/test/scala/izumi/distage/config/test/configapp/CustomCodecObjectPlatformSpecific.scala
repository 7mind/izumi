package izumi.distage.config.test.configapp

import pureconfig.ConfigReader

trait CustomCodecObjectPlatformSpecific {
  implicit val pureconfigReader: ConfigReader[CustomCodecObject] = ConfigReader.fromStringOpt {
    case "eaaxacaca" => Some(new CustomCodecObject(453))
    case "a" => Some(new CustomCodecObject(45))
    case _ => Some(new CustomCodecObject(1))
  }
}
