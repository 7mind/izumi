package izumi.idealingua.il.parser.structure

import fastparse._
import fastparse.NoWhitespace._

trait Comments
  extends Symbols {

  def MaybeDoc[_: P]: P[Option[String]] = P(DocComment ~ NLC ~ sep.inline).?

  def MultilineComment[_: P]: P0 = P((!"/**" ~ "/*" ~ CommentChunk.rep ~ "*/") | "/**/").rep(1)

  def ShortComment[_: P]: P[Unit] = P("//" ~ (CharsWhile(c => c != '\n' && c != '\r', 0) ~ NLC))

  protected[structure] def DocComment[_: P]: P[String] = {
    P(!"/**/" ~ "/*" ~ (!"*/" ~ "*" ~ DocChunk).rep(1, sep = NLC ~ sep.wss) ~ NLC ~ sep.wss ~ "*/").map {
      s => s.mkString("\n")
    }
  }

  private def DocChunk[_: P]: P[String] = P(CharsWhile(c => c != '\n' && c != '\r').rep.!)

  private def CommentChunk[_: P]: P[Unit] = P(CharsWhile(c => c != '/' && c != '*') | MultilineComment | !"*/" ~ AnyChar)

}

