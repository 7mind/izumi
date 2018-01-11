package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.planning.RefTable

class MissingRefException(message: String, val missing: Set[DIKey], val reftable: RefTable) extends DIException(message, null)
