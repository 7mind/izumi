package izumi.thirdparty.internal.boopickle

private[boopickle] object Constants {
  final val NullRef    = -1
  final val NullObject = 0

  // codes for special strings
  final val StringInt: Byte       = 1
  final val StringLong: Byte      = 2
  final val StringUUID: Byte      = 3
  final val StringUUIDUpper: Byte = 4

  // codes for special Durations
  final val DurationInf: Byte       = 1
  final val DurationMinusInf: Byte  = 2
  final val DurationUndefined: Byte = 3

  // codes for Either
  final val EitherLeft: Byte  = 1
  final val EitherRight: Byte = 2

  // codes for Option
  final val OptionNone: Byte = 1
  final val OptionSome: Byte = 2

  // common strings that can be used as references
  val immutableInitData = Seq("null", "true", "false", "0", "1", "-1", "2", "3", "4", "5", "6", "7", "8", "9")

  val identityInitData = Seq(None)
}
