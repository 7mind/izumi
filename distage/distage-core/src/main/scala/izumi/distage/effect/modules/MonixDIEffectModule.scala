package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.ModuleDef
import monix.bio
import monix.bio.Task
import monix.bio.instances.{CatsConcurrentEffectForTask, CatsParallelForTask}
import monix.catnap.SchedulerEffect
import monix.execution.Scheduler

object MonixDIEffectModule extends MonixDIEffectModule

trait MonixDIEffectModule extends ModuleDef {
  implicit val scheduler: Scheduler = Scheduler.global
  implicit val ce: CatsConcurrentEffectForTask = new CatsConcurrentEffectForTask()(scheduler, bio.IO.defaultOptions)

  make[ConcurrentEffect[Task]]
    .from(new CatsConcurrentEffectForTask()(scheduler, bio.IO.defaultOptions)).aliased[Concurrent[Task]]

  addImplicit[Parallel[Task]].from(new CatsParallelForTask[Throwable])
  addImplicit[ContextShift[Task]]
  addImplicit[Timer[Task]]

  include(PolymorphicCatsDIEffectModule[Task])
}
