
namespace IRT.Marshaller {
    public interface IMarshaller<T> {
        T Marshal<I>(I data);
        O Unmarshal<O>(T data);
    }

    public interface IJsonMarshaller: IMarshaller<string> {
    }

    public interface IBinaryMarshaller: IMarshaller<byte[]> {
    }
}