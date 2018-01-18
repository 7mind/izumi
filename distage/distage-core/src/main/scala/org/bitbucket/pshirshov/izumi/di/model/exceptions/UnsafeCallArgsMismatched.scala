package org.bitbucket.pshirshov.izumi.di.model.exceptions

import org.bitbucket.pshirshov.izumi.di.TypeFull

class UnsafeCallArgsMismatched(message: String, val expected: Seq[TypeFull], val actual: Seq[Any]) extends DIException(message, null)
