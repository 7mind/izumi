# Idealingua DML/IDL

[Code generation examples](cogen.md)


## Embedded data types

Notes


1. When it's impossible to represent a numeric type with target language we use minimal numeric type with bigger range
2. When it's not possible to represent time type or UUID with target language we use string representation
  

### Scalar types

Type name   | Aliases            | Explanation                                 | Scala mapping                |
------------| ------------------ | ------------------------------------------- | -----------------------------|
`bool`      | `boolean`          | Boolean                                     | `Boolean`                    |
`str`       | `string`           | String                                      | `String`                     |
`i08`       | `byte`, `int8`     | 8-bit integer                               | `Byte`                       |
`i16`       | `short`, `int16`   | 16-bit integer                              | `Short`                      |
`i32`       | `int`, `int32`     | 32-bit integer                              | `Int`                        |
`i64`       | `long`, `int64`    | 64-bit integer                              | `Long`                       |
`flt`       | `float`            | Floating point                              | `Float`                      |
`dbl`       | `double`           | Double accuracy floating point              | `Double`                     |
`uid`       | `uuid`             | UUID                                        | `java.util.UUID`             |
`tsz`       | `dtl`, `datetimel` | Timestamp with timezone                     | `java.time.ZonedDateTime`    |
`tsl`       | `dtz`, `datetimez` | Local timestamp                             | `java.time.LocalDateTime`    |
`time`      | `time`             | Time                                        | `java.time.LocalTime`        |
`date`      | `date`             | Date                                        | `java.time.LocalDate`        |

### Generics

Type name    | Explanation                                 | Scala mapping  | 
------------ | ------------------------------------------- | -------------- |
`list[T]`    | List                                        | `List`         |
`map[K, V]`  | Map (only scalar keys supported)            | `Map`          |
`opt[T]`     | Optional value                              | `Set`          |
`set[T]`     | Set (no guarantees for traversal ordering)  | `scala.Option` |
