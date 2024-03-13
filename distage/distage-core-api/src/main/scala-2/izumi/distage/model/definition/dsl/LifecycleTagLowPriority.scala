package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.Lifecycle

import scala.language.experimental.macros

trait LifecycleTagLowPriority {
  /**
    * The `resourceTag` implicit above works perfectly fine, this macro here is exclusively
    * a workaround for highlighting in Intellij IDEA
    *
    * (it's also used to display error trace from TagK's @implicitNotFound)
    *
    * TODO: report to IJ bug tracker
    */
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[Any, Any]]: LifecycleAdapters.LifecycleTag[R] = /*scalafmt*/
    macro LifecycleTagMacro.fakeResourceTagMacroIntellijWorkaroundImpl[R]
}

trait ZIOEnvLifecycleTagLowPriority1 {
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[Any, Any], T]: LifecycleAdapters.ZIOEnvLifecycleTag[R, T] = /*scalafmt*/
    macro LifecycleTagMacro.fakeResourceTagMacroIntellijWorkaroundImpl[R]
}
