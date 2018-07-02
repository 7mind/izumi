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
}

func NewHTTPClientTransport(endpoint string, timeout int, skipSSLVerify bool) *HTTPClientTransport {
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
    }
}

func (c *HTTPClientTransport) Send(service string, method string, dataIn interface{}, dataOut interface{}) error {
    url := c.Endpoint + service + "/" + method

    var req *http.Request
    var err error
    if dataIn == nil {
        req, err = http.NewRequest("GET", url, nil)
    } else {
        body, err := json.Marshal(dataIn)
        if err != nil {
            return err
        }
        req, err = http.NewRequest("POST", url, bytes.NewBuffer(body))
        if err != nil {
            return err
        }
        req.Header.Set("Content-Type", "application/json")
    }

    if c.Authorization != "" {
        req.Header.Set("Authorization", "Bearer "+c.Authorization)
    }

    resp, err := c.Client.Do(req)
    if err != nil {
        return err
    }

    defer resp.Body.Close()
    respBody, err := ioutil.ReadAll(resp.Body)
    if err != nil {
        return err
    }

    if err := json.Unmarshal(respBody, dataOut); err != nil {
        return fmt.Errorf("error while unmarshalling data %+v Body: %s", err, string(respBody))
    }

    return nil
}
