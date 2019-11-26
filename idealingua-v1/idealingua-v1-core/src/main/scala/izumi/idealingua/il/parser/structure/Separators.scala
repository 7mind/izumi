package izumi.idealingua.il.parser.structure

import fastparse.NoWhitespace._
import fastparse._

trait Separators extends Comments {
  private def ws[_: P]: P[Unit] = P(" " | "\t")

  def wss[_: P]: P[Unit] = P(ws.rep)

  private def WsComment[_: P]: P[Unit] = P(wss ~ MultilineComment ~ wss)

  private def SepLineBase[_: P]: P[Unit] = P(NLC | (WsComment ~ NLC | (wss ~ ShortComment)))

  def inline[_: P]: P[Unit] = P(WsComment | wss)

  def any[_: P]: P[Unit] = P(wss ~ (WsComment | SepLineBase).rep ~ wss)

  def sepStruct[_: P]: P[Unit] = P(any ~ (";" | "," | SepLineBase | any) ~ any)

  def sepEnum[_: P]: P[Unit] = P((ws.rep(1) | NLC | WsComment | (wss ~ ShortComment)).rep(1))

  def sepAdt[_: P]: P[Unit] = P(sepEnum)

  def sepEnumFreeForm[_: P]: P[Unit] = P(any ~ ("|" | ";" | ",") ~ any)

  def sepAdtFreeForm[_: P]: P[Unit] = P(sepEnumFreeForm)

}


