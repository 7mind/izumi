package izumi.idealingua.il.parser.structure

import izumi.idealingua.il.parser.IDLParserContext
import izumi.idealingua.model.il.ast.raw.defns.RawNodeMeta
import izumi.idealingua.model.il.ast.raw.typeid.ParsedId
import fastparse.NoWhitespace._
import fastparse._

class MetaAggregates(context: IDLParserContext) {
  import aggregates._

  def withMeta[T](defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, T)] = {
    P(MaybeDoc ~ context.defConst.defAnnos ~ context.defPositions.positioned(defparser)).map {
      case (doc, annos, (pos, r)) =>
        (RawNodeMeta(doc, annos, pos), r)
    }
  }

  def cstarting[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, ParsedId, T)] = {
    withMeta(starting(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

  def cblock[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, ParsedId, T)] = {
    withMeta(block(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

}
