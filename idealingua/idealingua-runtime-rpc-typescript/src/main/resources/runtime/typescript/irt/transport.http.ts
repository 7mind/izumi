
import { AuthMethod, Authorization } from './auth';
import { ClientTransport, ServiceClientInData } from './transport';
import { DummyLogger, Logger, LogLevel } from './logger';
import { JSONMarshaller } from './marshaller';

export class HTTPClientTransport implements ClientTransport {
    public endpoint: string;
    private auth: Authorization;
    private timeout: number;
    private logger: Logger;
    private marshaller: JSONMarshaller;

    constructor(endpoint: string, marshaller: JSONMarshaller, logger: Logger = undefined) {
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

    public setAuthorization(method: AuthMethod) {
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

    public send(service: string, method: string, data: ServiceClientInData): Promise<any> {
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
            const json = payloadHasNoData ? null : this.marshaller.Marshal<ServiceClientInData>(payload);
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
}