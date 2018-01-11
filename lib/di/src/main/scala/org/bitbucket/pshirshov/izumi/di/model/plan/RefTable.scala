package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.model.DIKey

case class RefTable(dependencies: Map[DIKey, Set[DIKey]], dependants: Map[DIKey, Set[DIKey]])
