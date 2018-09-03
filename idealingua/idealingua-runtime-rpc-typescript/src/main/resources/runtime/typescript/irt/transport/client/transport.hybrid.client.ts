
import { Logger } from '../../logger';
import { WebSocketClientTransport } from './transport.websocket.client';
import { ClientTransport, IncomingData, OutgoingData } from '../transport';
import { HTTPClientTransport } from './transport.http.client';
import { JSONMarshaller } from '../../marshaller';
import { AuthMethod } from '../auth';
import { TransportHeaders } from '../transport';

export type handlerSuccess = (service: string, method: string, payload: string) => void;
export type handlerFailure = (service: string, method: string, error: string) => void;

export class HybridClientTransportGeneric<C> implements ClientTransport {
    private _restTransport: HTTPClientTransport;
    private _wsTransport: WebSocketClientTransport<C>;
    private _authMethod: AuthMethod | undefined;
    private _headers: TransportHeaders;

    public get onSend(): handlerSuccess {
        return this._restTransport.onSend;
    }
    public set onSend(value: handlerSuccess) {
        this._restTransport.onSend = value;
        this._wsTransport.onSend = value;
    }

    public get onSuccess(): handlerSuccess {
        return this._restTransport.onSuccess;
    }
    public set onSuccess(value: handlerSuccess) {
        this._restTransport.onSuccess = value;
        this._wsTransport.onSuccess = value;
    }

    public get onFailure(): handlerFailure {
        return this._restTransport.onFailure;
    }
    public set onFailure(value: handlerFailure) {
        this._restTransport.onFailure = value;
        this._wsTransport.onFailure = value;
    }

    constructor(restEndpoint: string, wsEndpoint: string, marshaller: JSONMarshaller, logger: Logger) {
        this._headers = {};
        this._restTransport = new HTTPClientTransport(restEndpoint, marshaller, logger);
        this._wsTransport = new WebSocketClientTransport<C>(wsEndpoint, marshaller, logger);
    }

    public getAuthorization(): AuthMethod | undefined {
        return this._authMethod;
    }

    public setAuthorization(method: AuthMethod | undefined) {
        this._authMethod = method;
        this._restTransport.setAuthorization(method);
        this._wsTransport.setAuthorization(method);
    }

    public getHeaders(): TransportHeaders {
        return this._headers;
    }

    public setHeaders(headers: TransportHeaders | undefined) {
        this._headers = headers || {};
        this._restTransport.setHeaders(headers);
        this._wsTransport.setHeaders(headers);
    }

    public send(service: string, method: string, data: IncomingData): Promise<OutgoingData> {
        if (this._wsTransport.isReady()) {
            return this._wsTransport.send(service, method, data);
        }

        return this._restTransport.send(service, method, data);
    }
}

export class HybridClientTransport extends HybridClientTransportGeneric<object> {
}