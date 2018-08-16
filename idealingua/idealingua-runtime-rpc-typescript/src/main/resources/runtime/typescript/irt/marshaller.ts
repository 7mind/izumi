import { Void } from './void';

export interface Marshaller<T> {
    Marshal<I>(data: I): T
    Unmarshal<O>(data: T): O
}

export interface JSONMarshaller extends Marshaller<string> {
}

export class JSONMarshallerImpl implements JSONMarshaller {
    private pretty: boolean;

    public constructor(pretty: boolean = false) {
        this.pretty = pretty;
    }

    public Marshal<I>(data: I): string {
        if (data instanceof Void) {
            return '{}';
        }

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
