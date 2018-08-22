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

        const serialized = typeof data['serialize'] === 'function' ? data['serialize']() : data;
        if (this.pretty) {
            return JSON.stringify(serialized, null, 4);
        } else {
            return JSON.stringify(serialized);
        }
    }

    public Unmarshal<O>(data: string): O {
        return JSON.parse(data);
    }
}
