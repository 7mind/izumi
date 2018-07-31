package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse.all._

trait Comments
  extends Symbols {

  final lazy val DocComment = {
    val Chunk = P(CharsWhile(c => c != '\n' && c != '\r').rep.!)
    P(!"/**/" ~ "/*" ~ (!"*/" ~ "*" ~ Chunk).rep(1, sep = NLC ~ sep.wss) ~ NLC ~ sep.wss ~ "*/").map {
      s => s.mkString("\n")
    }
  }

  final lazy val MultilineComment: P0 = {
    val CommentChunk = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
    P((!"/**" ~ "/*" ~ CommentChunk.rep ~ "*/") | "/**/").rep(1)
  }

  final lazy val ShortComment = P("//" ~ (CharsWhile(c => c != '\n' && c != '\r', min = 0) ~ NLC))

  final lazy val MaybeDoc = (DocComment ~ NLC ~ sep.inline).?
}

