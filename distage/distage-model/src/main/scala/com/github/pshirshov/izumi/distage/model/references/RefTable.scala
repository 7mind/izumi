package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

case class RefTable(
                     dependenciesOf: Map[RuntimeUniverse.DIKey, Set[RuntimeUniverse.DIKey]]
                     , dependsOn: Map[RuntimeUniverse.DIKey, Set[RuntimeUniverse.DIKey]]
                   )
