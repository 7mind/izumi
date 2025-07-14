package izumi.logstage.api

import izumi.logstage.api.rendering.LogstageCodec

object Fixture {
  final case class NoInstance(x: Int)
  final case class YesInstance(x: Int)
  object YesInstance {
    implicit val codec: LogstageCodec[YesInstance] = _ `write` _.x
  }

  sealed trait Sealed
  object Sealed {
    implicit val codec: LogstageCodec[Sealed] = (writer, s) =>
      s match {
        case Branch(x) => writer.write(s"""CustomBranchCodec("$x")""")
      }

    final case class Branch(x: String) extends Sealed
  }

}
