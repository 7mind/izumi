
import { AuthMethod } from './auth';
import { ServiceDispatcher } from '../dispatcher';

export interface IncomingData {
    serialize(): any;
}

export type OutgoingData = any;
export type TransportHeaders = {[key: string]: string};

export interface ClientTransport {
    send(service: string, method: string, data: IncomingData): Promise<OutgoingData>
    setAuthorization(method: AuthMethod | undefined): void
    getAuthorization(): AuthMethod | undefined
    setHeaders(headers: TransportHeaders | undefined): void
    getHeaders(): TransportHeaders
}

export interface ClientSocketTransport<C, D> extends ClientTransport {
    setContext(context: C): void
    getContext(): C
    registerBuzzer(buzzer: ServiceDispatcher<C, D>): boolean
    unregisterBuzzer(id: string): boolean
}

export interface ServerSocketTransport {
    send(service: string, method: string, data: IncomingData): Promise<OutgoingData>
}