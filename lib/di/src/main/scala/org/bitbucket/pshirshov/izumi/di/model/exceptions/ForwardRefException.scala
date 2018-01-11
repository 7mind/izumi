package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.planning.FdwRefTable

class ForwardRefException(message: String, val reftable: FdwRefTable) extends DIException(message, null)
