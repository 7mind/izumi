package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse.all._

trait Separators extends Comments {
  private val ws = P(" " | "\t")(sourcecode.Name("WS"))
  final val wss = P(ws.rep)

  private val WsComment = wss ~ MultilineComment ~ wss
  private val SepLineBase = P(NLC | (WsComment ~ NLC | (wss ~ ShortComment)))

  final val inline = P(WsComment | wss)
  final val any = P(wss ~ (WsComment | SepLineBase).rep ~ wss)

  final val sepStruct = P(";" | "," | SepLineBase | ws).rep(min = 1)

  final val sepAdt = P(
    (wss ~ (WsComment | SepLineBase).rep(min = 0, max = 1) ~ wss ~ ("|" | ";" | ",") ~ wss ~ (WsComment | SepLineBase).rep(min = 0, max = 1) ~ wss) |
      ((wss | WsComment) ~ SepLineBase ~ (wss | WsComment)) |
      ws.rep(min = 1)
  )

  final val sepEnum = sepAdt

}


