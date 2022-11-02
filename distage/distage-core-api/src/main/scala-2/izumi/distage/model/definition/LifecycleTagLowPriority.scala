package izumi.distage.model.definition

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
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[Any, Any]]: LifecycleTagImpl[R] = macro
    LifecycleTagMacro.fakeResourceTagMacroIntellijWorkaroundImpl[R]
}

trait TrifunctorHasLifecycleTagLowPriority1 {
  implicit final def fakeResourceTagMacroIntellijWorkaround[R <: Lifecycle[Any, Any], T]: TrifunctorHasLifecycleTagImpl[R, T] = macro
    LifecycleTagMacro.fakeResourceTagMacroIntellijWorkaroundImpl[R]
}
