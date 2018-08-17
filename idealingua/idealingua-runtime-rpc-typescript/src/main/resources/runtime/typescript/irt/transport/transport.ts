
import { AuthMethod } from './auth/auth';

export interface IncomingData {
    serialize(): any;
}

export type OutgoingData = any;
export type TransportHeaders = {[key: string]: string};

export interface ClientTransport {
    send(service: string, method: string, data: IncomingData): Promise<OutgoingData>
    setAuthorization(method: AuthMethod): void
    getAuthorization(): AuthMethod | undefined
    setHeaders(headers: TransportHeaders): void
    getHeaders(): TransportHeaders
}

export interface ClientSocketTransport extends ClientTransport { 
    // ClientSocketTransport supports streams and buzzer calls
}