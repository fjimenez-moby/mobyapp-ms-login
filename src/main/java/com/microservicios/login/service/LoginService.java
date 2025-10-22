package com.microservicios.login.service;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.microservicios.login.dto.AirtableMapper;
import com.microservicios.login.dto.AirtableUserResponse;
import com.microservicios.login.exception.MailInvalidoException;
import coms.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class LoginService {

    private static final Logger logger = Logger.getLogger(LoginService.class.getName());

    @Value("${google.client.id}")
    private String googleClientId;

    @Value("${google.client.secret}")
    private String googleClientSecret;

    @Value("${MAIL_CHECK}")
    private String mailCheckUri;

    @Value("${URL_BASE}")
    private String urlBase;

    @Autowired
    RestTemplate restTemplate;
    private final AirtableMapper airtableMapper;
    private final GoogleIdTokenVerifier verifier;

    public LoginService() {
        this.airtableMapper = new AirtableMapper();
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory())
                .setAudience(Collections.emptyList()) // Se configurará dinámicamente
                .build();
    }

    public UserDTO verifyGoogleToken(String idTokenString) throws GeneralSecurityException, IOException, MailInvalidoException {

        logger.info("Verificando token de Google...");

        GoogleIdTokenVerifier actualVerifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = actualVerifier.verify(idTokenString);

        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();

            String email = payload.getEmail();
            Boolean emailVerified = payload.getEmailVerified();
            String name = (String) payload.get("given_name");
            String lastName = (String) payload.get("family_name");
            String pictureUrl = (String) payload.get("picture");

            if (email == null || !email.endsWith("@mobydigital.com") || !emailVerified) {
                logger.warning("Error al checkear el mail. \n Email: " + email + "\n emailVerified: " + emailVerified);
                throw new MailInvalidoException("El mail debe pertenecer a la empresa");

            }
            // La URL debe apuntar al endpoint de búsqueda por query parameter: /user?email={email}
            String urlUser = urlBase + "user?email={email}";
            try {
                AirtableUserResponse response = restTemplate.getForObject(urlUser, AirtableUserResponse.class, email);
                logger.info("Usuario encontrado en la tabla de Usuarios. Email: " + email);
                return airtableMapper.fromAirtable(response);

            } catch (HttpClientErrorException exU) {
                // Si el usuario NO existe en la nueva tabla (404 NOT FOUND)
                if (exU.getStatusCode() == HttpStatus.NOT_FOUND) {
                    logger.warning("Usuario NO encontrado en la tabla de usuarios. Iniciando proceso de migración para: " + email);
                    // Verifica la existencia en la nomina activa
                    // Usaremos el endpoint que devuelve TRUE/FALSE si está activo en la nómina antigua.
                    // Endpoint: /api/records/checkEmail?email={email}
                    String urlCheckNomina = mailCheckUri + email;
                    Boolean exists = null;
                    try {
                        // restTemplate.getForObject maneja el 404/400 aquí, si el endpoint de JS devuelve TRUE/FALSE o lanza 404/400.
                        exists = restTemplate.getForObject(urlCheckNomina, Boolean.class);
                    } catch (HttpClientErrorException exN) {
                        // Si checkEmail falla con 404/400 (no existe en nómina), capturamos.
                        logger.warning("El mail no existe en nómina activa. Status: " + exN.getStatusCode());
                        throw new MailInvalidoException("El mail no está habilitado en la nómina activa.");
                    }

                    if (Boolean.FALSE.equals(exists)) {
                        logger.warning("El mail está inactivo en la nómina antigua. Email: " + email);
                        throw new MailInvalidoException("El mail no está habilitado en la nómina activa.");
                    }

                    // Si existe y está activo en la nómina antigua, llamamos al endpoint de migración.
                    // Payload final con datos de Google
                    Map<String, Object> migrationPayload = new HashMap<>();
                    migrationPayload.put("email", email);
                    migrationPayload.put("nombre", name);
                    migrationPayload.put("apellido", lastName);
                    migrationPayload.put("foto", pictureUrl);

                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(migrationPayload);

                    // URL al endpoint de migración: /api/records/migrateUser
                    String urlMigrate = urlBase + "migrateUser";
                    AirtableUserResponse response = restTemplate.postForObject(urlMigrate, requestEntity, AirtableUserResponse.class);
                    return airtableMapper.fromAirtable(response);
                } else {
                    // Manejar otros errores HTTP (401, 403, 500, etc.)
                    logger.severe("Error HTTP inesperado al buscar usuario: " + exU.getStatusCode());
                    throw exU;
                }
            } catch (RuntimeException e) {
                // Capturar errores de conexión u otros errores inesperados
                logger.severe("Error general al comunicarse con microservicio: " + e.getMessage());
                throw e;
            }
        }
        return null;
    }

    public TokenResponse exchangeCodeForTokens(String code, String redirectUri) throws IOException {
        return new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                googleClientId,
                googleClientSecret,
                code,
                redirectUri)
                .execute();
    }

    public UserDTO authenticateUser(String idTokenString, String accessToken, String refreshToken) throws GeneralSecurityException, IOException, MailInvalidoException {
        if (idTokenString == null || idTokenString.trim().isEmpty()) {
            logger.warning("ID Token vacío o nulo recibido");
            return null;
        }
        UserDTO user = verifyGoogleToken(idTokenString);

        if (user != null) {
            logger.info("Usuario autenticado exitosamente ! : " + user.getEmail());
            // Los tokens ahora se guardan automáticamente en Redis vía Spring Session
        }

        return user;
    }
}
