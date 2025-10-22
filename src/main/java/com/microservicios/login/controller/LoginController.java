package com.microservicios.login.controller;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.microservicios.login.exception.MailInvalidoException;
import com.microservicios.login.service.LoginService;

import coms.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private static final Logger logger = Logger.getLogger(LoginController.class.getName());

    private final LoginService service;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${GOOGLE_REDIRECT_URI}")
    private String googleRedirectUri;

    @Value("${LOGIN_REDIRECT}")
    private String loginRedirectUri;


    @Autowired
    public LoginController(LoginService service, ReactiveStringRedisTemplate redisTemplate) {
        this.service = service;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/google/callback")
    public ResponseEntity<String> handleGoogleCallback(@RequestParam("code") String code,
                                                       @RequestParam(value = "error", required = false) String error,
                                                       @RequestParam(value = "state", required = false) String state,
                                                       HttpServletRequest httpRequest)
    throws MailInvalidoException, IOException, GeneralSecurityException
    {
        if (error != null) {
            logger.warning("Error en callback de Google: " + error);
            return ResponseEntity.badRequest().body("Error de autenticación: " + error);
        }

            logger.info("Callback recibido - Procesando autenticación");
            String redirectUri = googleRedirectUri;
            TokenResponse tokenResponse = service.exchangeCodeForTokens(code, redirectUri);
            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            String idTokenString = tokenResponse.get("id_token").toString();
            logger.info("Tengo token " + accessToken);

            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", accessToken);
            tokens.put("refreshToken", refreshToken);

            if (accessToken != null) {

              UserDTO user = service.authenticateUser(idTokenString, accessToken, refreshToken);
           
                // Se usa Spring Session automático
                HttpSession session = httpRequest.getSession(true);

                // Guardamos los tokens en la sesión (automáticamente en Redis)
                session.setAttribute("tokens", tokens);
                session.setAttribute("accessToken", accessToken);
                session.setAttribute("refreshToken", refreshToken);
                session.setAttribute("user", user);
                logger.info("Sesión automática creada con ID: " + session.getId());

                // Ya no enviamos sessionId en la URL, la cookie se envía automáticamente
                String finalRedirectUrl = loginRedirectUri + "/home?auth=success";
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", finalRedirectUrl);
                logger.info("Redireccionando a: " + finalRedirectUrl);
                return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
            } else {
                logger.warning("Fallo en la obtención del Access Token");
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", loginRedirectUri + "/login?auth=error&message=token_not_received")
                        .build();
            }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(
            @SessionAttribute(name = "user", required = false) UserDTO user,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session
    ) {
        // 1) Si la sesión tiene user, lo devolvemos (ignora Bearer)
        if (user != null) {
            // opcional: log para ver que estás leyendo la misma sesión
            Logger.getLogger(LoginController.class.getName())
                    .info("Leyendo user desde sesión id=" + session.getId());
            return ResponseEntity.ok(user); // Jackson lo serializa a JSON
        }

        // 2) Fallback: si no hay user en sesión pero llega Bearer, respondé algo mínimo
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No hay sesión (solo llegó Bearer).");
        }

        // 3) Sin sesión ni Bearer
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("No hay sesión activa.");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false); // No crear nueva si no existe

        if (session != null) {
            // Spring Session elimina automáticamente de Redis
            session.invalidate(); // Esto elimina la sesion de Redis automáticamente
            logger.info("Sesión invalidada correctamente");
            return ResponseEntity.ok("Cierre de sesión realizado correctamente.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No hay sesión activa para cerrar.");
        }
    }

}