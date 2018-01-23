package org.bitbucket.pshirshov.izumi.distage.model.plan

import org.bitbucket.pshirshov.izumi.distage.model.DIKey

case class RefTable(dependencies: Map[DIKey, Set[DIKey]], dependants: Map[DIKey, Set[DIKey]])
