
export enum WebSocketMessageKind {
    Failure = '?:failure',
    RpcRequest = 'rpc:request',
    RpcResponse = 'rpc:response',
    RpcFailure = 'rpc:failure',
    BuzzerRequest = 'buzzer:request',
    BuzzerResponse = 'buzzer:response',
    BuzzerFailure = 'buzzer:failure',
    StreamS2C = 'stream:s2c',
    StreamC2S = 'stream:c2s'
}

export interface WebSocketMessageBase {
    kind: WebSocketMessageKind
}

// Matches WebSocketMessageKind.RpcRequest, WebSocketMessageKind.BuzzerRequest
export interface WebSocketRequestMessage extends WebSocketMessageBase {
    service?: string
    method?: string
    id: string
    data?: any
    headers?: {[key: string]: string}
}

// Matches WebSocketMessageKind.RpcResponse, WebSocketMessageKind.RpcFailure, 
//      WebSocketMessageKind.BuzzerResponse, WebSocketMessageKind.BuzzerFailure
export interface WebSocketResponseMessage extends WebSocketMessageBase {
    ref: string;
    data: any;
}

// Matches WebSocketMessageKind.Failure
export interface WebSocketFailureMessage extends WebSocketMessageBase {
    data: string;
    cause: string;
}
