
using System;
// TODO Consider moving out of here the converter attribute
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using System.Linq;
using IRT.Marshaller;

namespace IRT {
    [JsonConverter(typeof(Either_JsonNetConverter))]
    public abstract class Either<L, A> {
        public abstract Either<L, B> Map<B>(Func<A, B> f);
        public abstract Either<V, B> BiMap<V, B>(Func<A, B> g, Func<L, V> f);
        public abstract B Fold<B>(Func<A, B> whenRight, Func<L, B> whenLeft);
        public abstract L GetOrElse(L l);
        public abstract L GetLeft();
        public abstract A GetOrElse(A a);
        public abstract A GetRight();
        public abstract bool IsLeft();
        public abstract bool IsRight();
        public abstract Either<A, L> Swap();
        public abstract Either<L, A> FilterOrElse(Func<A, bool> p, L zero);
        public abstract void Match(Action<A> whenRight, Action<L> whenLeft);

        public static explicit operator L(Either<L, A> e) {
            if (!e.IsLeft()) {
                throw new InvalidCastException("Either is not in the Left state.");
            }

            return e.GetLeft();
        }

        public static explicit operator A(Either<L, A> e) {
            if (e.IsLeft()) {
                throw new InvalidCastException("Either is not in the Right state.");
            }

            return e.GetRight();
        }

        // We support both sides to have equal type, for example if both
        // success and failure hold a simple Message class. In that case
        // same operator would fail, so we move it to the generated class.
        public static implicit operator Either<L, A> (L value) {
            return new Left(value);
        }

        public static implicit operator Either<L, A> (A value) {
            return new Right(value);
        }

        public sealed class Left: Either<L, A> {
            private readonly L Value;
            public Left(L value) {
                Value = value;
            }

            public override Either<L, B> Map<B>(Func<A, B> f) {
                return new Either<L, B>.Left(Value);
            }

            public override Either<V, B> BiMap<V, B>(Func<A, B> g, Func<L, V> f) {
                return new Either<V, B>.Left(f(Value));
            }

            public override B Fold<B>(Func<A, B> whenRight, Func<L, B> whenLeft) {
                return whenLeft(Value);
            }

            public override L GetOrElse(L l) {
                return Value;
            }

            public override A GetOrElse(A a) {
                return a;
            }

            public override L GetLeft() {
                return Value;
            }

            public override A GetRight() {
                throw new InvalidCastException("Either is not in the Right state.");
            }

            public override bool IsLeft() {
                return true;
            }

            public override bool IsRight() {
                return false;
            }

            public override Either<A, L> Swap() {
                return new Either<A, L>.Right(Value);
            }

            public override Either<L, A> FilterOrElse(Func<A, bool> p, L zero) {
                return this;
            }

            public override void Match(Action<A> whenRight, Action<L> whenLeft) {
                whenLeft(Value);
            }
        }

        public sealed class Right: Either<L, A> {
            private readonly A Value;
            public Right(A value) {
                Value = value;
            }

            public override Either<L, B> Map<B>(Func<A, B> f) {
                return new Either<L, B>.Right(f(Value));
            }

            public override Either<V, B> BiMap<V, B>(Func<A, B> g, Func<L, V> f) {
                return new Either<V, B>.Right(g(Value));
            }

            public override B Fold<B>(Func<A, B> whenRight, Func<L, B> whenLeft) {
                return whenRight(Value);
            }

            public override L GetOrElse(L l) {
                return l;
            }

            public override A GetOrElse(A a) {
                return Value;
            }

            public override L GetLeft() {
                throw new InvalidCastException("Either is not in the Left state.");
            }

            public override A GetRight() {
                return Value;
            }

            public override bool IsLeft() {
                return false;
            }

            public override bool IsRight() {
                return true;
            }

            public override Either<A, L> Swap() {
                return new Either<A, L>.Left(Value);
            }

            public override Either<L, A> FilterOrElse(Func<A, bool> p, L zero) {
                if (p(Value)) {
                    return this;
                }

                return new Left(zero);
            }

            public override void Match(Action<A> whenRight, Action<L> whenLeft) {
                whenRight(Value);
            }
        }
    }

