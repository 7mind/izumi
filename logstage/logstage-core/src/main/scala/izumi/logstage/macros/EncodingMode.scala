package izumi.logstage.macros

sealed trait EncodingMode {
  final def fold[R](onRaw: => R)(onStrictness: Boolean => R): R = this match {
    case EncodingMode.NonStrict => onStrictness(false)
    case EncodingMode.Strict => onStrictness(true)
    case EncodingMode.Raw => onRaw
  }
}
object EncodingMode {
  case object NonStrict extends EncodingMode
  case object Strict extends EncodingMode
  case object Raw extends EncodingMode
}
