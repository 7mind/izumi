package izumi.thirdparty.internal.boopickle

trait TuplePicklers extends PicklerHelper {

  implicit def Tuple1Pickler[T1: P] = new P[Tuple1[T1]] {
    override def pickle(x: Tuple1[T1])(implicit state: PickleState): Unit = { write[T1](x._1) }
    override def unpickle(implicit state: UnpickleState)                  = Tuple1[T1](read[T1])
  }

  implicit def Tuple2Pickler[T1: P, T2: P] = new P[(T1, T2)] {
    override def pickle(x: (T1, T2))(implicit state: PickleState): Unit = { write[T1](x._1); write[T2](x._2) }
    override def unpickle(implicit state: UnpickleState)                = (read[T1], read[T2])
  }

  implicit def Tuple3Pickler[T1: P, T2: P, T3: P] = new P[(T1, T2, T3)] {
    override def pickle(x: (T1, T2, T3))(implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3)
    }
    override def unpickle(implicit state: UnpickleState) = (read[T1], read[T2], read[T3])
  }

  implicit def Tuple4Pickler[T1: P, T2: P, T3: P, T4: P] = new P[(T1, T2, T3, T4)] {
    override def pickle(x: (T1, T2, T3, T4))(implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4)
    }
    override def unpickle(implicit state: UnpickleState) = (read[T1], read[T2], read[T3], read[T4])
  }

  implicit def Tuple5Pickler[T1: P, T2: P, T3: P, T4: P, T5: P] = new P[(T1, T2, T3, T4, T5)] {
    override def pickle(x: (T1, T2, T3, T4, T5))(implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5)
    }
    override def unpickle(implicit state: UnpickleState) = (read[T1], read[T2], read[T3], read[T4], read[T5])
  }

  implicit def Tuple6Pickler[T1: P, T2: P, T3: P, T4: P, T5: P, T6: P] = new P[(T1, T2, T3, T4, T5, T6)] {
    override def pickle(x: (T1, T2, T3, T4, T5, T6))(implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6)
    }
    override def unpickle(implicit state: UnpickleState) = (read[T1], read[T2], read[T3], read[T4], read[T5], read[T6])
  }

  implicit def Tuple7Pickler[T1: P, T2: P, T3: P, T4: P, T5: P, T6: P, T7: P] = new P[(T1, T2, T3, T4, T5, T6, T7)] {
    override def pickle(x: (T1, T2, T3, T4, T5, T6, T7))(implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7)
    }
    override def unpickle(implicit state: UnpickleState) =
      (read[T1], read[T2], read[T3], read[T4], read[T5], read[T6], read[T7])
  }

  implicit def Tuple8Pickler[T1: P, T2: P, T3: P, T4: P, T5: P, T6: P, T7: P, T8: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8))(implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1], read[T2], read[T3], read[T4], read[T5], read[T6], read[T7], read[T8])
    }

  implicit def Tuple9Pickler[T1: P, T2: P, T3: P, T4: P, T5: P, T6: P, T7: P, T8: P, T9: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9))(implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1], read[T2], read[T3], read[T4], read[T5], read[T6], read[T7], read[T8], read[T9])
    }

  implicit def Tuple10Pickler[T1: P, T2: P, T3: P, T4: P, T5: P, T6: P, T7: P, T8: P, T9: P, T10: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10))(implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1], read[T2], read[T3], read[T4], read[T5], read[T6], read[T7], read[T8], read[T9], read[T10])
    }

  implicit def Tuple11Pickler[T1: P, T2: P, T3: P, T4: P, T5: P, T6: P, T7: P, T8: P, T9: P, T10: P, T11: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11))(implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1], read[T2], read[T3], read[T4], read[T5], read[T6], read[T7], read[T8], read[T9], read[T10], read[T11])
    }

  implicit def Tuple12Pickler[T1: P, T2: P, T3: P, T4: P, T5: P, T6: P, T7: P, T8: P, T9: P, T10: P, T11: P, T12: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12))(implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12])
    }

  implicit def Tuple13Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13))(implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12],
         read[T13])
    }

  implicit def Tuple14Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14))(
          implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
        write[T14](x._14)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12],
         read[T13],
         read[T14])
    }

  implicit def Tuple15Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P] = new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)] {
    override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15))(
        implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
      write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
      write[T14](x._14);
      write[T15](x._15)
    }
    override def unpickle(implicit state: UnpickleState) =
      (read[T1],
       read[T2],
       read[T3],
       read[T4],
       read[T5],
       read[T6],
       read[T7],
       read[T8],
       read[T9],
       read[T10],
       read[T11],
       read[T12],
       read[T13],
       read[T14],
       read[T15])
  }

  implicit def Tuple16Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P,
                              T16: P] = new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)] {
    override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16))(
        implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
      write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
      write[T14](x._14);
      write[T15](x._15); write[T16](x._16)
    }
    override def unpickle(implicit state: UnpickleState) =
      (read[T1],
       read[T2],
       read[T3],
       read[T4],
       read[T5],
       read[T6],
       read[T7],
       read[T8],
       read[T9],
       read[T10],
       read[T11],
       read[T12],
       read[T13],
       read[T14],
       read[T15],
       read[T16])
  }

  implicit def Tuple17Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P,
                              T16: P,
                              T17: P] = new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)] {
    override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17))(
        implicit state: PickleState): Unit = {
      write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
      write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
      write[T14](x._14);
      write[T15](x._15); write[T16](x._16); write[T17](x._17)
    }
    override def unpickle(implicit state: UnpickleState) =
      (read[T1],
       read[T2],
       read[T3],
       read[T4],
       read[T5],
       read[T6],
       read[T7],
       read[T8],
       read[T9],
       read[T10],
       read[T11],
       read[T12],
       read[T13],
       read[T14],
       read[T15],
       read[T16],
       read[T17])
  }

  implicit def Tuple18Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P,
                              T16: P,
                              T17: P,
                              T18: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18))(
          implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
        write[T14](x._14);
        write[T15](x._15); write[T16](x._16); write[T17](x._17); write[T18](x._18)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12],
         read[T13],
         read[T14],
         read[T15],
         read[T16],
         read[T17],
         read[T18])
    }

  implicit def Tuple19Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P,
                              T16: P,
                              T17: P,
                              T18: P,
                              T19: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19))(
          implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
        write[T14](x._14);
        write[T15](x._15); write[T16](x._16); write[T17](x._17); write[T18](x._18); write[T19](x._19)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12],
         read[T13],
         read[T14],
         read[T15],
         read[T16],
         read[T17],
         read[T18],
         read[T19])
    }

  implicit def Tuple20Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P,
                              T16: P,
                              T17: P,
                              T18: P,
                              T19: P,
                              T20: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)] {
      override def pickle(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20))(
          implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
        write[T14](x._14);
        write[T15](x._15); write[T16](x._16); write[T17](x._17); write[T18](x._18); write[T19](x._19); write[T20](x._20)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12],
         read[T13],
         read[T14],
         read[T15],
         read[T16],
         read[T17],
         read[T18],
         read[T19],
         read[T20])
    }

  implicit def Tuple21Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P,
                              T16: P,
                              T17: P,
                              T18: P,
                              T19: P,
                              T20: P,
                              T21: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)] {
      override def pickle(
          x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21))(
          implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
        write[T14](x._14);
        write[T15](x._15); write[T16](x._16); write[T17](x._17); write[T18](x._18); write[T19](x._19); write[T20](x._20);
        write[T21](x._21)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12],
         read[T13],
         read[T14],
         read[T15],
         read[T16],
         read[T17],
         read[T18],
         read[T19],
         read[T20],
         read[T21])
    }

  implicit def Tuple22Pickler[T1: P,
                              T2: P,
                              T3: P,
                              T4: P,
                              T5: P,
                              T6: P,
                              T7: P,
                              T8: P,
                              T9: P,
                              T10: P,
                              T11: P,
                              T12: P,
                              T13: P,
                              T14: P,
                              T15: P,
                              T16: P,
                              T17: P,
                              T18: P,
                              T19: P,
                              T20: P,
                              T21: P,
                              T22: P] =
    new P[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)] {
      override def pickle(
          x: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22))(
          implicit state: PickleState): Unit = {
        write[T1](x._1); write[T2](x._2); write[T3](x._3); write[T4](x._4); write[T5](x._5); write[T6](x._6); write[T7](x._7);
        write[T8](x._8); write[T9](x._9); write[T10](x._10); write[T11](x._11); write[T12](x._12); write[T13](x._13);
        write[T14](x._14);
        write[T15](x._15); write[T16](x._16); write[T17](x._17); write[T18](x._18); write[T19](x._19); write[T20](x._20);
        write[T21](x._21);
        write[T22](x._22)
      }
      override def unpickle(implicit state: UnpickleState) =
        (read[T1],
         read[T2],
         read[T3],
         read[T4],
         read[T5],
         read[T6],
         read[T7],
         read[T8],
         read[T9],
         read[T10],
         read[T11],
         read[T12],
         read[T13],
         read[T14],
         read[T15],
         read[T16],
         read[T17],
         read[T18],
         read[T19],
         read[T20],
         read[T21],
         read[T22])
    }
}
