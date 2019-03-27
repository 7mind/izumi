package irt

import (
	"encoding/json"
	"fmt"
)

type Marshaller interface {
	Marshal(data interface{}) ([]byte, error)
	Unmarshal(data []byte, model interface{}) error
}

type JSONMarshaller struct {
	pretty bool
}

func NewJSONMarshaller(pretty bool) *JSONMarshaller {
	return &JSONMarshaller{
		pretty: pretty,
	}
}

func (m *JSONMarshaller) Marshal(data interface{}) ([]byte, error) {
	if m.pretty {
		return json.MarshalIndent(data, "", "    ")
	} else {
		return json.Marshal(data)
	}
}

func (m *JSONMarshaller) Unmarshal(data []byte, model interface{}) error {
	return json.Unmarshal(data, model)
}

type BinaryMarshaller struct {
}

func NewBinaryMarshaller() *BinaryMarshaller {
	return &BinaryMarshaller{}
}

func (m *BinaryMarshaller) Marshal(data interface{}) ([]byte, error) {
	return nil, fmt.Errorf("binary marshaller is not implemented")
}

func (m *BinaryMarshaller) Unmarshal(data []byte, model interface{}) error {
	return fmt.Errorf("binary marshaller is not implemented")
}
