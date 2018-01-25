package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.EqualitySafeType
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.{IncompatibleTypesException, InvalidPlanException}
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

import scala.collection.mutable

class SetStrategyDefaultImpl extends SetStrategy {
  def makeSet(context: ProvisioningContext, op: ExecutableOp.SetOp.CreateSet): Seq[OpResult.NewInstance] = {
    import op._
    // target is guaranteed to be a Set
    val scalaCollectionSetType = EqualitySafeType.get[collection.Set[_]]
    val erasure = scalaCollectionSetType.tpe.typeSymbol

    if (!tpe.tpe.baseClasses.contains(erasure)) {
      throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
        s"For $target expected $tpe to be a sub-class of $scalaCollectionSetType, but it isn't!"
        , scalaCollectionSetType
        , tpe)
    }

    Seq(OpResult.NewInstance(target, mutable.HashSet[Any]()))
  }

  def addToSet(context: ProvisioningContext, op: ExecutableOp.SetOp.AddToSet): Seq[OpResult.SetElement] = {
    // value is guaranteed to have already been instantiated or imported
    val targetElement = context.fetchKey(op.element) match {
      case Some(value) =>
        value
      case _ =>
        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
          s" but the instance has not been initialized! Set: ${op.target}, instance: ${op.element}")
    }

    // set is guaranteed to have already been added
    val targetSet = context.fetchKey(op.target) match {
      case Some(set: mutable.HashSet[_]) =>
        set
      case Some(somethingElse) =>
        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
          s" but target Set is not a Set! It's ${somethingElse.getClass.getName}")

      case _ =>
        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
          s" but Set has not been initialized! Set: ${op.target}, instance: ${op.element}")
    }

    Seq(OpResult.SetElement(targetSet.asInstanceOf[mutable.HashSet[Any]], targetElement))
  }
}

object SetStrategyDefaultImpl {
  final val instance = new SetStrategyDefaultImpl()
}

