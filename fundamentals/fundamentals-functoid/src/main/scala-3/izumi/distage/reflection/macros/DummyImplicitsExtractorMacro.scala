package izumi.distage.reflection.macros

import scala.quoted.Quotes

final class DummyImplicitsExtractorMacro[Q <: Quotes](using val qctx: Q) {
  import qctx.reflect.*

  private val dummyTypeSymbol: Symbol = TypeRepr.of[FunctoidDummyImplicit].typeSymbol

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
    val treeAccumulator = new TreeAccumulator[Set[DummyArg]] {
      override def foldTree(x: Set[DummyArg], tree: qctx.reflect.Tree)(owner: qctx.reflect.Symbol): Set[DummyArg] = {
        println("entered fold tree: " + tree.show(using Printer.TreeStructure))
        tree match {
          case fun @ Apply(inner: Apply, args) =>
            println("inner fun tpe: " + fun.fun.tpe.widenTermRefByName)
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val extracted = foldTrees(Set.empty, args)(owner)
                val newTypes = update(extracted, lt.paramTypes, extracted.nonEmpty)
                foldTree(newTypes ++ x, inner)(owner)
            }
          case fun @ Apply(s: Select, args) => foldOverTree(x, fun)(owner)
          case s: Select => foldOverTree(x, s)(owner)
          case i: Ident =>
            if (i.tpe.baseClasses.contains(dummyTypeSymbol)) {
              x + DummyArg(DummyImplicitArg(i, i.tpe), false)
            } else x
          case fun @ Apply(t: TypeApply, args) =>
            println("type apply fun tpe: " + fun.fun.tpe.widenTermRefByName + " of " + fun.show)
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val (fromArgs, types) = args.zip(lt.paramTypes).flatMap { 
                  case (arg, tpe) => 
                    val res = foldTree(Set.empty, arg)(owner)
                    if (res.isEmpty) None
                    else Some(res -> tpe)
                }.unzip
                val fromTerm = foldTree(Set.empty, t)(owner)
                println("args size: " + args.size)
                println("lt types: " + lt.paramTypes + " size " + lt.paramTypes.size)
                println("from args: " + fromArgs + " size " + fromArgs.size)
                println("from term: " + fromTerm + " size " + fromTerm.size)
                val newTypesFromArgs =
                  if (fromArgs.flatten.exists(_.notUpdated)) update(fromArgs.flatten.toSet, types, true)
                  else fromArgs.flatten.toSet
                val newTypesFromTerm =
                  if (fromTerm.exists(_.notUpdated)) update(fromTerm, lt.paramTypes, true)
                  else fromTerm
                newTypesFromTerm ++ newTypesFromArgs ++ x
              case _ => foldTrees(x, args)(owner) ++ x
            }

          case fun @ Apply(_, args) =>
            println("fun tpe: " + fun.fun.tpe.widenTermRefByName)
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val extracted = foldTrees(Set.empty, args)(owner)
                val newTypes = update(extracted, lt.paramTypes, extracted.nonEmpty)
                newTypes ++ x
              case _ => foldTrees(x, args)(owner) ++ x
            }

          case _ => foldOverTree(x, tree)(owner)
        }
      }
    }
    treeAccumulator
      .foldTree(Set.empty, term)(owner)
      .toList
      .map(_.dummy)
  }

  private def update(args: Set[DummyArg], types: List[TypeRepr], dummy: Boolean): Set[DummyArg] = {
    if (dummy) {
      args.zip(types).map { case (arg, tpe) => arg.update(tpe, true) }
    } else Set.empty
  }

  private def hasDummy(args: List[Term]): Boolean = {
    args.exists(_.tpe.baseClasses.contains(dummyTypeSymbol))
  }
}
