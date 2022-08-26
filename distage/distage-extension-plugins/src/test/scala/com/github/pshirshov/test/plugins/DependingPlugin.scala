package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.sneaky.SneakyPlugin
import izumi.fundamentals.platform.build.ExposedTestScope

@ExposedTestScope
class DependingPlugin extends SneakyPlugin() {
  make[String].from((_: TestDep, _: TestDep3, _: TestService) => "abc")
}

@ExposedTestScope
object DependingPlugin extends App {
  class NestedDoublePlugin extends SneakyPlugin()
  object NestedDoublePlugin extends SneakyPlugin() {
    make[TestDepending]
  }
}
