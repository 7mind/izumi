package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan}
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._

import scala.util.{Failure, Success, Try}


// TODO: add introspection capabilities
class ProvisionerDefaultImpl
(
  setStrategy: SetStrategy
  , proxyStrategy: ProxyStrategy
  , factoryStrategy: FactoryStrategy
  , traitStrategy: TraitStrategy
  , factoryProviderStrategy: FactoryProviderStrategy
  , providerStrategy: ProviderStrategy
  , classStrategy: ClassStrategy
  , importStrategy: ImportStrategy
  , instanceStrategy: InstanceStrategy
  , failureHandler: ProvisioningFailureInterceptor
) extends Provisioner with OperationExecutor {
  override def provision(plan: FinalPlan, parentContext: Locator): ProvisionImmutable = {
    val provisioingContext = ProvisionActive()
    val (imports, theRest) = plan.steps.partition(_.isInstanceOf[ImportDependency])

    processImports(parentContext, provisioingContext, imports)

    val provisions = theRest.foldLeft(provisioingContext) {
      case (active, step) =>
        val failureContext = ProvisioningFailureContext(parentContext, active, step)

        val maybeResult = Try(execute(LocatorContext(active.toImmutable, parentContext), step))
          .recoverWith(failureHandler.onExecutionFailed(failureContext))

        maybeResult match {
          case Success(results) =>
            interpret(failureContext, active, results)

          case Failure(f) =>
            failureHandler.onStepFailure(failureContext, f)
        }
    }

    ProvisionImmutable(provisions.instances, provisions.imports)
  }

  private def processImports(parentContext: Locator, provisioingContext: ProvisionActive, imports: Seq[ExecutableOp]): Unit = {
    val importContext = LocatorContext(provisioingContext.toImmutable, parentContext)
    val (good, bad) = imports.map(i => OperationWithResult(i, Try(execute(importContext, i))))
      .partition(_.result.isSuccess)

    val importResults = good
      .collect {
        case OperationWithResult(op, Success(r)) =>
          op -> r.map { result => Try(interpretResult(provisioingContext, result)) }
      }

    val (badImportResults, _) = importResults.partition(_._2.exists(_.isFailure))


    if (bad.nonEmpty || badImportResults.nonEmpty) {
      val failures = bad.collect {
        case OperationWithResult(op, f@Failure(_)) =>
          OperationWithResult(op, f)
      }
      val exceptions = badImportResults.map {
        case (op, f) =>
          f.collect {
            case Failure(e) =>
              OperationFailed(op, e)

          }
      }

      failureHandler.onImportsFailed(ProvisioningMassFailureContext(parentContext, provisioingContext), failures, exceptions.flatten)
    }
  }

  private def interpret(failureContext: ProvisioningFailureContext, active: ProvisionActive, results: Seq[OpResult]) = {
    results.foldLeft(active) {
      case (acc, result) =>
        Try(interpretResult(active, result))
          .recoverWith(failureHandler.onBadResult(failureContext)) match {
          case Success(_) =>
            acc
          case Failure(f) =>
            failureHandler.onStepOperationFailure(failureContext, result, f)
        }
    }
  }

  private def interpretResult(active: ProvisionActive, result: OpResult): Unit = {
    result match {
      case OpResult.NewImport(target, value) =>
        value match {
          case _ if active.imports.contains(target) =>
            throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
          case opResult: OpResult =>
            throw new TriedToAddSetIntoSetException(s"Pathological case. Tried to add set into itself: $target -> $value", target, opResult)
          case _ =>
            active.imports += (target -> value)
        }

      case OpResult.NewInstance(target, value) =>
        value match {
          case _ if active.instances.contains(target) =>
            throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
          case opResult: OpResult =>
            throw new TriedToAddSetIntoSetException(s"Pathological case. Tried to add set into itself: $target -> $value", target, opResult)
          case _ =>
            active.instances += (target -> value)
        }

      case OpResult.UpdatedSet(target, instance) =>
        active.instances += (target -> instance)

      case OpResult.DoNothing() =>
        ()
    }
  }

  def execute(context: ProvisioningKeyProvider, step: ExecutableOp): Seq[OpResult] = {
    step match {
      case op: ImportDependency =>
        importStrategy.importDependency(context, op)

      case op: WiringOp.ReferenceInstance =>
        instanceStrategy.getInstance(context, op)

      case op: WiringOp.ReferenceKey =>
        instanceStrategy.getInstance(context, op)

      case op: WiringOp.CallProvider =>
        providerStrategy.callProvider(context, this, op)

      case op: WiringOp.CallFactoryProvider =>
        factoryProviderStrategy.callFactoryProvider(context, this, op)

      case op: WiringOp.InstantiateClass =>
        classStrategy.instantiateClass(context, op)

      case op: CreateSet =>
        setStrategy.makeSet(context, op)

      case op: WiringOp.InstantiateTrait =>
        traitStrategy.makeTrait(context, op)

      case op: WiringOp.InstantiateFactory =>
        factoryStrategy.makeFactory(context, this, op)

      case op: ProxyOp.MakeProxy =>
        proxyStrategy.makeProxy(context, op)

      case op: ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, op)
    }
  }


}