    // TODO Consider moving out of here to the marshaller itself
    // This implementation seems to trigger some issues with Json.net, as it doesn't know how to
    // deserialize a generic type like this...
//     public class Either_JsonNetConverter<L, R>: JsonNetConverter<Either<L, R>> {
//         public override void WriteJson(JsonWriter writer, Either<L, R> al, JsonSerializer serializer) {
//             writer.WriteStartObject();

//             if (al.IsLeft()) {
//                 writer.WritePropertyName("Failure");
//                 var l = al.GetLeft();
//                 if (typeof(L).IsInterface) {
//                     // Serializing polymorphic type
//                     writer.WriteStartObject();
//                     writer.WritePropertyName((l as IRTTI).GetFullClassName());
//                     serializer.Serialize(writer, l);
//                     writer.WriteEndObject();
//                 } else {
//                     serializer.Serialize(writer, l);
//                 }
//             } else {
//                 writer.WritePropertyName("Success");
//                 var r = al.GetRight();
//                 if (typeof(R).IsInterface) {
//                     // Serializing polymorphic type
//                     writer.WriteStartObject();
//                     writer.WritePropertyName((r as IRTTI).GetFullClassName());
//                     serializer.Serialize(writer, r);
//                     writer.WriteEndObject();
//                 } else {
//                     serializer.Serialize(writer, r);
//                 }
//             }

//             writer.WriteEndObject();
//         }

//         public override Either<L, R> ReadJson(JsonReader reader, System.Type objectType, Either<L, R> existingValue, bool hasExistingValue, JsonSerializer serializer) {
//             var json = JObject.Load(reader);
//             var kv = json.Properties().First();
//             switch (kv.Name) {
//                 case "Success": {
//                     var v = serializer.Deserialize<R>(kv.Value.CreateReader());
//                     return new Either<L, R>.Right(v);
//                 }

//                 case "Failure": {
//                     var v = serializer.Deserialize<L>(kv.Value.CreateReader());
//                     return new Either<L, R>.Left(v);
//                 }

//                 default:
//                     throw new System.Exception("Unknown either Either<L, R> type: " + kv.Name);
//             }
//         }
//     }
    
    public class Either_JsonNetConverter : JsonConverter
    {
        public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer)
        {
            Type valueType = value.GetType();
            Type[] genArgs = valueType.GetGenericArguments();
            
            writer.WriteStartObject();

            if ((bool)valueType.GetMethod("IsLeft").Invoke(value, new object[0])) {
                writer.WritePropertyName("Failure");
                var l = valueType.GetMethod("GetLeft").Invoke(value, new object[0]);
                if (genArgs[0].IsInterface) {
                    // Serializing polymorphic type
                    writer.WriteStartObject();
                    writer.WritePropertyName((l as IRTTI).GetFullClassName());
                    serializer.Serialize(writer, l);
                    writer.WriteEndObject();
                } else {
                    serializer.Serialize(writer, l);
                }
            } else {
                writer.WritePropertyName("Success");
                var r = valueType.GetMethod("GetRight").Invoke(value, new object[0]);
                if (genArgs[1].IsInterface) {
                    // Serializing polymorphic type
                    writer.WriteStartObject();
                    writer.WritePropertyName((r as IRTTI).GetFullClassName());
                    serializer.Serialize(writer, r);
                    writer.WriteEndObject();
                } else {
                    serializer.Serialize(writer, r);
                }
            }

            writer.WriteEndObject();
        }

        public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer)
        {
            Type[] genArgs = objectType.GetGenericArguments();
            
            var json = JObject.Load(reader);
            var kv = json.Properties().First();
            switch (kv.Name) {
                case "Success": {
                    var v = serializer.Deserialize(kv.Value.CreateReader(), genArgs[1]);
                    var rightType = typeof(Either<,>.Right).MakeGenericType(genArgs);

                    return Activator.CreateInstance(rightType, v);
                }

                case "Failure": {
                    var v = serializer.Deserialize(kv.Value.CreateReader(), genArgs[0]);
                    var leftType = typeof(Either<,>.Left).MakeGenericType(genArgs);

                    return Activator.CreateInstance(leftType, v);
                }

                default:
                    throw new System.Exception("Unknown either Either<L, R> type: " + kv.Name);
            }
        }

        public override bool CanConvert(Type objectType)
        {
            return typeof(Either<,>).IsAssignableFrom(objectType);
        }
    }
}
