
package com.microservicios.login.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.ToString;


@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
@Getter
public class AirtableUserResponse {
    private String id;
    private FieldsDTO fields;

    public String getId() {
        return id;
    }

    public FieldsDTO getFields() {
        return fields;
    }
}