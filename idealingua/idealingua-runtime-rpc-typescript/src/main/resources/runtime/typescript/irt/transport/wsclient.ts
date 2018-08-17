
export enum WSClientState {
    Connecting,
    Connected,
    Disconnecting,
    Disconnected,
}

export interface WSClientSettings {
    reconnectInterval?: number,
    maxReconnectInterval?: number,
    reconnectDecay?: number,
    connectTimeout?: number,
    maxReconnectAttempts?: number | undefined,
}

const CLOSE_NORMAL = 1000;

export class WSClient {
    private static defaultSettings: WSClientSettings = {
        reconnectInterval: 1000,
        maxReconnectInterval: 15000,
        reconnectDecay: 1.5,
        connectTimeout: 2000,
        maxReconnectAttempts: undefined,
    };

    private _settings: WSClientSettings;
    private _state: WSClientState;
    private _url: string;
    private _protocols: string | string[];
    private _ws: WebSocket;
    private _attempts: number;
    private _forceClose: boolean;

    public onConnect: () => void;
    public onConnecting: (code: number) => void;
    public onDisconnect: (code: number) => void;
    public onMessage: (data: any) => void;
    public onError: (event: Event) => void;

    public get state(): WSClientState {
        return this._state;
    }

    public get url(): string {
        return this._url;
    }

    public get protocols(): string | string[] {
        return this._protocols;
    }

    public get reconnectAttemps(): number {
        return this._attempts;
    }

    constructor(settings: WSClientSettings = WSClient.defaultSettings) {
        this._state = WSClientState.Disconnected;
        this._settings = {
            ...WSClient.defaultSettings,
            ...settings,
        };
        this._url = undefined;
        this._protocols = [];
        this._attempts = 0;
        this._forceClose = false;
        this._state = WSClientState.Disconnected;
    }

    private tryOpen() {
        if (this._settings.maxReconnectAttempts && this._attempts >= this._settings.maxReconnectAttempts) {
            this._state = WSClientState.Disconnected;
            if (this.onDisconnect) {
                this.onDisconnect(CLOSE_NORMAL);
            }
            return;
        }

        const ws = new WebSocket(this._url, this._protocols);
        this._ws = ws;
        // this._ws.binaryType = 'blob' | 'arraybuffer';

        let timedOut = false;
        const timeout = setTimeout(
            () => {
                timedOut = true;
                this._ws.close();
                timedOut = false;
            },
            this._settings.connectTimeout,
        );

        ws.onopen = (event: Event) => {
            clearTimeout(timeout);
            this._state = WSClientState.Connected;
            this._attempts = 0;
            if (this.onConnect) {
                this.onConnect();
            }
        };

        ws.onclose = (event: CloseEvent) => {
            clearTimeout(timeout);
            this._ws = undefined;
            if (this._forceClose) {
                this._state = WSClientState.Disconnected;
                if (this.onDisconnect) {
                    this.onDisconnect(event.code);
                }
                return;
            }

            this._state = WSClientState.Connecting;
            if (this.onConnecting) {
                this.onConnecting(event.code);
            }

            // We can check whether it is timed out here

            const reconnectTimeout = this._settings.reconnectInterval * Math.pow(this._settings.reconnectDecay, this._attempts);
            setTimeout(
                () => {
                    this._attempts++;
                    this.tryOpen();
                },
                reconnectTimeout > this._settings.maxReconnectInterval ? this._settings.maxReconnectInterval : reconnectTimeout,
            );
        };

        ws.onerror = (event: Event) => {
            if (this.onError) {
                this.onError(event);
            }
        };

        ws.onmessage = (event: MessageEvent) => {
            if (this.onMessage) {
                this.onMessage(event.data);
            }
        };
    }

    public open(url: string, protocols: string | string[] = []) {
        this.close();
        this._forceClose = false;
        this._attempts = 0;
        this._url = url;
        this._protocols = protocols;
        this.tryOpen();
    }

    public close() {
        if (!this._ws) {
            return;
        }

        this._state = WSClientState.Disconnecting;
        this._forceClose = true;
        this._ws.close(CLOSE_NORMAL);
    }

    public send(data: any) {
        if (!this._ws || this._state !== WSClientState.Connected) {
            throw new Error('WebSocket is not yet available.');
        }

        this._ws.send(data);
    }
}
