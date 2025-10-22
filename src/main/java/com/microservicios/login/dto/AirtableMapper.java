package com.microservicios.login.dto;


import coms.dto.UserDTO;

public class AirtableMapper {

    public UserDTO fromAirtable(AirtableUserResponse airtableUser) {
        UserDTO dto = new UserDTO();
        if (airtableUser == null || airtableUser.getFields() == null) {
            return dto;
        }
        FieldsDTO f = airtableUser.getFields();
        if (f.getNombre() != null && !f.getNombre().isBlank()) {
            dto.setName(f.getNombre());
        }
        if (f.getCorreoMoby() != null && !f.getCorreoMoby().isBlank()) {
            dto.setEmail(f.getCorreoMoby());
        }
        if (f.getFotoPerfilUrl() != null && !f.getFotoPerfilUrl().isBlank()) {
            dto.setProfilePicture(f.getFotoPerfilUrl());
        }
        return dto;
    }
}
