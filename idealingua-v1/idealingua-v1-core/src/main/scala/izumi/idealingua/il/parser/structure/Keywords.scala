package izumi.idealingua.il.parser.structure

import fastparse._, NoWhitespace._


trait Keywords extends Separators {
  def kw[_:P](s: String): P[Unit] = P(s ~ inline) //(sourcecode.Name(s"`$s`"))

  def kw[_:P](s: String, alt: String*): P[Unit] = {
    def alts = alt.foldLeft(P(s)) { case (acc, v) => acc | v }
    P(alts ~ inline) //(sourcecode.Name(s"`$s | $alt`"))
  }

  def domain[_:P]: P[Unit] = kw("domain", "package", "namespace")
  def include[_:P]: P[Unit] = kw("include")
  def `import`[_:P]: P[Unit] = kw("import")

  def enum[_:P]: P[Unit] = kw("enum")
  def adt[_:P]: P[Unit] = kw("adt", "choice")
  def alias[_:P]: P[Unit] = kw("alias", "type", "using")
  def newtype[_:P]: P[Unit] = kw("clone", "newtype", "copy")
  def id[_:P]: P[Unit] = kw("id")
  def mixin[_:P]: P[Unit] = kw("mixin", "interface")
  def data[_:P]: P[Unit] = kw("data", "dto", "struct")
  def foreign[_:P]: P[Unit] = kw("foreign")
  def service[_:P]: P[Unit] = kw("service", "server")
  def buzzer[_:P]: P[Unit] = kw("buzzer", "sender")
  def streams[_:P]: P[Unit] = kw("streams", "tunnel", "pump")
  def consts[_:P]: P[Unit] = kw("const", "values")

  def declared[_:P]: P[Unit] = kw("declared")

  def defm[_:P]: P[Unit] = kw("def", "fn", "fun", "func")
  def defe[_:P]: P[Unit] = kw("line", "event")
  def upstream[_:P]: P[Unit] = kw("toserver", "up", "upstream")
  def downstream[_:P]: P[Unit] = kw("toclient", "down", "downstream")

  def apply[T](kw: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[T] = {
    P(kw ~/ defparser)
  }

}

