package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.sneaky.SneakyPlugin

class DependingPlugin extends SneakyPlugin {
  make[String].from((_: TestDep, _: TestDep3, _: TestService) => "abc")
}

object DependingPlugin extends App {
  class NestedDoublePlugin extends SneakyPlugin
  object NestedDoublePlugin extends SneakyPlugin {
    make[TestDepending]
  }
}
