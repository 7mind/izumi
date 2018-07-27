package irt

import (
	"strings"
	"time"
)

// TODO Replace with better logic than brute-forcing this thing
var DATETIME_FORMATS = []string{
	"2006-01-02T15:04:05Z07:00",
	"2006-01-02T15:04:05.0Z07:00",
	"2006-01-02T15:04:05.00Z07:00",
	"2006-01-02T15:04:05.000Z07:00",
	"2006-01-02T15:04:05.0000Z07:00",
	"2006-01-02T15:04:05.00000Z07:00",
	"2006-01-02T15:04:05.000000Z07:00",
	"2006-01-02T15:04:05.0000000Z07:00",
	"2006-01-02T15:04:05.00000000Z07:00",
	"2006-01-02T15:04:05.000000000Z07:00",
	"2006-01-02T15:04:05Z",
	"2006-01-02T15:04:05.0Z",
	"2006-01-02T15:04:05.00Z",
	"2006-01-02T15:04:05.000Z",
	"2006-01-02T15:04:05.0000Z",
	"2006-01-02T15:04:05.00000Z",
	"2006-01-02T15:04:05.000000Z",
	"2006-01-02T15:04:05.0000000Z",
	"2006-01-02T15:04:05.00000000Z",
	"2006-01-02T15:04:05.000000000Z",
	"2006-01-02T15:04:05",
	"2006-01-02T15:04:05.0",
	"2006-01-02T15:04:05.00",
	"2006-01-02T15:04:05.000",
	"2006-01-02T15:04:05.0000",
	"2006-01-02T15:04:05.00000",
	"2006-01-02T15:04:05.000000",
	"2006-01-02T15:04:05.0000000",
	"2006-01-02T15:04:05.00000000",
	"2006-01-02T15:04:05.000000000",
}

func ReadTime(value string) (time.Time, error) {
	return time.Parse("15:04:05.000", value)
}

func WriteTime(value time.Time) string {
	return value.Format("15:04:05.000")
}

func ReadDate(value string) (time.Time, error) {
	return time.Parse("2006-01-02", value)
}

func WriteDate(value time.Time) string {
	return value.Format("2006-01-02")
}

func ReadDateTime(value string, utc bool) (time.Time, error) {
	regionIndex := strings.Index(value, "[")
	var region *time.Location
	var err error

	if regionIndex >= 0 {
		region, err = time.LoadLocation(value[regionIndex : len(value)-1])
		if err != nil {
			region = nil
		}
		value = value[:regionIndex]
	}

	var t time.Time

	if region != nil {
		for _, f := range DATETIME_FORMATS {
			t, err = time.ParseInLocation(f, value, region)
			if err == nil {
				return t, nil
			}
		}
	} else {
		for _, f := range DATETIME_FORMATS {
			t, err = time.Parse(f, value)
			if err == nil {
				return t, nil
			}
		}
	}

	if err != nil {
		return t, err
	}

	if utc {
		return t.UTC(), nil
	}

	return t, nil
}

func ReadLocalDateTime(value string) (time.Time, error) {
	return ReadDateTime(value, false)
}

func WriteLocalDateTime(value time.Time) string {
	return value.Format("2006-01-02T15:04:05.000")
}

func ReadZoneDateTime(value string) (time.Time, error) {
	return ReadDateTime(value, false)
}

func WriteZoneDateTime(value time.Time) string {
	return value.Format("2006-01-02T15:04:05.000-07:00")
}

func ReadUTCDateTime(value string) (time.Time, error) {
	return ReadDateTime(value, true)
}

func WriteUTCDateTime(value time.Time) string {
	return value.UTC().Format("2006-01-02T15:04:05.000Z")
}
