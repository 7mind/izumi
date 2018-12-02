package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse._
import fastparse.NoWhitespace._

trait Comments
  extends Symbols {

  def DocChunk[_: P]: P[String] = P(CharsWhile(c => c != '\n' && c != '\r').rep.!)

  def DocComment[_: P]: P[String] = {
    P(!"/**/" ~ "/*" ~ (!"*/" ~ "*" ~ DocChunk).rep(1, sep = NLC ~ sep.wss) ~ NLC ~ sep.wss ~ "*/").map {
      s => s.mkString("\n")
    }
  }

  def CommentChunk[_: P]: P[Unit] = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)

  def MultilineComment[_: P]: P0 = P((!"/**" ~ "/*" ~ CommentChunk.rep ~ "*/") | "/**/").rep(1)

  def ShortComment[_: P]: P[Unit] = P("//" ~ (CharsWhile(c => c != '\n' && c != '\r', min = 0) ~ NLC))

  def MaybeDoc[_: P]: P[Option[String]] = (DocComment ~ NLC ~ sep.inline).?
}

