package org.bitbucket.pshirshov.izumi.distage.model.exceptions

import org.bitbucket.pshirshov.izumi.distage.model.plan.RefTable

class ForwardRefException(message: String, val reftable: RefTable) extends DIException(message, null)


