package izumi.idealingua.il.parser

import izumi.idealingua.il.parser.structure.{kw, sep}
import izumi.idealingua.model.common.StreamDirection
import izumi.idealingua.model.il.ast.raw.defns.{RawStream, RawStreams, RawTopLevelDefn}
import fastparse.NoWhitespace._
import fastparse._

class DefStreams(context: IDLParserContext) {

  import context._
  import sep._

  def downstream[_: P]: P[RawStream.Directed] = metaAgg.withMeta(defSignature.baseSignature(kw.downstream)).map {
    case (meta, (id, in)) =>
      RawStream.Directed(id, StreamDirection.ToClient, in, meta)
  }

  def upstream[_: P]: P[RawStream.Directed] = metaAgg.withMeta(defSignature.baseSignature(kw.upstream)).map {
    case (meta, (id, in)) =>
      RawStream.Directed(id, StreamDirection.ToServer, in, meta)
  }

  def stream[_: P]: P[RawStream.Directed] = downstream | upstream

  // other method kinds should be added here
  def streams[_: P]: P[Seq[RawStream]] = P(stream.rep(sep = any))

  def streamsBlock[_: P]: P[RawTopLevelDefn.TLDStreams] = P(metaAgg.cblock(kw.streams, streams))
    .map {
      case (c, i, v) => RawStreams(i.toStreamsId, v.toList, c)
    }
    .map(RawTopLevelDefn.TLDStreams)
}
