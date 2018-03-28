# Idealingua DML/IDL

[Code generation examples](cogen.md)


## Embedded data types

Notes


1. When it's impossible to represent a numeric type with target language we use minimal numeric type with bigger range
2. When it's not possible to represent time type or UUID with target language we use string representation
  

### Scalar types

Type name    | Explanation                                 | Scala mapping                |
------------ | ------------------------------------------- | -----------------------------|
`bool`       | Boolean                                     | `Boolean`                    |
`str`        | String                                      | `String`                     |
`i08`        | 8-bit integer                               | `Byte`                       |
`i16`        | 16-bit integer                              | `Short`                      |
`i32`        | 32-bit integer                              | `Int`                        |
`i64`        | 64-bit integer                              | `Long`                       |
`flt`        | Floating point                              | `Float`                      |
`dbl`        | Double accuracy floating point              | `Double`                     |
`uid`        | UUID                                        | `java.util.UUID`             |
`tsz`        | Timestamp with timezone                     | `java.time.ZonedDateTime`    |
`tsl`        | Local timestamp                             | `java.time.LocalDateTime`    |
`time`       | Time                                        | `java.time.LocalTime`        |
`date`       | Date                                        | `java.time.LocalDate`        |

### Generics

Type name    | Explanation                                 | Scala mapping  | 
------------ | ------------------------------------------- | -------------- |
`list[T]`    | List                                        | `List`         |
`map[K, V]`  | Map (only scalar keys supported)            | `Map`          |
`opt[T]`     | Optional value                              | `Set`          |
`set[T]`     | Set (no guarantees for traversal ordering)  | `scala.Option` |
