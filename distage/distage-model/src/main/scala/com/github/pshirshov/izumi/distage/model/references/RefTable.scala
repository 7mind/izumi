package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

case class RefTable(dependencies: Map[RuntimeUniverse.DIKey, Set[RuntimeUniverse.DIKey]], dependants: Map[RuntimeUniverse.DIKey, Set[RuntimeUniverse.DIKey]])
