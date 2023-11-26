package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.definition.errors.ProvisionerIssue.MissingImport
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.strategies.ImportStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.distage.model.reflection.DIKey

class ImportStrategyDefaultImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningKeyProvider, plan: Plan, op: ImportDependency): Either[MissingImport, Seq[NewObjectOp]] = {
    context.importKey(op.target) match {
      case Some(v) =>
        Right(Seq(NewObjectOp.NewImport(op.target, v)))
      case _ =>
        val similar = ImportStrategyDefaultImpl.findSimilarImports(plan.input.bindings, op.target)
        Left(MissingImport(op, similar.similarSame, similar.similarSub))
    }
  }

}

object ImportStrategyDefaultImpl {
  case class SimilarBindings(similarSame: Set[Binding], similarSub: Set[Binding])

  def findSimilarImports(bindings: ModuleBase, target: DIKey): SimilarBindings = {
    def isSimilarSameTypeBinding(to: DIKey)(binding: Binding): Boolean = {
      to.tpe =:= binding.key.tpe
    }

    def isSimilarSubTypeBinding(to: DIKey)(binding: Binding): Boolean = {
      !(to.tpe =:= binding.key.tpe) && (binding.key.tpe <:< to.tpe)
    }
    val similarSame = bindings.bindings.filter(isSimilarSameTypeBinding(target))
    val similarSub = bindings.bindings.filter(isSimilarSubTypeBinding(target))
    SimilarBindings(similarSame, similarSub)
  }
}
