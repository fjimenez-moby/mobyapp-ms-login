package com.microservicios.login.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@ToString
public class FieldsDTO {
    @JsonProperty("Nombre")
    private String nombre;

    @JsonProperty("Correo Moby")
    private String correoMoby;

    @JsonProperty("Foto de Perfil URL")
    private String fotoPerfilUrl;

}

