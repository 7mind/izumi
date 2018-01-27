package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.provisioning.ProvisionerHook

class ProvisionerHookDefaultImpl extends ProvisionerHook {

}

object ProvisionerHookDefaultImpl {
  final val instance = new ProvisionerHookDefaultImpl()
}