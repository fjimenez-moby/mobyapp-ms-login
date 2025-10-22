package com.microservicios.login.exception.handler;

import com.microservicios.login.exception.MailInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ControllerAdvice
public class GlobalExceptionHandler {

    Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @Value("${LOGIN_REDIRECT}")
    private String loginRedirectUri;

    @ExceptionHandler(MailInvalidoException.class)
    public ResponseEntity<String> handleMailInvalido(MailInvalidoException ex) {
        logger.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION,loginRedirectUri + "/login?auth=error&type=invalid_email")
                .build();
    }

    @ExceptionHandler(Exception.class)
        public ResponseEntity<Void> handleException(Exception ex) {
        logger.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION,loginRedirectUri + "/login?auth=error&type=server_error")
                .build();
    }

}
