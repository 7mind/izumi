package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.plan.RefTable

class ForwardRefException(message: String, val reftable: RefTable) extends DIException(message, null)


