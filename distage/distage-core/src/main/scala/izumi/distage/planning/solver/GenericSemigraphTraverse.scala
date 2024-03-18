//package izumi.distage.planning.solver
//
//import izumi.distage.model.definition.ModuleBase
//import izumi.distage.model.definition.conflicts.{Annotated, Node}
//import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
//import izumi.distage.model.plan.Roots
//import izumi.distage.model.planning.PlanIssue.*
//import izumi.distage.model.planning.{ActivationChoices, AxisPoint, PlanIssue}
//import izumi.distage.model.reflection.{DIKey, SafeType}
//import izumi.distage.planning.SubcontextHandler
//import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
//import izumi.distage.planning.solver.SemigraphSolver.SemiEdgeSeq
//import izumi.distage.provisioning.strategies.ImportStrategyDefaultImpl
//import izumi.functional.IzEither.*
//import izumi.fundamentals.collections.IzCollections.*
//import izumi.fundamentals.collections.nonempty.{NEList, NESet}
//import izumi.fundamentals.collections.{ImmutableMultiMap, MutableMultiMap}
//import izumi.reflect.TagK
//
//import java.util.concurrent.TimeUnit
//import scala.collection.mutable
//import scala.concurrent.duration.FiniteDuration
//
//class GenericSemigraphTraverse[Err](
//  preps: GraphQueries,
//  subcontextHandler: SubcontextHandler[Err],
//) {
//
//  def traverse[F[_]: TagK](
//    bindings: ModuleBase,
//    roots: Roots,
//    providedKeys: DIKey => Boolean,
//    excludedActivations: Set[NESet[AxisPoint]],
//    ignoreIssues: Boolean,
//  ): PlanVerifierResult = {
//    val before = System.currentTimeMillis()
//    var after = before
//
////    val verificationHandler = new SubcontextHandler.VerificationHandler(this, excludedActivations)
//    (for {
//      ops <- preps.computeOperationsUnsafe(subcontextHandler, bindings).map(_.toSeq)
//    } yield {
//      val allAxis: Map[String, Set[String]] = ops.flatMap(_._1.axis).groupBy(_.axis).map {
//        case (axis, points) =>
//          (axis, points.map(_.value).toSet)
//      }
//      val (mutators, defns) = ops.partition(_._3.isMutator)
//      val justOps = defns.map { case (k, op, _) => k -> op }
//
//      val setOps = preps
//        .computeSetsUnsafe(justOps)
//        .map {
//          case (k, (s, _)) =>
//            (Annotated(k, None, Set.empty), Node(s.members, s))
//
//        }.toMultimapView
//        .map {
//          case (k, v) =>
//            val members = v.flatMap(_.deps).toSet
//            (k, Node(members, v.head.meta.copy(members = members): InstantiationOp))
//        }
//        .toSeq
//
//      val opsMatrix: Seq[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = preps.toDeps(justOps)
//
//      val matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp] = SemiEdgeSeq(opsMatrix ++ setOps)
//
//      val matrixToTrace = defns.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap
//      val justMutators = mutators.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap
//
//      val rootKeys: Set[DIKey] = preps.getRoots(roots, justOps)
//      val execOpIndex: MutableMultiMap[DIKey, InstantiationOp] = preps.executableOpIndex(matrix)
//
//      val mutVisited = mutable.HashSet.empty[(DIKey, Set[AxisPoint])]
//      val effectType = SafeType.getK[F]
//
//      val issues =
//        try {
//          trace(allAxis, mutVisited, matrixToTrace, execOpIndex, justMutators, providedKeys, excludedActivations, rootKeys, effectType, bindings, ignoreIssues)
//        } finally {
//          after = System.currentTimeMillis()
//        }
//
//      val visitedKeys = mutVisited.map(_._1).toSet
//      val time = FiniteDuration(after - before, TimeUnit.MILLISECONDS)
//
//      NESet.from(issues) match {
//        case issues @ Some(_) => PlanVerifierResult.Incorrect(issues, visitedKeys, time)
//        case None => PlanVerifierResult.Correct(visitedKeys, time)
//      }
//    }) match {
//      case Left(errors) =>
//        after = System.currentTimeMillis()
//        val time = FiniteDuration(after - before, TimeUnit.MILLISECONDS)
//        val issues = errors.map(f => PlanIssue.CantVerifyLocalContext(f)).toSet[PlanIssue]
//        PlanVerifierResult.Incorrect(Some(NESet.unsafeFrom(issues)), Set.empty, time)
//      case Right(value) => value
//    }
//
//  }
//
//  protected[this] def trace(
//    allAxis: Map[String, Set[String]],
//    allVisited: mutable.HashSet[(DIKey, Set[AxisPoint])],
//    matrix: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
//    execOpIndex: MutableMultiMap[DIKey, InstantiationOp],
//    justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
//    providedKeys: DIKey => Boolean,
//    excludedActivations: Set[NESet[AxisPoint]],
//    rootKeys: Set[DIKey],
//    effectType: SafeType,
//    bindings: ModuleBase,
//    ignoreIssues: Boolean,
//  ): Set[PlanIssue] = {
//
//    @inline def go(visited: Set[DIKey], current: Set[(DIKey, DIKey)], currentActivation: Set[AxisPoint]): RecursionResult = RecursionResult(current.iterator.map {
//      case (key, dependee) =>
//        if (visited.contains(key) || allVisited.contains((key, currentActivation))) {
//          Right(Iterator.empty)
//        } else {
//          @inline def reportMissing[A](key: DIKey, dependee: DIKey): Left[NEList[MissingImport], Nothing] = {
//            val origins = preps.allImportingBindings(matrix, currentActivation)(key, dependee)
//            val similarBindings = ImportStrategyDefaultImpl.findSimilarImports(bindings, key)
//            Left(NEList(MissingImport(key, dependee, origins, similarBindings.similarSame, similarBindings.similarSub)))
//          }
//
//          @inline def reportMissingIfNotProvided[A](key: DIKey, dependee: DIKey)(orElse: => Either[NEList[PlanIssue], A]): Either[NEList[PlanIssue], A] = {
//            if (providedKeys(key)) orElse else reportMissing(key, dependee)
//          }
//
//          matrix.get(key) match {
//            case None =>
//              reportMissingIfNotProvided(key, dependee)(Right(Iterator.empty))
//
//            case Some(allOps) =>
//              val ops = allOps.filterNot(o => preps.isIgnoredActivation(excludedActivations, o._2))
//              val ac = ActivationChoices(currentActivation)
//
//              val withoutCurrentActivations = {
//                val withoutImpossibleActivationsIter = ops.iterator.filter(ac `allValid` _._2)
//                withoutImpossibleActivationsIter.map {
//                  case (op, activations) =>
//                    (op, activations diff currentActivation, activations)
//                }.toSet
//              }
//
//              for {
//                // we ignore activations for set definitions
//                opsWithMergedSets <- {
//                  val (setOps, otherOps) = withoutCurrentActivations.partitionMap {
//                    case (s: CreateSet, _, _) => Left(s)
//                    case a => Right(a)
//                  }
//                  for {
//                    mergedSets <- setOps.groupBy(_.target).values.biTraverse {
//                      ops =>
//                        for {
//                          members <- ops.iterator
//                            .flatMap(_.members)
//                            .biFlatTraverse {
//                              memberKey =>
//                                matrix.get(memberKey) match {
//                                  case Some(value) if value.sizeIs == 1 =>
//                                    if (ac.allValid(value.head._2)) Right(List(memberKey)) else Right(Nil)
//                                  case Some(value) =>
//                                    Left(NEList(InconsistentSetMembers(memberKey, NEList.unsafeFrom(value.iterator.map(_._1.origin.value).toList))))
//                                  case None =>
//                                    reportMissingIfNotProvided(memberKey, key)(Right(List(memberKey)))
//                                }
//                            }.to(Set)
//                        } yield {
//                          (ops.head.copy(members = members), Set.empty[AxisPoint], Set.empty[AxisPoint])
//                        }
//                    }
//                  } yield otherOps ++ mergedSets
//                }
//                _ <-
//                  if (!ignoreIssues && opsWithMergedSets.isEmpty && !providedKeys(key)) { // provided key cannot have unsaturated axis
//                    val allDefinedPoints = ops.flatMap(_._2).groupBy(_.axis)
//                    val probablyUnsaturatedAxis = allDefinedPoints.iterator.flatMap {
//                      case (axis, definedPoints) =>
//                        NESet
//                          .from(currentActivation.filter(_.axis == axis).diff(definedPoints))
//                          .map(UnsaturatedAxis(key, axis, _))
//                    }.toList
//
//                    if (probablyUnsaturatedAxis.isEmpty) {
//                      reportMissing(key, dependee)
//                    } else {
//                      Left(NEList.unsafeFrom(probablyUnsaturatedAxis))
//                    }
//                  } else {
//                    Right(())
//                  }
//                next <- checkConflicts(allAxis, opsWithMergedSets, execOpIndex, excludedActivations, effectType, ignoreIssues)
//              } yield {
//                allVisited.add((key, currentActivation))
//
//                val mutators =
//                  justMutators.getOrElse(key, Set.empty).iterator.filter(ac `allValid` _._2).flatMap(m => GenericSemigraphTraverse.depsOf(execOpIndex, m._1)).toSeq
//
//                val goNext = next.iterator.map {
//                  case (nextActivation, nextDeps) =>
//                    () =>
//                      go(
//                        visited = visited + key,
//                        current = (nextDeps ++ mutators).map(_ -> key),
//                        currentActivation = currentActivation ++ nextActivation,
//                      )
//                }
//
//                goNext
//              }
//          }
//        }
//    })
//
//    // for trampoline
//    sealed trait RecResult {
//      type RecursionResult <: Iterator[Either[NEList[PlanIssue], Iterator[() => RecursionResult]]]
//    }
//    type RecursionResult = RecResult#RecursionResult
//    @inline def RecursionResult(a: Iterator[Either[NEList[PlanIssue], Iterator[() => RecursionResult]]]): RecursionResult = a.asInstanceOf[RecursionResult]
//
//    // trampoline
//    val errors = Set.newBuilder[PlanIssue]
//    val remainder = mutable.Stack(() => go(Set.empty, Set.from(rootKeys.map(r => r -> r)), Set.empty))
//
//    while (remainder.nonEmpty) {
//      val i = remainder.pop().apply()
//      while (i.hasNext) {
//        i.next() match {
//          case Right(nextSteps) =>
//            remainder pushAll nextSteps
//          case Left(newErrors) =>
//            errors ++= newErrors
//        }
//      }
//    }
//
//    errors.result()
//  }
//
//}
