package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

case class RefTable(
                     dependenciesOf: Map[RuntimeDIUniverse.DIKey, Set[RuntimeDIUniverse.DIKey]]
                     , dependsOn: Map[RuntimeDIUniverse.DIKey, Set[RuntimeDIUniverse.DIKey]]
                   )
