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

    override def toString: String = {
      s"DummyImplicitArg(${term.show}, ${tpe.show}, ${providedImplicit.map(_.show)})"
    }
  }

  private final case class DummyArg(
    dummy: DummyImplicitArg,
    updated: Boolean,
  ) {
    def notUpdated: Boolean = !updated
    def update(tpe: TypeRepr): DummyArg =
      this.copy(dummy = this.dummy.copy(tpe = tpe), updated = true)
  }

  def extractDummyArguments(term: Term, owner: Symbol): List[DummyImplicitArg] = {
    val treeAccumulator = new TreeAccumulator[List[DummyArg]] {
      override def foldTree(acc: List[DummyArg], tree: qctx.reflect.Tree)(owner: qctx.reflect.Symbol): List[DummyArg] = {
        tree match {
          case fun @ Apply(inner: Apply, args) =>
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val extracted = foldTrees(List.empty, args)(owner)
                val newTypes = update(extracted, lt.paramTypes)
                foldTree(newTypes ++ acc, inner)(owner)
            }
          case Apply(_: Select, _) =>
            foldOverTree(acc, tree)(owner)
          case s @ Select(p, _) =>
            val res = foldOverTree(acc, s)(owner)
            res.map {
              case d @ DummyArg(_, false) => d.update(p.tpe.widenTermRefByName)
              case d => d
            }
          case i: Ident =>
            if (i.tpe.baseClasses.contains(dummyTypeSymbol)) {
              acc :+ DummyArg(DummyImplicitArg(i, i.tpe), false)
            } else acc
          case fun @ Apply(t: TypeApply, args) =>
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
//                println(
//                  s"got methodtype ${lt.show} ${lt.show(using Printer.TypeReprStructure)} ${lt.paramTypes.exists(_.typeSymbol == defn.RepeatedParamClass)} ${lt.paramTypes.collect {
//                      case x if x.typeSymbol == defn.RepeatedParamClass =>
//                        x match {
//                          case AppliedType(_, List(arg)) => s"REPEATED:${arg.show}"
//                        }
//                    }}"
//                )
                val varargType = lt.paramTypes.lastOption match {
                  case Some(AppliedType(t, targ :: Nil)) if t.typeSymbol == defn.RepeatedParamClass =>
                    Some(targ)
                  case _ =>
                    None
                }
                val paramTypesWithVararg = varargType match {
                  case None => lt.paramTypes
                  case Some(vararg) =>
                    val repeat = args.size - (lt.paramTypes.size - 1)
                    lt.paramTypes.init ::: List.fill(repeat)(vararg)
                }
                val (fromArgs, types) = args
                  .zip(paramTypesWithVararg)
                  .flatMap {
                    case (arg, tpe) =>
                      // FIXME carry forward `tpe` in the accumulator, instead of fixing-up "non-updated" dummies afterwards
                      foldTree(List.empty, arg)(owner) match {
                        case Nil => None
                        case args => Some(args -> tpe)
                      }
                  }.unzip
                val fromTerm = foldTree(List.empty, t)(owner)
                val newTypesFromArgs =
                  if (fromArgs.flatten.exists(_.notUpdated)) update(fromArgs.flatten, types)
                  else fromArgs.flatten
                val newTypesFromTerm =
                  if (fromTerm.exists(_.notUpdated)) update(fromTerm, paramTypesWithVararg)
                  else fromTerm

                val res = newTypesFromTerm ++ newTypesFromArgs ++ acc
//                if (res.nonEmpty) {
//                  println(
//                    s"Got apply(TypeApply) dummies from `${fun.show}`\nnewTypesFromTerm=$newTypesFromTerm\nnewTypesFromArgs=$newTypesFromArgs\ntypes=$types\nfromTerm=$fromTerm\nfromargs=$fromArgs"
//                  )
//                }
                res
              case _ => foldTrees(acc, args)(owner) ++ acc
            }

          case fun @ Apply(_, args) =>
            fun.fun.tpe.widenTermRefByName match {
              case lt: MethodType =>
                val extracted = foldTrees(List.empty, args)(owner)
                val newTypes = update(extracted, lt.paramTypes)
                newTypes ++ acc
              case _ => foldTrees(acc, args)(owner) ++ acc
            }

          case _ => foldOverTree(acc, tree)(owner)
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
        extractDummySymbolsFromImplicitSearch(newSym :: knownSyms)
      case _: ImplicitSearchFailure => knownSyms
    }
  }

  private def update(args: List[DummyArg], types: List[TypeRepr]): List[DummyArg] = {
    args.zip(types).map { case (arg, tpe) => if (arg.notUpdated) arg.update(tpe) else arg }
  }

}
