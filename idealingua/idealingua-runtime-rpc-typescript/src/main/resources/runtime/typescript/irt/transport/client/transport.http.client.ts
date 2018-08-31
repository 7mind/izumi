
import { AuthMethod, Authorization } from '../auth';
import { ClientTransport, IncomingData, TransportHeaders } from '../transport';
import { DummyLogger, Logger, LogLevel } from '../../logger';
import { JSONMarshaller } from '../../marshaller';

export class HTTPClientTransport implements ClientTransport {
    public endpoint: string;
    private auth: Authorization;
    private timeout: number;
    private logger: Logger;
    private marshaller: JSONMarshaller;
    private headers: TransportHeaders;

    public onSend: (service: string, method: string, payload: string) => void;
    public onSuccess: (service: string, method: string, payload: string) => void;
    public onFailure: (service: string, method: string,  error: string) => void;

    constructor(endpoint: string, marshaller: JSONMarshaller, logger: Logger = undefined) {
        this.setEndpoint(endpoint);
        this.timeout = 60 * 1000;
        this.logger = logger;
        if (!this.logger) {
            this.logger = new DummyLogger();
        }
        this.marshaller = marshaller;
        this.headers = {};
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

        const req = new XMLHttpRequest();
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

        if (this.headers) {
            for (let key in this.headers) {
                if (!this.headers.hasOwnProperty(key)) {
                    continue;
                }

                req.setRequestHeader(key, this.headers[key]);
            }
        }

        if (this.auth) {
            req.setRequestHeader('Authorization', this.auth.toValue());
            this.logger.logf(LogLevel.Debug, 'Header: Authorization: ' + this.auth.toValue());
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

    public setHeaders(headers: TransportHeaders | undefined) {
        this.headers = headers || {};
    }

    public getHeaders(): TransportHeaders {
        return this.headers;
    }

    public getAuthorization(): AuthMethod | undefined {
        return this.auth ? this.auth.method : undefined;
    }

    public setAuthorization(method: AuthMethod | undefined) {
        if (!method) {
            this.auth = undefined;
            return;
        }

        this.auth = new Authorization();
        this.auth.method = method;
    }

    public setTimeout(timeout: number) {
        this.timeout = timeout;
    }

    public send(service: string, method: string, data: IncomingData): Promise<any> {
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
            const json = payloadHasNoData ? null : this.marshaller.Marshal<IncomingData>(payload);
            this.logger.logf(LogLevel.Debug, 'Endpoint: ' + url);
            this.logger.logf(LogLevel.Debug, 'Method: ' + (payloadHasNoData ? 'GET' : 'POST'));

            if (json !== null) {
                this.logger.logf(LogLevel.Trace, 'Request Body:\n' + json);
            }

            if (this.onSend) {
                this.onSend(service, method, json);
            }
            this.doRequest(url, json,
                (successContent) => {
                    this.logger.logf(LogLevel.Trace, 'Response body:\n' + successContent);
                    if (this.onSuccess) {
                        this.onSuccess(service, method, successContent);
                    }
                    const content = this.marshaller.Unmarshal<any>(successContent);
                    resolve(content);
                },
                (failureContent) => {
                    this.logger.logf(LogLevel.Error, 'Failure:\n' + failureContent);
                    if (this.onFailure) {
                        this.onFailure(service, method, failureContent);
                    }
                    reject(failureContent);
                });
        });
    }
}