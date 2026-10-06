package izumi.distage.testkit.runner

import scala.scalanative.reflect.annotation.EnableReflectiveInstantiation

// The annotation retains concrete descendants for target-side suite construction.
@EnableReflectiveInstantiation
trait TestSuitePlatform
