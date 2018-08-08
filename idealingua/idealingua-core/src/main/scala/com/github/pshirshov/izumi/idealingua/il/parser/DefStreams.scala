package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.{aggregates, kw, sep}
import com.github.pshirshov.izumi.idealingua.model.common.StreamDirection
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILStreams
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawStream, Streams}
import fastparse.all._

trait DefStreams {

  import sep._

  final val downstream = DefSignature.baseSignature(kw.downstream).map {
    case (c, id, in) =>
      RawStream.Directed(id, StreamDirection.ToClient, in, c)

    case f =>
      throw new IllegalStateException(s"Impossible case: $f")
  }

  final val upstream = DefSignature.baseSignature(kw.upstream).map {
    case (c, id, in) =>
      RawStream.Directed(id, StreamDirection.ToServer, in, c)

    case f =>
      throw new IllegalStateException(s"Impossible case: $f")
  }

  final val stream = downstream | upstream

  // other method kinds should be added here
  final val streams: Parser[Seq[RawStream]] = P(stream.rep(sep = any))

  final val streamsBlock = aggregates.cblock(kw.streams, streams)
    .map {
      case (c, i, v) => ILStreams(Streams(i.toStreamsId, v.toList, c))
    }
}

object DefStreams extends DefStreams {
}





