
import { WSClient, WSClientState } from './wsclient';
import { JSONMarshaller } from './marshaller';
import { Logger, LogLevel } from './logger';
import { ClientTransport, ServiceClientInData, ServiceClientOutData } from './transport';
import { AuthMethod, Authorization } from './auth';


export interface WebSocketRequestMessage {
    service?: string
    method?: string
    id: string
    data?: any
    authorization?: string
}

export interface WebSocketResponseMessage {
    ref: string;
    data: any;
    error?: string;
}

interface DeferredPromise {
    promise?: Promise<ServiceClientOutData>
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

export class WebSocketClientTransport implements ClientTransport {
    private _supported: boolean;
    private _wsc: WSClient;
    private _marshaller: JSONMarshaller;
    private _logger: Logger;
    private _requests: {[key: string]: DeferredPromise};
    private _auth?: Authorization;
    private _authID?: string;
    private _authenticated: boolean;

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
        this._authenticated = true;
    }

    public isReady(checkAuth: boolean = true): boolean {
        if (!this._supported) {
            return false;
        }

        if (checkAuth && !this._authenticated) {
            return false;
        }

        return this._wsc.state === WSClientState.Connected;
    }

    public send(service: string, method: string, data: ServiceClientInData): Promise<ServiceClientOutData> {
        const request: WebSocketRequestMessage = {
            service,
            method,
            id: RandomMessageID(),
            data: data.serialize(),
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
            timeout: setTimeout(
                () => {
                    const error = 'timed out request to ' + service + '/' + method;
                    if (onFailure) {
                        onFailure(service, method, error);
                    }
                    record.reject(new Error(error));
                    delete this._requests[request.id];
                },
                60000,
            ),
        };
        record.promise = new Promise<ServiceClientOutData>((resolve, reject) => {
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

    private checkAuth() {
        if (!this.isReady(false)) {
            return;
        }

        if (this._auth) {
            this.sendAuth(this._auth.toValue());
        }

        this._authenticated = true;
    }

    private sendAuth(authorization: string) {
        this._authID = RandomMessageID('auth-');
        const msg: WebSocketRequestMessage = {
            authorization,
            id: this._authID,
        };
        const serialized = this._marshaller.Marshal(msg);
        this._logger.logf(
            LogLevel.Trace, '====================================================\nAuthenticating...\n',
            JSON.stringify(msg, null, '    '));
        this._wsc.send(serialized);
    }

    public setAuthorization(method: AuthMethod) {
        if (!method) {
            this._auth = undefined;
        }

        this._auth = new Authorization();
        this._auth.method = method;
        this.checkAuth();
    }

    private onConnecting() {
        this._authenticated = false;
    }

    private onClose() {
        this._authenticated = false;
    }

    private onMessage(data: any) {
        const deserialized = this._marshaller.Unmarshal<WebSocketResponseMessage>(data);
        const ref = deserialized.ref;
        this._logger.logf(LogLevel.Trace, '====================================================\nIncoming message:\n', data);

        if (!(ref in this._requests)) {
            if (ref === this._authID) {
                if (deserialized.error) {
                    this._logger.logf(LogLevel.Error, 'Authentication failed ' + deserialized.error);
                }
                this._authenticated = true;
                return;
            }
            console.warn('Unknown reference ID came back: ' + ref, deserialized);
            return;
        }

        const record = this._requests[ref];
        delete this._requests[ref];
        clearTimeout(record.timeout);

        if (deserialized.error) {
            if (this.onFailure) {
                this.onFailure(record.service, record.method, deserialized.error);
            }
            record.reject(deserialized.error);
        } else {
            if (this.onSuccess) {
                this.onSuccess(record.service, record.method, deserialized.data);
            }
            record.resolve(deserialized.data);
        }
    }

    private onOpen() {
        this.checkAuth();
    }
}
