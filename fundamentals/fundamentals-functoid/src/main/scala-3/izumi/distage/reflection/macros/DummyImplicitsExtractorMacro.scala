package izumi.distage.reflection.macros

import scala.annotation.tailrec
import scala.quoted.Quotes

final class DummyImplicitsExtractorMacro[Q <: Quotes](using val qctx: Q) {
  import qctx.reflect.*

  private val dummyType: TypeRepr = TypeRepr.of[FunctoidDummyImplicit]
  private val dummyTypeSymbol: Symbol = dummyType.typeSymbol

  final case class DummyImplicitArg(
    term: Term,
    tpe: TypeRepr,
    providedImplicit: Option[Term] = None,
  ) {
    def withProvidedImplicit(i: Option[Term]): DummyImplicitArg = {
      this.copy(providedImplicit = i)
    }
  }

  private final case class DummyArg(
    dummy: DummyImplicitArg,
    updated: Boolean,
  ) {
    def notUpdated: Boolean = !updated
    def update(tpe: TypeRepr, updated: Boolean): DummyArg =
      this.copy(dummy = this.dummy.copy(tpe = tpe), updated = updated)
  }

  def extractDummyArguments(term: Term, owner: Symbol): List[DummyImplicitArg] = {
    val treeAccumulator = new TreeAccumulator[List[DummyArg]] {
      override def foldTree(x: List[DummyArg], tree: qctx.reflect.Tree)(owner: qctx.reflect.Symbol): List[DummyArg] = {
        tree match {
          case fun @ Apply(inner: Apply, args) =>
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val extracted = foldTrees(List.empty, args)(owner)
                val newTypes = update(extracted, lt.paramTypes, extracted.nonEmpty)
                foldTree(newTypes ++ x, inner)(owner)
            }
          case fun @ Apply(s: Select, args) => foldOverTree(x, fun)(owner)
          case s: Select => foldOverTree(x, s)(owner)
          case i: Ident =>
            if (i.tpe.baseClasses.contains(dummyTypeSymbol)) {
              x :+ DummyArg(DummyImplicitArg(i, i.tpe), false)
            } else x
          case fun @ Apply(t: TypeApply, args) =>
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val (fromArgs, types) = args
                  .zip(lt.paramTypes).flatMap {
                    case (arg, tpe) =>
                      foldTree(List.empty, arg)(owner) match {
                        case Nil => None
                        case args => Some(args -> tpe)
                      }
                  }.unzip
                val fromTerm = foldTree(List.empty, t)(owner)
                val newTypesFromArgs =
                  if (fromArgs.flatten.exists(_.notUpdated)) update(fromArgs.flatten, types, true)
                  else fromArgs.flatten
                val newTypesFromTerm =
                  if (fromTerm.exists(_.notUpdated)) update(fromTerm, lt.paramTypes, true)
                  else fromTerm
                newTypesFromTerm ++ newTypesFromArgs ++ x
              case _ => foldTrees(x, args)(owner) ++ x
            }

          case fun @ Apply(_, args) =>
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val extracted = foldTrees(List.empty, args)(owner)
                val newTypes = update(extracted, lt.paramTypes, extracted.nonEmpty)
                newTypes ++ x
              case _ => foldTrees(x, args)(owner) ++ x
            }

          case _ => foldOverTree(x, tree)(owner)
        }
      }
    }
    treeAccumulator
      .foldTree(List.empty, term)(owner)
      .distinct
      .map(_.dummy)
  }

  @tailrec def extractDummySymbolsFromImplicitSearch(knownSyms: List[Symbol]): List[Symbol] = {
    Implicits.searchIgnoring(dummyType)(knownSyms*) match {
      case succ: ImplicitSearchSuccess =>
        val newSym = succ.tree.symbol
        println(s"XYGot newSYm $newSym")
        extractDummySymbolsFromImplicitSearch(newSym :: knownSyms)
      case x =>
        println(s"XYGOt failure $x")
        knownSyms
    }
  }

  private def update(args: List[DummyArg], types: List[TypeRepr], dummy: Boolean): List[DummyArg] = {
    if (dummy) {
      args.zip(types).map { case (arg, tpe) => arg.update(tpe, true) }
    } else List.empty
  }

  private def hasDummy(args: List[Term]): Boolean = {
    args.exists(_.tpe.baseClasses.contains(dummyTypeSymbol))
  }
}
