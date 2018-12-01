package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse._, NoWhitespace._


trait Keywords extends Separators {
  def kw[_:P](s: String): P[Unit] = P(s ~ inline) //(sourcecode.Name(s"`$s`"))

  def kw[_:P](s: String, alt: String*): P[Unit] = {
    def alts = alt.foldLeft(P(s)) { case (acc, v) => acc | v }
    P(alts ~ inline) //(sourcecode.Name(s"`$s | $alt`"))
  }

  final def domain[_:P]: P[Unit] = kw("domain", "package", "namespace")
  final def include[_:P]: P[Unit] = kw("include")
  final def `import`[_:P]: P[Unit] = kw("import")

  final def enum[_:P]: P[Unit] = kw("enum")
  final def adt[_:P]: P[Unit] = kw("adt", "choice")
  final def alias[_:P]: P[Unit] = kw("alias", "type", "using")
  final def newtype[_:P]: P[Unit] = kw("clone", "newtype", "copy")
  final def id[_:P]: P[Unit] = kw("id")
  final def mixin[_:P]: P[Unit] = kw("mixin", "interface")
  final def data[_:P]: P[Unit] = kw("data", "dto", "struct")
  final def service[_:P]: P[Unit] = kw("service", "server")
  final def buzzer[_:P]: P[Unit] = kw("buzzer", "sender")
  final def streams[_:P]: P[Unit] = kw("streams", "tunnel", "pump")
  final def consts[_:P]: P[Unit] = kw("const", "defues")

  final def defm[_:P]: P[Unit] = kw("def", "fn", "fun", "func")
  final def defe[_:P]: P[Unit] = kw("line", "event")
  final def upstream[_:P]: P[Unit] = kw("toserver", "up", "upstream")
  final def downstream[_:P]: P[Unit] = kw("toclient", "down", "downstream")

  def apply[T](kw: P[Unit], defparser: P[T])(implicit v: P[_]): P[T] = {
    P(kw ~/ defparser)
  }

}

