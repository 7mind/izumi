
import { AuthMethod } from './auth/auth';

export interface ServiceClientInData {
    serialize(): any;
}

export type ServiceClientOutData = any;
export type TransportHeaders = {[key: string]: string};

export interface ClientTransport {
    send(service: string, method: string, data: ServiceClientInData): Promise<ServiceClientOutData>
    setAuthorization(method: AuthMethod): void
    getAuthorization(): AuthMethod | undefined
    setHeaders(headers: TransportHeaders): void
    getHeaders(): TransportHeaders
}

export interface ClientSocketTransport extends ClientTransport { 
    // ClientSocketTransport supports streams and buzzer calls
}