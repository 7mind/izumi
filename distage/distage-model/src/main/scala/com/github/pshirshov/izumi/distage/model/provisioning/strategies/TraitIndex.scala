package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.plan.Association

case class TraitIndex(
                       methods: Map[Method, Association.Method]
                       , getters: Map[String, TraitField]
                       , setters: Map[String, TraitField]
                     )
