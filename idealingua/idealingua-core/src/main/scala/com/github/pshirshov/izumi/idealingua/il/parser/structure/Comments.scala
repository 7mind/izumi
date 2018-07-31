package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse.all._

trait Comments
  extends Symbols {

  final lazy val MultilineComment: P0 = {
    val CommentChunk = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)
    P("/*" ~ CommentChunk.rep ~ "*/").rep(1)
  }

  final lazy val ShortComment = P("//" ~ (CharsWhile(c => c != '\n' && c != '\r', min = 0) ~ NLC))

}

