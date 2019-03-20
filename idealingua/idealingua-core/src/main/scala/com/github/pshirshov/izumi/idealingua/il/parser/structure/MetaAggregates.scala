package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.il.parser.IDLParserContext
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawNodeMeta
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import fastparse.NoWhitespace._
import fastparse._

class MetaAggregates(context: IDLParserContext) {
  import aggregates._

  def withMeta[T](defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, T)] = {
    P(MaybeDoc ~ context.defConst.defAnnos ~ context.defPositions.positioned(defparser)).map {
      case (doc, annos, (pos, r)) =>
        (RawNodeMeta(doc.toSeq, annos, pos), r)
    }
  }

  def kwWithMeta[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, T)] = {
    withMeta(kw(keyword, defparser)).map {
      case (m, t) => (m, t)
    }
  }


  def cstarting[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, RawDeclaredTypeName, T)] = {
    withMeta(typeDecl(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

  def cblock[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, RawDeclaredTypeName, T)] = {
    withMeta(block(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

}
