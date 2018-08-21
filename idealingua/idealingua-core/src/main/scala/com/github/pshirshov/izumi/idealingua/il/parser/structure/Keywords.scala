package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse.all._

trait Keywords extends Separators {
  def kw(s: String): Parser[Unit] = P(s ~ inline)(sourcecode.Name(s"`$s`"))

  def kw(s: String, alt: String*): Parser[Unit] = {
    val alts = alt.foldLeft(P(s)) { case (acc, v) => acc | v }
    P(alts ~ inline)(sourcecode.Name(s"`$s | $alt`"))
  }

  final val domain = kw("domain", "package", "namespace")
  final val include = kw("include")
  final val `import` = kw("import")

  final val enum = kw("enum")
  final val adt = kw("adt", "choice")
  final val alias = kw("alias", "type", "using")
  final val newtype = kw("clone", "newtype", "copy")
  final val id = kw("id")
  final val mixin = kw("mixin", "interface")
  final val data = kw("data", "dto", "struct")
  final val service = kw("service", "server")
  final val buzzer = kw("buzzer", "sender")
  final val streams = kw("streams", "tunnel", "pump")
  final val consts = kw("const", "values")

  final val defm = kw("def", "fn", "fun", "func")
  final val defe = kw("line", "event")
  final val upstream = kw("toserver", "up", "upstream")
  final val downstream = kw("toclient", "down", "downstream")

  def apply[T](kw: Parser[Unit], defparser: Parser[T]): Parser[T] = {
    P(kw ~/ defparser)
  }

}

