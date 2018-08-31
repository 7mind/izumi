
import { WSClient, WSClientState } from '../wsclient';
import { JSONMarshaller } from '../../marshaller';
import { Logger, LogLevel } from '../../logger';
import { ClientSocketTransport, IncomingData, OutgoingData } from '../transport';
import { AuthMethod, Authorization } from '../auth';
import {
    WebSocketRequestMessage,
    WebSocketResponseMessage,
    WebSocketMessageKind,
    WebSocketMessageBase,
    WebSocketFailureMessage
} from '../transport.websocket';
import { TransportHeaders } from '../transport';
import { Dispatcher, ServiceDispatcher } from '../../dispatcher';

interface DeferredPromise {
    promise?: Promise<OutgoingData>
    resolve?: any
    reject?: any
    timeout: number
    service: string
    method: string
}

export function RandomMessageID(prefix: string = ''): string {
    return prefix + 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

const headersUpdatedPrefix = 'headersupdate-';

export class WebSocketClientTransport<C> implements ClientSocketTransport<C, string> {
    private _supported: boolean;
    private _wsc: WSClient;
    private _marshaller: JSONMarshaller;
    private _logger: Logger;
    private _requests: {[key: string]: DeferredPromise};
    private _auth?: Authorization;
    private _headersUpdateID?: string;
    private _headersUpdated: boolean;
    private _headers: TransportHeaders;
    private _dispatcher: Dispatcher<C, string>;
    private _context: C;

    public onSend: (service: string, method: string, payload: string) => void;
    public onSuccess: (service: string, method: string, payload: string) => void;
    public onFailure: (service: string, method: string,  error: string) => void;

    constructor(endpoint: string, marshaller: JSONMarshaller, logger: Logger, protocols: string | string[] = []) {
        this._supported = !!window['WebSocket'];
        if (!this._supported) {
            return;
        }

        this._logger = logger;
        this._marshaller = marshaller;
        this._wsc = new WSClient();
        this._wsc.open(endpoint, protocols);
        this.onOpen = this.onOpen.bind(this);
        this.onMessage = this.onMessage.bind(this);
        this.onClose = this.onClose.bind(this);
        this.onConnecting = this.onConnecting.bind(this);
        this._requests = {};
        this._wsc.onDisconnect = this.onClose;
        this._wsc.onMessage = this.onMessage;
        this._wsc.onConnect = this.onOpen;
        this._wsc.onConnecting = this.onConnecting;
        this._headersUpdated = true;
        this._dispatcher = new Dispatcher<C, string>();
    }

    public isReady(checkHeaders: boolean = true): boolean {
        if (!this._supported) {
            return false;
        }

        if (checkHeaders && !this._headersUpdated) {
            return false;
        }

        return this._wsc.state === WSClientState.Connected;
    }

    public send(service: string, method: string, data: IncomingData): Promise<OutgoingData> {
        const request: WebSocketRequestMessage = {
            service,
            method,
            kind: WebSocketMessageKind.RpcRequest,
            id: RandomMessageID(),
            data: data.serialize()
        };
        // if (Object.keys(request.data).length === 0) {
        //   request.data = undefined;
        // }

        this._logger.logf(LogLevel.Trace, '====================================================\nOutgoing message:\n', request);
        const serialized = this._marshaller.Marshal(request);
        const onFailure = this.onFailure;
        const record: DeferredPromise = {
            service,
            method,
            timeout: window.setTimeout(
                () => {
                    const error = 'timed out request to ' + service + '/' + method;
                    if (onFailure) {
                        onFailure(service, method, error);
                    }
                    record.reject(new Error(error));
                    delete this._requests[request.id];
                },
                60000
            )
        };
        record.promise = new Promise<OutgoingData>((resolve, reject) => {
            record.reject = reject;
            record.resolve = resolve;
        });
        this._requests[request.id] = record;
        this._wsc.send(serialized);
        if (this.onSend) {
            this.onSend(service, method, serialized);
        }
        return record.promise;
    }

    private checkHeaders() {
        if (!this.isReady(false)) {
            return;
        }

        if (this._auth) {
            this.sendHeaders();
            return;
        }

        this._headersUpdated = true;
    }

    private sendHeaders() {
        this._headersUpdateID = RandomMessageID(headersUpdatedPrefix);
        const headers = {
            ...this._headers
        };
        if (this._auth) {
            headers['Authorization'] = this._auth.toValue();
        }

        const msg: WebSocketRequestMessage = {
            headers,
            kind: WebSocketMessageKind.RpcRequest,
            id: this._headersUpdateID,
        };
        const serialized = this._marshaller.Marshal(msg);
        this._logger.logf(
            LogLevel.Trace, '====================================================\Updating headers...\n',
            JSON.stringify(msg, null, '    '));
        this._wsc.send(serialized);
    }

    public getHeaders(): TransportHeaders {
        return this._headers;
    }

    public setHeaders(headers: TransportHeaders | undefined) {
        this._headers = headers || {};
    }

    public getAuthorization(): AuthMethod | undefined {
        return this._auth ? this._auth.method : undefined;
    }

    public setAuthorization(method: AuthMethod | undefined) {
        if (!method) {
            this._auth = undefined;
        }

        this._auth = new Authorization();
        this._auth.method = method;
        this.checkHeaders();
    }

    private onConnecting() {
        this._headersUpdated = false;
    }

    private onClose() {
        this._headersUpdated = false;
    }

    private onHeadersUpdated(res: WebSocketResponseMessage): boolean {
        if (res.ref === this._headersUpdateID) {
            if (res.kind === WebSocketMessageKind.RpcFailure) {
                this._logger.logf(LogLevel.Error, `Headers update failed. Ref: ${res.ref}`, res.data);
            }
            this._headersUpdated = true;
            return true;
        }

        return false;
    }

    private onRpcResponseMessage(res: WebSocketResponseMessage) {
        if (!(res.ref in this._requests)) {
            if (!this.onHeadersUpdated(res)) {
                if (res.ref.indexOf(headersUpdatedPrefix) === 0) {
                    this._logger.logf(LogLevel.Debug, 'Outdated headers update ID came back: ' + res.ref, res);
                } else {
                    this._logger.logf(LogLevel.Warning, 'Unknown reference ID came back: ' + res.ref, res);
                }
            }
            return;
        }

        const record = this._requests[res.ref];
        delete this._requests[res.ref];
        clearTimeout(record.timeout);

        if (res.kind === WebSocketMessageKind.RpcFailure) {
            if (this.onFailure) {
                this.onFailure(record.service, record.method, res.data);
            }
            record.reject(res.data);
        } else {
            if (this.onSuccess) {
                this.onSuccess(record.service, record.method, res.data);
            }
            record.resolve(res.data);
        }
    }

    private onFailureMessage(res: WebSocketFailureMessage) {
        this._logger.logf(LogLevel.Error, `Server was unable to process a request. Error: ${res.cause}`, res.data);
    }

    private onBuzzerRequestMessage(req: WebSocketRequestMessage) {
        this._logger.logf(LogLevel.Debug, 'Incoming buzzer call:', req);

        const res: WebSocketResponseMessage = {
            kind: WebSocketMessageKind.BuzzerResponse,
            ref: req.id,
            data: undefined,
        };
        this._dispatcher.dispatch(this._context, req.service, req.method, req.data)
            .then((data: string) => {
                res.data = data;
                this._wsc.send(res)
            })
            .catch((err: string) => {
                res.kind = WebSocketMessageKind.BuzzerFailure;
                res.data = err;
                this._wsc.send(res);
            });
    }

    private onStreamS2CMessage(res: WebSocketMessageBase) {
        this._logger.logf(LogLevel.Warning, 'Incoming S2C stream, not supported!', res);
    }

    private onMessage(data: any) {
        const deserialized = this._marshaller.Unmarshal<WebSocketMessageBase>(data);
        this._logger.logf(LogLevel.Trace, '====================================================\nIncoming message:\n', data);

        switch (deserialized.kind) {
            case WebSocketMessageKind.RpcResponse:
                this.onRpcResponseMessage(deserialized as WebSocketResponseMessage);
                break;
            case WebSocketMessageKind.RpcFailure:
                this.onRpcResponseMessage(deserialized as WebSocketResponseMessage);
                break;
            case WebSocketMessageKind.BuzzerRequest:
                this.onBuzzerRequestMessage(deserialized as WebSocketRequestMessage);
                break;
            case WebSocketMessageKind.Failure:
                this.onFailureMessage(deserialized as WebSocketFailureMessage);
                break;
            case WebSocketMessageKind.StreamS2C:
                this.onStreamS2CMessage(deserialized);
                break;
            default:
                this._logger.logf(LogLevel.Error, 'Invalid message received, kind is unknown or not supported.', data);
        }
    }

    private onOpen() {
        this.checkHeaders();
    }

    public registerBuzzer(buzzer: ServiceDispatcher<C, string>): boolean {
        return this._dispatcher.register(buzzer);
    }

    public unregisterBuzzer(id: string): boolean {
        return this._dispatcher.unregister(id);
    }

    public setContext(context: C): void {
        this._context = context;
    }

    public getContext(): C {
        return this._context;
    }
}
