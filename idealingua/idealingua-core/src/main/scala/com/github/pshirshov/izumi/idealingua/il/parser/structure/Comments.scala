package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse._
import fastparse.NoWhitespace._

trait Comments
  extends Symbols {

  final def DocComment[_:P]: P[String] = {
    def Chunk = P(CharsWhile(c => c != '\n' && c != '\r').rep.!)
    P(!"/**/" ~ "/*" ~ (!"*/" ~ "*" ~ Chunk).rep(1, sep = NLC ~ sep.wss) ~ NLC ~ sep.wss ~ "*/").map {
      s => s.mkString("\n")
    }
  }

  final def MultilineComment[_:P]: P0 = {
    val CommentChunk = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
    P((!"/**" ~ "/*" ~ CommentChunk.rep ~ "*/") | "/**/").rep(1)
  }

  final def ShortComment[_:P]: P[Unit] = P("//" ~ (CharsWhile(c => c != '\n' && c != '\r', min = 0) ~ NLC))

  final def MaybeDoc[_:P]: P[Option[String]] = (DocComment ~ NLC ~ sep.inline).?
}

