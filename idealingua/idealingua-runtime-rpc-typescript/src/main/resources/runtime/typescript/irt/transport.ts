
import { AuthMethod } from './auth';

export interface ServiceClientInData {
    serialize(): any;
}

export type ServiceClientOutData = any;

export interface ClientTransport {
    send(service: string, method: string, data: ServiceClientInData): Promise<ServiceClientOutData>
    setAuthorization(method: AuthMethod): void
}

