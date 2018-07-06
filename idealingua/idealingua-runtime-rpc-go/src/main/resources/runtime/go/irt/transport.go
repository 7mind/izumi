package irt

import (
    "bytes"
    "crypto/tls"
    "encoding/json"
    "fmt"
    "io/ioutil"
    "net/http"
    "time"
)

type ServiceClientTransport interface {
    Send(service string, method string, dataIn interface{}, dataOut interface{}) error
}

type HTTPClientTransport struct {
    Endpoint      string
    Client        *http.Client
    Transport     *http.Transport
    Authorization string
    Logger        Logger
}

func NewHTTPClientTransportWithLogger(endpoint string, timeout int, skipSSLVerify bool, logger Logger) *HTTPClientTransport {
    transport := &http.Transport{
        TLSClientConfig:       &tls.Config{InsecureSkipVerify: skipSSLVerify},
        ExpectContinueTimeout: time.Millisecond * time.Duration(timeout),
        ResponseHeaderTimeout: time.Millisecond * time.Duration(timeout),
    }
    client := &http.Client{Transport: transport}
    return &HTTPClientTransport{
        Endpoint:  endpoint,
        Transport: transport,
        Client:    client,
        Logger:    logger,
    }
}

func NewHTTPClientTransport(endpoint string, timeout int, skipSSLVerify bool) *HTTPClientTransport {
    return NewHTTPClientTransportWithLogger(endpoint, timeout, skipSSLVerify, NewDummyLogger())
}

func (c *HTTPClientTransport) Send(service string, method string, dataIn interface{}, dataOut interface{}) error {
    url := c.Endpoint + service + "/" + method
    var req *http.Request
    var err error

    c.Logger.Logf(LogDebug, "================================================")
    c.Logger.Logf(LogDebug, "Requesting Service: %s, Method: %s", service, method)
    c.Logger.Logf(LogDebug, "Endpoint: %s", url)
    if dataIn == nil {
        c.Logger.Logf(LogDebug, "Method: GET")
        req, err = http.NewRequest("GET", url, nil)
    } else {
        c.Logger.Logf(LogDebug, "Method: POST")
        body, err := json.Marshal(dataIn)
        if err != nil {
            c.Logger.Logf(LogError, "Data in marshalling failed: %s", err.Error())
            return err
        }
        req, err = http.NewRequest("POST", url, bytes.NewBuffer(body))
        if err != nil {
            c.Logger.Logf(LogError, "Create POST request failed: %s", err.Error())
            return err
        }

        c.Logger.Logf(LogTrace, "Request body:\n`%s`", string(body))
        c.Logger.Logf(LogDebug, "Header: Content-Type: application/json")
        req.Header.Set("Content-Type", "application/json")
    }

    if c.Authorization != "" {
        c.Logger.Logf(LogDebug, "Header: Authorization: %s", c.Authorization)
        req.Header.Set("Authorization", c.Authorization)
    }

    resp, err := c.Client.Do(req)
    if err != nil {
        c.Logger.Logf(LogError, "Request failed: %s", err.Error())
        return err
    }
    c.Logger.Logf(LogDebug, "Response Status: %d", resp.StatusCode)
    c.Logger.Logf(LogDebug, "Response length: %d", resp.ContentLength)

    defer resp.Body.Close()
    respBody, err := ioutil.ReadAll(resp.Body)
    if err != nil {
        c.Logger.Logf(LogError, "Response reading failed: %s", err.Error())
        return err
    }

    c.Logger.Logf(LogTrace, "Response body:\n`%s`", string(respBody))
    if resp.StatusCode != 200 {
        msg := fmt.Sprintf("Server returned code %d. Response body: %s. Endpoint: %s", resp.StatusCode, string(respBody), url)
        c.Logger.Logf(LogError, msg)
        return fmt.Errorf(msg)
    }

    if err := json.Unmarshal(respBody, dataOut); err != nil {
        msg := fmt.Sprintf("error while unmarshalling data %+v Body: %s. Endpoint: %s", err, string(respBody), url)
        c.Logger.Logf(LogError, msg)
        return fmt.Errorf(msg)
    }

    c.Logger.Logf(LogDebug, "================================================")
    return nil
}
