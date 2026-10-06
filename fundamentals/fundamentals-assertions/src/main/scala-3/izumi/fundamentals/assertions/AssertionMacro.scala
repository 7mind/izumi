package izumi.fundamentals.assertions

import scala.collection.mutable.ArrayBuffer
import scala.quoted.{Expr, Quotes, Type, Varargs}

@scala.annotation.publicInBinary
private[assertions] object AssertionMacro {
  def clued(condition: Expr[Boolean], clue: Expr[Any], receiver: Expr[Assertions])(using Quotes): Expr[Unit] = '{
    val actualReceiver = $receiver
    val actualClue = $clue
    try ${ expand(condition, '{ AssertionContext.standard }, 'actualReceiver) }
    catch { case failure: AssertionFailure => throw failure.withClue(actualClue) }
  }

  def unary[F[_]: Type](condition: Expr[Boolean], context: Expr[AssertionContext], receiver: Expr[Assertions], suspension: Expr[AssertionSuspension1[F]])(using Quotes): Expr[F[Unit]] =
    '{ val actualReceiver = $receiver; $suspension.suspend(${ expand(condition, context, 'actualReceiver) }) }

  def binary[F[_, _]: Type](condition: Expr[Boolean], context: Expr[AssertionContext], receiver: Expr[Assertions], suspension: Expr[AssertionSuspension2[F]])(using Quotes): Expr[F[Nothing, Unit]] =
    '{ val actualReceiver = $receiver; $suspension.suspend(${ expand(condition, context, 'actualReceiver) }) }

  def expand(condition: Expr[Boolean], context: Expr[AssertionContext], receiver: Expr[Assertions])(using quotes: Quotes): Expr[Unit] = {
    import quotes.reflect.*

    val sites = ArrayBuffer.empty[Expr[ObservationSite]]
    val booleanOwner = defn.BooleanClass
    val comparisonOwners = Set("scala.Any", "scala.AnyRef", "java.lang.Object", "scala.Boolean", "scala.Byte", "scala.Short", "scala.Char", "scala.Int", "scala.Long", "scala.Float", "scala.Double")
    val comparisonNames = Set("==", "!=", "<", "<=", ">", ">=", "eq", "ne")

    def sourceText(position: Position): Expr[CompiledText] = position.sourceCode match {
      case Some(text) => '{ CompiledText.Available(${ Expr(text) }) }
      case None => '{ CompiledText.Unavailable }
    }

    def point(offset: Int, line: Int, column: Int): Expr[SourcePoint] = '{ SourcePoint(${ Expr(offset) }, ${ Expr(line) }, ${ Expr(column) }) }
    def span(position: Position): Expr[SourceSpan] = {
      if (position.start >= 0 && position.end > position.start) {
        '{ SourceSpan.Range(${ point(position.start, position.startLine, position.startColumn) }, ${ point(position.end, position.endLine, position.endColumn) }) }
      } else if (position.start >= 0) {
        '{ SourceSpan.Point(${ point(position.start, position.startLine, position.startColumn) }) }
      } else '{ SourceSpan.Unavailable }
    }

    def site(term: Term, kind: Expr[ObservationKind]): Int = {
      val index = sites.size
      sites += '{ ObservationSite(${ span(term.pos) }, ${ sourceText(term.pos) }, $kind) }
      index
    }

    def observe(term: Term, index: Int, recorder: Expr[AssertionRecorder]): Term = term.tpe.widen.asType match {
      case '[value] => '{ $recorder.observe[value](${ Expr(index) }, ${ term.asExprOf[value] }) }.asTerm
    }

    // Planning assigns every site before emission, including branches that will not execute.
    sealed trait Node { def emit(recorder: Expr[AssertionRecorder]): Expr[Boolean] }
    final case class Opaque(term: Term, index: Int) extends Node {
      override def emit(recorder: Expr[AssertionRecorder]): Expr[Boolean] = observe(term, index, recorder).asExprOf[Boolean]
    }
    final case class Binary(left: Node, right: Node, and: Boolean, index: Int) extends Node {
      override def emit(recorder: Expr[AssertionRecorder]): Expr[Boolean] = {
        val result = if (and) '{ ${ left.emit(recorder) } && ${ right.emit(recorder) } } else '{ ${ left.emit(recorder) } || ${ right.emit(recorder) } }
        observe(result.asTerm, index, recorder).asExprOf[Boolean]
      }
    }
    final case class Negation(child: Node, index: Int) extends Node {
      override def emit(recorder: Expr[AssertionRecorder]): Expr[Boolean] = observe('{ !${ child.emit(recorder) } }.asTerm, index, recorder).asExprOf[Boolean]
    }
    final case class Comparison(original: Apply, selection: Select, left: Term, right: Term, index: Int, leftIndex: Int, rightIndex: Int) extends Node {
      override def emit(recorder: Expr[AssertionRecorder]): Expr[Boolean] = {
        val method = Select.copy(selection)(observe(left, leftIndex, recorder), selection.name)
        val result = Apply.copy(original)(method, List(observe(right, rightIndex, recorder)))
        observe(result, index, recorder).asExprOf[Boolean]
      }
    }

    def plan(term: Term): Node = term match {
      case Inlined(None, Nil, body) => plan(body)
      case Typed(body, _) => plan(body)
      case Apply(selection @ Select(left, name), List(right)) if selection.symbol.owner == booleanOwner && (name == "&&" || name == "||") =>
        val index = site(term, '{ ObservationKind.BooleanOperator })
        Binary(plan(left), plan(right), name == "&&", index)
      case Apply(selection @ Select(child, "unary_!"), Nil) if selection.symbol.owner == booleanOwner =>
        val index = site(term, '{ ObservationKind.BooleanOperator })
        Negation(plan(child), index)
      case selection @ Select(child, "unary_!") if selection.symbol.owner == booleanOwner =>
        val index = site(term, '{ ObservationKind.BooleanOperator })
        Negation(plan(child), index)
      case application @ Apply(selection @ Select(left, name), List(right)) if comparisonNames.contains(name) && comparisonOwners.contains(selection.symbol.owner.fullName) =>
        Comparison(application, selection, left, right, site(term, '{ ObservationKind.Comparison }), site(left, '{ ObservationKind.Operand }), site(right, '{ ObservationKind.Operand }))
      case _: Ident | _: Literal => Opaque(term, site(term, '{ ObservationKind.BooleanLeaf }))
      case _ => Opaque(term, site(term, '{ ObservationKind.Opaque }))
    }

    val node = plan(condition.asTerm)
    val siteExpressions = Varargs(sites.toSeq)
    val position = condition.asTerm.pos
    val path = position.sourceFile.path
    val virtual = position.sourceFile.getJPath.isEmpty
    '{
      val _ = $receiver
      val recorder = new AssertionRecorder(Vector($siteExpressions*))
      val result = ${ node.emit('recorder) }
      recorder.check(result, ${ Expr(path) }, ${ Expr(virtual) }, ${ span(position) }, ${ sourceText(position) }, $context)
    }
  }
}
