# JSON Wire Format

IdeaLingua employs a simple JSON-based wire format. To interoperate, all the language translators should implement this spec.

## `data`: Data Class

`data` defined as:

```idealingua
package example

data User {
  name: str
  surname: str
  id: uuid
}
```

Should be rendered as:

```json
{
  "name": "Lorem",
  "surname": "Ipsum",
  "id": "13bee602-521b-47c2-ad81-30527f8b2398"
}
```

## `mixin`: Mixin

When sent over the wire, mixins include a fully qualified name of their implementation.

`mixin`s defined as:

```idealingua
package example

mixin IntPair {
  x: i32
  y: i32
}

data NamedIntPair {
  & IntPair
  name: str
}
```

Should be rendered as:

```json
{
  "example.IntPair#Struct": {
    "x": 256,
    "y": 512
  }
}
```

for the default implementation, and:

```json
{
  "example#NamedIntPair": {
    "x": 256,
    "y": 512,
    "name": "Vertex"
  }
}
```

for the `NamedIntPair` implementation.

## `adt`: Algebraic Data Type

`adt`'s include an unqualified name of their variant.

`adt` defined as:

```idealingua
package example

adt AB = A | Z as B

data A {
  value: i32
}

data Z {
  value: str
}
```

Should be rendered as:

```json
{
  "A": {
    "value": 1
  }
}
```

for the `A` variant, and:

```json
{
  "B": {
    "value": "abc"
  }
}
```

for the `Z as B` variant

## `id`: Identifier

Ids are rendered as strings prefixed by type name and separated by `:` symbol. Field order is preserved.

`id` defined as:

```idealingua
package example

id UserId {
  userId: uuid
  companyName: str
}
```

Should be rendered as:

```json
"UserId#837006c8-d070-4cde-a2dd-8999c186ef02:Lightbend"
```

## `alias`: Type Alias

Type Aliases should be rendered directly as their aliased types and should never impact serialization.

## `enum`: Enumeration

Enums are rendered as strings.

`enum` defined as:

```idealingua
package example

enum Gender = MALE | FEMALE
```

Should be rendered as:

```json
"FEMALE"
```

## Service

Service outputs are always wrapped into a JSON object with one field `"value"`.

For `service` defined as:

```idealingua
package example

service SayHello {
  def sayHello(): str
}
```

`sayHello` method will return a result rendered as:

```json
{ "value": "hello" }
```
