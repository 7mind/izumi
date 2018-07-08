
export interface IMarshaller<T> {
    Marshal<I>(data: I): T
    Unmarshal<O>(data: T): O
}

export interface IJSONMarshaller extends IMarshaller<string> {
}

export class JSONMarshaller implements IJSONMarshaller {
    private pretty: boolean;

    public constructor(pretty: boolean = false) {
        this.pretty = pretty;
    }

    public Marshal<I>(data: I): string {
        if (this.pretty) {
            return JSON.stringify(data, null, 4);
        } else {
            return JSON.stringify(data);
        }
    }

    public Unmarshal<O>(data: string): O {
        return JSON.parse(data);
    }
}
