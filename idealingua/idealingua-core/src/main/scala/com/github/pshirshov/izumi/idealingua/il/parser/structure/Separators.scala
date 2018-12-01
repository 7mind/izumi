package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse.NoWhitespace._
import fastparse._

trait Separators extends Comments {
  private def ws[_:P] = P(" " | "\t")(sourcecode.Name("WS"), implicitly)
  final def wss[_:P]: P[Unit] = P(ws.rep)

  private def WsComment[_:P] = wss ~ MultilineComment ~ wss
  private def SepLineBase[_:P] = P(NLC | (WsComment ~ NLC | (wss ~ ShortComment)))

  final def inline[_:P]: P[Unit] = P(WsComment | wss)
  final def any[_:P]: P[Unit] = P(wss ~ (WsComment | SepLineBase).rep ~ wss)

  final def sepStruct[_:P]: P[Unit] = P((";" | "," | SepLineBase | ws)).rep(1)

  final def sepAdt[_:P]: P[Unit] = P(
    (wss ~ (WsComment | SepLineBase).rep(min = 0, max = 1) ~ wss ~ ("|" | ";" | ",") ~ wss ~ (WsComment | SepLineBase).rep(min = 0, max = 1) ~ wss) |
      ((wss | WsComment) ~ SepLineBase ~ (wss | WsComment)) |
      ws.rep(1)
  )

  final def sepEnum[_:P]: P[Unit] = sepAdt

}


