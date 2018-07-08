
import { Logger, DummyLogger, LogLevel } from './logger';
import { IJSONMarshaller } from './marshaller';

export interface IRTServiceClientInData {
    serialize(): any;
}

export type IRTServiceClientOutData = any;

export interface IRTClientTransport {
    send(service: string, method: string, data: IRTServiceClientInData): Promise<IRTServiceClientOutData>
    subscribe(packageClass: string, callback: (data: any) => void): void
    unsubscribe(packageClass: string, callback: (data: any) => void): void
}

export class IRTHTTPClientTransport implements IRTClientTransport {
    public endpoint: string;
    private authorization: string;
    private timeout: number;
    private logger: Logger;
    private marshaller: IJSONMarshaller;

    constructor(endpoint: string, marshaller: IJSONMarshaller, logger: Logger = undefined) {
        this.setEndpoint(endpoint);
        this.timeout = 60 * 1000;
        this.logger = logger;
        if (!this.logger) {
            this.logger = new DummyLogger();
        }
        this.marshaller = marshaller;
    }

    private get isReady(): boolean {
        if (typeof this.endpoint == 'undefined') {
            return false;
        }

        return true;
    }

    private doRequest(url: string,
                      payload: string,
                      successCallback: (content: string) => void,
                      failureCallback: (error: string) => void) {

        var req = new XMLHttpRequest();
        req.onreadystatechange = function() {
            if (req.readyState === 4) {
                if (req.status === 200) {
                    successCallback(req.responseText);
                } else {
                    failureCallback(req.responseText)
                }
            }
        };

        if (payload) {
            req.open('POST', url, true);
            this.logger.logf(LogLevel.Debug, 'Header: Content-type: application/json');
            req.setRequestHeader('Content-type', 'application/json');
        } else {
            req.open('GET', url, true);
        }

        if (this.authorization) {
            req.setRequestHeader('Authorization', 'Bearer ' + this.authorization);
            this.logger.logf(LogLevel.Debug, 'Header: Authorization: Bearer ' + this.authorization);
        }

        req.timeout = this.timeout;
        req.send(payload);
    }

    private sanitizeEndpoint(endpoint: string): string {
        if (!endpoint || endpoint.length === 0) {
            return endpoint;
        }

        if (endpoint.endsWith('/') || endpoint.endsWith('\\')) {
            endpoint = endpoint.substr(0, endpoint.length - 1);
        }

        return endpoint;
    }

    public setEndpoint(endpoint: string) {
        this.endpoint = this.sanitizeEndpoint(endpoint);
    }

    public setAuthorization(token: string) {
        this.authorization = token;
    }

    public setTimeout(timeout: number) {
        this.timeout = timeout;
    }

    public send(service: string, method: string, data: IRTServiceClientInData): Promise<any> {
        if (!this.isReady) {
            return new Promise((resolve, reject) => {
                reject("Transport has not been initialized or endpoint is undefined.")
            });
        }

        this.logger.logf(LogLevel.Debug, '====================================================');
        this.logger.logf(LogLevel.Debug, 'Requesting ' + service + ' service, method ' + method);

        return new Promise((resolve, reject) => {
            const url = `${this.endpoint}/${service}/${method}`;
            const payload = data.serialize();
            const payloadHasNoData = Object.keys(payload).length === 0 && payload.constructor === Object;
            const json = payloadHasNoData ? null : this.marshaller.Marshal<IRTServiceClientInData>(payload);
            this.logger.logf(LogLevel.Debug, 'Endpoint: ' + url);
            this.logger.logf(LogLevel.Debug, 'Method: ' + (payloadHasNoData ? 'GET' : 'POST'));

            if (json !== null) {
                this.logger.logf(LogLevel.Trace, 'Request Body:\n' + json);
            }

            this.doRequest(url, json,
                (successContent) => {
                    this.logger.logf(LogLevel.Trace, 'Response body:\n' + successContent);
                    const content = this.marshaller.Unmarshal<any>(successContent);
                    resolve(content);
                },
                (failureContent) => {
                    this.logger.logf(LogLevel.Error, 'Failure:\n' + failureContent);
                    reject(failureContent);
                });
        });
    }

    public subscribe(packageClass: string, callback: (data: any) => void): void {

    }

    unsubscribe(packageClass: string, callback: (data: any) => void): void {

    }
}
