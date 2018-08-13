
import { Logger } from './logger';
import { WebSocketClientTransport } from './transport.websocket';
import { ClientTransport, ServiceClientInData, ServiceClientOutData } from './transport';
import { HTTPClientTransport } from './transport.http';
import { JSONMarshaller } from './marshaller';
import { AuthMethod } from './auth';

export class WebHybridClientTransport implements ClientTransport {
    private _restTransport: HTTPClientTransport;
    private _wsTransport: WebSocketClientTransport;

    constructor(restEndpoint: string, wsEndpoint: string, marshaller: JSONMarshaller, logger: Logger) {
        this._restTransport = new HTTPClientTransport(restEndpoint, marshaller, logger);
        this._wsTransport = new WebSocketClientTransport(wsEndpoint, marshaller, logger);
    }

    public setAuthorization(method: AuthMethod) {
        this._restTransport.setAuthorization(method);
        this._wsTransport.setAuthorization(method);
    }

    public send(service: string, method: string, data: ServiceClientInData): Promise<ServiceClientOutData> {
        if (this._wsTransport.isReady()) {
            return this._wsTransport.send(service, method, data);
        }

        return this._restTransport.send(service, method, data);
    }
}