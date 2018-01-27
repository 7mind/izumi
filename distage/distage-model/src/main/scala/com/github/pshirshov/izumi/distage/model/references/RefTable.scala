package com.github.pshirshov.izumi.distage.model.references

case class RefTable(dependencies: Map[DIKey, Set[DIKey]], dependants: Map[DIKey, Set[DIKey]])
