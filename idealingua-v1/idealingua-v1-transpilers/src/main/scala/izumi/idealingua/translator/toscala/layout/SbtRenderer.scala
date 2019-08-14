package izumi.idealingua.translator.toscala.layout

import izumi.idealingua.model.publishing.BuildManifest

class SbtRenderer {
  def renderOp(pair: Tuple2[String, SbtDslOp]): String = {
    val key = pair._1
    val parts = pair._2 match {
      case SbtDslOp.Append(v, scope) =>
        v match {
          case s: Seq[_] =>
            Seq(key, renderScope(scope), "++=", renderValue(s))
          case o =>
            Seq(key, renderScope(scope), "+=", renderValue(o))
        }
      case SbtDslOp.Assign(v, scope) =>
        Seq(key, renderScope(scope), ":=", renderValue(v))
    }
    parts.mkString(" ")
  }

  def renderScope(scope: Scope): String = {
    scope match {
      case Scope.ThisBuild =>
        "in ThisBuild"
      case Scope.Project =>
        ""
    }
  }

  def renderValue(v: Any): String = {
    v match {
      case s: String =>
        s""""$s""""
      case b: Boolean =>
        b.toString
      case v: Number =>
        v.toString
      case o: Option[_] =>
        o match {
          case Some(value) =>
            s"Some(${renderValue(value)})"
          case None =>
            "None"
        }
      case u: BuildManifest.MFUrl =>
        s"url(${renderValue(u.url)})"
      case l: BuildManifest.License =>
        s"${renderValue(l.name)} -> ${renderValue(l.url)}"
      case s: Seq[_] =>
        s.map(renderValue).mkString("Seq(\n  ", ",\n  ", ")")
      case r: RawExpr =>
        r.e.trim
    }
  }

}
