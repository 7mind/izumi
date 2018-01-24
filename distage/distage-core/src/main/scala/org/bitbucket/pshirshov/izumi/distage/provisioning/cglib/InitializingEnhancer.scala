package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import net.sf.cglib.asm.$ClassVisitor
import net.sf.cglib.core.TypeUtils
import net.sf.cglib.proxy.Enhancer
import org.bitbucket.pshirshov.izumi.distage.provisioning.cglib.CglibTools.IZ_INIT_METHOD_NAME

class InitializingEnhancer(runtimeClass: Class[_]) extends Enhancer {
  override def generateClass(v: $ClassVisitor): Unit = {
    super.generateClass(v)


    val internalName = TypeUtils.getType(runtimeClass.getTypeName).getInternalName
    val mv = v.visitMethod(1, IZ_INIT_METHOD_NAME, "()Z", null, null)
    mv.visitCode()
    mv.visitVarInsn(25, 0) // ALOAD
    mv.visitVarInsn(25, 0)
    mv.visitMethodInsn(184 // invoke static
      , internalName
      , "$init$"
      , s"(L$internalName;)V"
      , false
    )
    mv.visitInsn(4) // ICONST_1
    mv.visitInsn(172)
    mv.visitMaxs(2, 1)
    mv.visitEnd()
  }
}
