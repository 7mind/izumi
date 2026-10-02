package izumi.fundamentals.assertions

import scala.collection.mutable.ArrayBuffer
import scala.reflect.macros.blackbox

object AssertionMacro {
  def standard(c: blackbox.Context)(condition: c.Expr[Boolean]): c.Expr[Unit] = {
    expand(c)(condition, standardContext(c), c.prefix.tree)
  }

  def configured(c: blackbox.Context)(condition: c.Expr[Boolean], context: c.Expr[AssertionContext]): c.Expr[Unit] = expand(c)(condition, context, c.prefix.tree)

  def unary[F[_]](c: blackbox.Context)(condition: c.Expr[Boolean])(suspension: c.Expr[AssertionSuspension1[F]]): c.Expr[F[Unit]] =
    c.Expr[F[Unit]](suspended(c)(condition, standardContext(c), suspension.tree))

  def unaryConfigured[F[_]](c: blackbox.Context)(condition: c.Expr[Boolean], context: c.Expr[AssertionContext])(suspension: c.Expr[AssertionSuspension1[F]]): c.Expr[F[Unit]] =
    c.Expr[F[Unit]](suspended(c)(condition, context, suspension.tree))

  def binary[F[_, _]](c: blackbox.Context)(condition: c.Expr[Boolean])(suspension: c.Expr[AssertionSuspension2[F]]): c.Expr[F[Nothing, Unit]] =
    c.Expr[F[Nothing, Unit]](suspended(c)(condition, standardContext(c), suspension.tree))

  def binaryConfigured[F[_, _]](c: blackbox.Context)(condition: c.Expr[Boolean], context: c.Expr[AssertionContext])(suspension: c.Expr[AssertionSuspension2[F]]): c.Expr[F[Nothing, Unit]] =
    c.Expr[F[Nothing, Unit]](suspended(c)(condition, context, suspension.tree))

  private def standardContext(c: blackbox.Context): c.Expr[AssertionContext] = {
    import c.universe._
    c.Expr[AssertionContext](q"_root_.izumi.fundamentals.assertions.AssertionContext.standard")
  }

  private def suspended(c: blackbox.Context)(condition: c.Expr[Boolean], context: c.Expr[AssertionContext], suspension: c.Tree): c.Tree = {
    import c.universe._
    val receiver = TermName(c.freshName("assertionReceiver"))
    val assertion = expand(c)(condition, context, q"$receiver")
    q"{ val $receiver = ${ c.prefix.tree }; $suspension.suspend($assertion) }"
  }

  private def expand(c: blackbox.Context)(condition: c.Expr[Boolean], context: c.Expr[AssertionContext], receiver: c.Tree): c.Expr[Unit] = {
    import c.universe._

    val sites = ArrayBuffer.empty[Tree]
    val recorder = TermName(c.freshName("assertionRecorder"))
    val comparisonOwners = Set("scala.Any", "scala.AnyRef", "java.lang.Object", "scala.Boolean", "scala.Byte", "scala.Short", "scala.Char", "scala.Int", "scala.Long", "scala.Float", "scala.Double")
    val comparisonNames = Set("==", "!=", "<", "<=", ">", ">=", "eq", "ne")
    // Without range positions, synthesized ranges can omit the comparison's left operand.
    val rangePositions = c.enclosingPosition.isRange

    def point(position: Position, offset: Int): Tree = {
      val source = position.source
      val line = source.offsetToLine(offset)
      val column = offset - source.lineToOffset(line)
      q"_root_.izumi.fundamentals.assertions.SourcePoint($offset, $line, $column)"
    }

    def span(position: Position): Tree = {
      if (rangePositions && position.isRange) q"_root_.izumi.fundamentals.assertions.SourceSpan.Range(${ point(position, position.start) }, ${ point(position, position.end) })"
      else if (position != NoPosition) q"_root_.izumi.fundamentals.assertions.SourceSpan.Point(${ point(position, position.point) })"
      else q"_root_.izumi.fundamentals.assertions.SourceSpan.Unavailable"
    }

    def text(position: Position): Tree = {
      if (rangePositions && position.isRange) {
        val content = new String(position.source.content.slice(position.start, position.end))
        q"_root_.izumi.fundamentals.assertions.CompiledText.Available($content)"
      } else q"_root_.izumi.fundamentals.assertions.CompiledText.Unavailable"
    }

    def site(tree: Tree, kind: String): Int = {
      val index = sites.size
      val kindName = TermName(kind)
      sites += q"_root_.izumi.fundamentals.assertions.ObservationSite(${ span(tree.pos) }, ${ text(tree.pos) }, _root_.izumi.fundamentals.assertions.ObservationKind.$kindName)"
      index
    }

    def observe(tree: Tree, index: Int): Tree = q"$recorder.observe[${ tree.tpe.widen }]($index, $tree)"

    def instrument(tree: Tree): Tree = tree match {
      case Typed(body, _) => instrument(body)
      case Apply(selection @ Select(left, name), List(right)) if selection.symbol.owner == definitions.BooleanClass && (name.decodedName.toString == "&&" || name.decodedName.toString == "||") =>
        val index = site(tree, "BooleanOperator")
        val result = if (name.decodedName.toString == "&&") q"${ instrument(left) } && ${ instrument(right) }" else q"${ instrument(left) } || ${ instrument(right) }"
        q"$recorder.observe[Boolean]($index, $result)"
      case Apply(selection @ Select(child, name), Nil) if selection.symbol.owner == definitions.BooleanClass && name.decodedName.toString == "unary_!" =>
        val index = site(tree, "BooleanOperator")
        q"$recorder.observe[Boolean]($index, !${ instrument(child) })"
      case selection @ Select(child, name) if selection.symbol.owner == definitions.BooleanClass && name.decodedName.toString == "unary_!" =>
        val index = site(tree, "BooleanOperator")
        q"$recorder.observe[Boolean]($index, !${ instrument(child) })"
      case Apply(selection @ Select(left, name), List(right)) if comparisonNames.contains(name.decodedName.toString) && comparisonOwners.contains(selection.symbol.owner.fullName) =>
        val index = site(tree, "Comparison")
        val leftObserved = observe(left, site(left, "Operand"))
        val rightObserved = observe(right, site(right, "Operand"))
        val method = c.internal.setSymbol(Select(leftObserved, name), selection.symbol)
        val result = Apply(method, List(rightObserved))
        q"$recorder.observe[Boolean]($index, $result)"
      case _: Ident | _: Literal => observe(tree, site(tree, "BooleanLeaf"))
      case _ => observe(tree, site(tree, "Opaque"))
    }

    val instrumented = instrument(condition.tree)
    val position = condition.tree.pos
    val path = if (position != NoPosition) position.source.path else "<unknown>"
    val virtual = position == NoPosition || position.source.file.isVirtual
    c.Expr[Unit](q"""{
      val _ = $receiver
      val $recorder = new _root_.izumi.fundamentals.assertions.AssertionRecorder(_root_.scala.Vector(..$sites))
      val result = $instrumented
      $recorder.check(result, $path, $virtual, ${ span(position) }, ${ text(position) }, $context)
    }""")
  }
}
