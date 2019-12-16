package izumi.distage.model.exceptions

import izumi.distage.model.provisioning.NewObjectOp

class UnexpectedProvisionResultException(message: String, val results: Seq[NewObjectOp]) extends DIException(message)
