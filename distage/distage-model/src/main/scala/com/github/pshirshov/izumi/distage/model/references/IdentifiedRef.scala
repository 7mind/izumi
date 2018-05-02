package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

case class IdentifiedRef(key: RuntimeDIUniverse.DIKey, value: Any)
