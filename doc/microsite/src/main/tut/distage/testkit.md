Testkit
=======

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

[distage sample project](https://github.com/7mind/distage-sample) features 
[several](https://github.com/7mind/distage-sample/blob/develop/domain/users/src/test/scala/com/github/ratoshniuk/izumi/distage/sample/storages/InternalStorageTest.scala)
[examples](https://github.com/7mind/distage-sample/blob/develop/domain/users/src/test/scala/com/github/ratoshniuk/izumi/distage/sample/services/UserServiceTest.scala) of testkit usage.

High-level syntax example of `distage-testkit` for scalatest:

```scala
class DistageTestExampleBIO extends DistageBIOSpecScalatest[ZIO] {

  "distage test runner" should {
    "support bifunctor" in {
      service: MockUserRepository[ZIO[Throwable, ?]] =>
        for {
          _ <- ZIO(assert(service != null))
        } yield ()
    }
  }

}
``` 

### Basics

### Testkit and Role-Based Applications




