package com.github.pshirshov.izumi.distage.model.references

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

case class IdentifiedRef(key: RuntimeUniverse.DIKey, value: Any)
