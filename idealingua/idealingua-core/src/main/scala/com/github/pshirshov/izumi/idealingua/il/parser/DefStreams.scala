package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.{aggregates, kw, sep}
import com.github.pshirshov.izumi.idealingua.model.common.StreamDirection
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILStreams
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawStream, Streams}
import fastparse._
import fastparse.NoWhitespace._

trait DefStreams {

  import sep._

  final def downstream[_:P]: P[RawStream.Directed] = DefSignature.baseSignature(kw.downstream).map {
    case (c, id, in) =>
      RawStream.Directed(id, StreamDirection.ToClient, in, c)

    case f =>
      throw new IllegalStateException(s"Impossible case: $f")
  }

  final def upstream[_:P]: P[RawStream.Directed] = DefSignature.baseSignature(kw.upstream).map {
    case (c, id, in) =>
      RawStream.Directed(id, StreamDirection.ToServer, in, c)

    case f =>
      throw new IllegalStateException(s"Impossible case: $f")
  }

  final def stream[_:P]: P[RawStream.Directed] = downstream | upstream

  // other method kinds should be added here
  final def streams[_:P]: P[Seq[RawStream]] = P(stream.rep(sep = any))

  final def streamsBlock[_:P]: P[ILStreams] = aggregates.cblock(kw.streams, streams)
    .map {
      case (c, i, v) => ILStreams(Streams(i.toStreamsId, v.toList, c))
    }
}

object DefStreams extends DefStreams {
}





