
export interface IRTServiceClientInData {
    serialize(): any;
}

export interface IRTServiceClientOutData {
}

export interface IRTClientTransport {
    send(service: string, method: string, data: IRTServiceClientInData): Promise<IRTServiceClientOutData>
    subscribe(packageClass: string, callback: (data: any) => void): void
    unsubscribe(packageClass: string, callback: (data: any) => void): void
    log(content: string | Error): void
}

export class IRTHTTPClientTransport implements IRTClientTransport {
    public endpoint: string;
    private authorization: string;

    constructor(endpoint: string = undefined) {
        this.setEndpoint(endpoint);
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
            req.setRequestHeader("Content-type", "application/json");
        } else {
            req.open('GET', url, true);
        }

        if (this.authorization) {
            req.setRequestHeader('Authorization', 'Bearer ' + this.authorization);
        }

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

    public send(service: string, method: string, data: IRTServiceClientInData): Promise<any> {
        if (!this.isReady) {
            return new Promise((resolve, reject) => {
                reject("Transport has not been initialized or endpoint is undefined.")
            });
        }

        return new Promise((resolve, reject) => {
            const url = `${this.endpoint}/${service}/${method}`;
            const payload = data.serialize();
            const payloadHasNoData = Object.keys(payload).length === 0 && payload.constructor === Object;
            this.doRequest(url, payloadHasNoData ? null : JSON.stringify(payload) ,
                (successContent) => {
                    resolve(JSON.parse(successContent));
                },
                (failureContent) => {
                    reject(failureContent);
                });
        });
    }

    public subscribe(packageClass: string, callback: (data: any) => void): void {

    }

    unsubscribe(packageClass: string, callback: (data: any) => void): void {

    }

    log(content: string | Error): void {
        console.log(content)
    }
}
