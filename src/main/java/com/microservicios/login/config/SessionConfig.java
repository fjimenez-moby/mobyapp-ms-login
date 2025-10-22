package com.microservicios.login.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;


@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600) // Sesión expira en 1 hora
public class SessionConfig {

    /**
     * CONFIGURACIÓN DE COOKIES AUTOMÁTICAS
     *
     * Esta configuración le dice al navegador cómo manejar las cookies:
     * - JSESSIONID: Nombre estándar de la cookie de sesión
     * - HttpOnly: JavaScript no puede leer la cookie (anti-hackers)
     * - SameSite: Protección contra ataques CSRF
     * - Path: La cookie funciona en toda la aplicación
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();

        // Nombre de la cookie que el navegador va a guardar automáticamente
        serializer.setCookieName("JSESSIONID");

        // HttpOnly = true: JavaScript no puede robar la cookie
        serializer.setUseHttpOnlyCookie(true);

        // Secure = false para desarrollo (cambiar a true en producción con HTTPS)
        serializer.setUseSecureCookie(false);

        // SameSite protege contra ataques de otros sitios web
        serializer.setSameSite("Lax");

        // Path = "/" significa que la cookie funciona en toda la app
        serializer.setCookiePath("/");

        return serializer;
    }

    /**
     * Este bean configura el serializador para los atributos de la sesión en Redis.
     * Usamos GenericJackson2JsonRedisSerializer para guardar los datos como JSON,
     * lo cual evita los caracteres extraños de la serialización de Java.
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }

    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }
}