package irt

import (
	"encoding/base64"
	"fmt"
	"net/http"
	"strings"
)

type AuthCustom struct {
	Value string
}

func NewAuthCustom(value string) *AuthCustom {
	return &AuthCustom{
		Value: value,
	}
}

type AuthApiKey struct {
	ApiKey string
}

func NewAuthApiKey(apiKey string) *AuthApiKey {
	return &AuthApiKey{
		ApiKey: apiKey,
	}
}

type AuthToken struct {
	Token string
}

func NewAuthToken(token string) *AuthToken {
	return &AuthToken{
		Token: token,
	}
}

type AuthBasic struct {
	User string
	Pass string
}

func (a *AuthBasic) GetPass() (string, error) {
	pass, err := base64.StdEncoding.DecodeString(a.Pass)
	if err != nil {
		return "", err
	}

	return string(pass), nil
}

func NewAuthBasic(user string, pass string) *AuthBasic {
	return &AuthBasic{
		User: user,
		Pass: base64.StdEncoding.EncodeToString([]byte(pass)),
	}
}

type Authorization struct {
	ApiKey *AuthApiKey
	Token  *AuthToken
	Basic  *AuthBasic
	Custom *AuthCustom
}

func (c *Authorization) Reset() {
	c.ApiKey = nil
	c.Token = nil
	c.Basic = nil
	c.Custom = nil
}

func (c *Authorization) UpdateFromRequest(r http.Request) error {
	return c.UpdateFromHeaders(r.Header)
}

func (c *Authorization) UpdateFromHeaders(h http.Header) error {
	header := h.Get("Authorization")
	if header == "" {
		return nil
	}

	return c.UpdateFromValue(header)
}

func (c *Authorization) UpdateFromValue(auth string) error {
	pieces := strings.Split(auth, " ")
	if len(pieces) != 2 {
		if len(auth) != 0 {
			c.Custom = NewAuthCustom(auth)
			return nil
		}

		return fmt.Errorf("authorization update expects 'Type Value' format, got '%s'", auth)
	}

	switch strings.ToLower(pieces[0]) {
	case "bearer":
		c.Token = &AuthToken{
			Token: pieces[1],
		}
	// There are a few variations exist, Apikey, ApiKey, Api-Key, covering all there.
	case "apikey":
		fallthrough
	case "api-key":
		c.ApiKey = &AuthApiKey{
			ApiKey: pieces[1],
		}
	case "basic":
		basicPieces := strings.Split(pieces[1], ":")
		if len(pieces) != 2 {
			return fmt.Errorf("basic authorization expects 'user:pass' format, got '%s'", pieces[1])
		}
		c.Basic = &AuthBasic{
			User: basicPieces[0],
			Pass: basicPieces[1],
		}
	default:
		c.Custom = NewAuthCustom(auth)
	}

	return nil
}

func (c *Authorization) ToValue() string {
	if c.Token != nil {
		return "Bearer " + c.Token.Token
	}

	if c.ApiKey != nil {
		return "Api-Key " + c.ApiKey.ApiKey
	}

	if c.Basic != nil {
		return "Basic " + c.Basic.User + ":" + c.Basic.Pass
	}

	if c.Custom != nil {
		return c.Custom.Value
	}

	return ""
}
