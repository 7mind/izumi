package izumi.distage.testkit.runner.di

import izumi.distage.model.plan.{ExecutableOp, Plan}
import izumi.distage.model.reflection.{DIKey, SetKeyMeta}
import izumi.distage.testkit.model.{DistageTest, TestTree}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.RunnerFailure
import izumi.distage.testkit.runner.impl.TestPlanner.{PlannedTests, PlanningFailure}
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

private[di] object DistagePlanInspection {
  def apply(planned: PlannedTests[AnyF], tests: Vector[TestDescriptor]): PlanInspection = {
    def index(test: DistageTest[AnyF]): Int = {
      val uid = test.testMeta.uid
      require(uid >= 0L && uid < tests.size.toLong, "Prepared plan contains an unknown transient test identity")
      uid.toInt
    }
    def ids(selected: Seq[DistageTest[AnyF]]): Vector[TestId] = selected.toVector.sortBy(index).map(test => tests(index(test)).id)
    def graphPlans(tree: TestTree[AnyF]): Vector[Plan] = {
      Vector(tree.levelPlan) ++ tree.groups.flatMap(_.preparedTests.map(_.timedPlan.out)) ++ tree.nested.flatMap(graphPlans)
    }
    val environments = planned.good.flatMap(_.envs.toSeq).groupBy(_._1).flatMap(_._2).toVector.sortBy { case (_, tree) =>
      tree.allTests.map(test => index(test.test)).sorted.headOption.getOrElse(Int.MaxValue)
    }
    val graphs = environments.flatMap { case (environment, tree) => Vector(environment.runtimePlan) ++ graphPlans(tree) }
    val keys = graphs.flatMap(plan => plan.plan.meta.nodes.keys ++ plan.plan.predecessors.links.values.flatten).distinct.sortBy(label).zipWithIndex.map { case (key, position) =>
      key -> DependencyKey(DependencyKeyId(position), label(key))
    }
    val keyIds = keys.map { case (key, description) => key -> description.id }.toMap
    def steps(plan: Plan): Vector[PlanStep] = plan.plan.meta.nodes.toVector.map { case (key, op) =>
      val operation = op match {
        case _: ExecutableOp.ImportDependency => PlanOperation.Import
        case _: ExecutableOp.AddRecursiveLocatorRef => PlanOperation.LocatorReference
        case _: ExecutableOp.CreateSet => PlanOperation.CreateSet
        case _: ExecutableOp.WiringOp.CallProvider => PlanOperation.CallProvider
        case _: ExecutableOp.WiringOp.UseInstance => PlanOperation.UseInstance
        case _: ExecutableOp.WiringOp.ReferenceKey => PlanOperation.ReferenceKey
        case _: ExecutableOp.WiringOp.CreateSubcontext => PlanOperation.CreateSubcontext
        case _: ExecutableOp.MonadicOp.ExecuteEffect => PlanOperation.ExecuteEffect
        case _: ExecutableOp.MonadicOp.AllocateResource => PlanOperation.AllocateResource
        case _: ExecutableOp.ProxyOp.MakeProxy => PlanOperation.MakeProxy
        case _: ExecutableOp.ProxyOp.InitProxy => PlanOperation.InitProxy
      }
      PlanStep(keyIds(key), operation, plan.plan.predecessors.links(key).toVector.map(keyIds).sortBy(_.value))
    }.sortBy(_.key.value)
    def treeScopes(tree: TestTree[AnyF], path: Vector[Int]): Vector[PlanScope] = {
      val selected = ids(tree.allTests.map(_.test))
      val individual = tree.groups.flatMap(_.preparedTests).sortBy(test => index(test.test)).zipWithIndex.map { case (test, child) =>
        PlanScope(PlanScopeId(path :+ child), PlanScopeKind.Test, ids(Vector(test.test)), steps(test.timedPlan.out))
      }.toVector
      val nested = tree.nested.sortBy(tree => tree.allTests.map(test => index(test.test)).sorted.headOption.getOrElse(Int.MaxValue)).zipWithIndex.flatMap { case (child, position) =>
        treeScopes(child, path :+ (individual.size + position))
      }.toVector
      Vector(PlanScope(PlanScopeId(path), PlanScopeKind.Memoization, selected, steps(tree.levelPlan))) ++ individual ++ nested
    }
    val scopes = environments.zipWithIndex.flatMap { case ((environment, tree), root) =>
      val path = Vector(root)
      Vector(PlanScope(PlanScopeId(path), PlanScopeKind.Runtime, ids(tree.allTests.map(_.test)), steps(environment.runtimePlan))) ++ treeScopes(tree, path :+ 0)
    }
    val environmentFailures = planned.bad.toVector.map { case (selected, failure) =>
      val cause = failure match {
        case PlanningFailure.Exception(throwable) => throwable
        case PlanningFailure.DIErrors(errors) => errors.aggregateErrors
      }
      PlanFailure(ids(selected), RunnerFailure.fromThrowable(FailurePhase.Planning, cause))
    }
    val individualFailures = environments.flatMap { case (_, tree) =>
      tree.allFailures.map(test => PlanFailure(ids(Vector(test.test)), RunnerFailure.fromThrowable(FailurePhase.Planning, test.timedPlan.out.aggregateErrors)))
    }
    PlanInspection(keys.map(_._2), scopes, environmentFailures ++ individualFailures)
  }

  private def label(key: DIKey): String = key match {
    case basic: DIKey.BasicKey => basic.toString
    case DIKey.SetElementKey(set, reference, disambiguator) =>
      val suffix = disambiguator match {
        case SetKeyMeta.NoMeta => ""
        case _: SetKeyMeta.WithImpl => "#implementation"
        case SetKeyMeta.WithAutoset(base) => "#autoset:" + label(base)
      }
      "{set." + label(set) + "/" + label(reference) + suffix + "}"
    case DIKey.ProxyInitKey(proxied) => "{proxyinit." + label(proxied) + "}"
    case DIKey.ProxyControllerKey(proxied, _) => "{proxyref." + label(proxied) + "}"
    case DIKey.ResourceKey(wrapped, effect) => "{resource." + label(wrapped) + "/" + effect + "}"
    case DIKey.EffectKey(wrapped, effect) => "{effect." + label(wrapped) + "/" + effect + "}"
  }
}
