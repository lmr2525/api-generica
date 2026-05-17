/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.config.AppConfig;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestiona tokens JWT, utilizados para identificar al cliente de
 * cada petición HTTP
 * 
 * @author Grupo1 
 */
public class JwtService {

    private static final Algorithm algorithm = Algorithm.HMAC256(AppConfig.getSecretKey());

    /**
     * Genera JWT Access Token
     *
     * @param usuarioId
     * @param rol
     * @return
     */
    public String generarAccessToken(Long usuarioId, Integer rol) {
        return JWT.create()
                .withIssuer("tu_empresa_erp")
                .withClaim("id", usuarioId)
                .withClaim("rol", rol)
                .withClaim("tipo", "access")
                .withExpiresAt(new Date(System.currentTimeMillis() + 15 * 60 * 1000)) // Caduca en 15 minutos
                .sign(algorithm);
    }

    public String generarRefreshToken(Long usuarioId) {
        return JWT.create()
                .withIssuer("tu_empresa_erp")
                .withClaim("id", usuarioId)
                .withClaim("tipo", "refresh")
                .withExpiresAt(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)) // Caduca en 7 días
                .sign(algorithm);
    }

    public DecodedJWT verificarToken(String token) {
        return JWT.require(algorithm)
                .withIssuer("tu_empresa_erp")
                .build()
                .verify(token); // Lanza excepción si la firma es inválida o expiró
    }

    public Map<String, String> insertarTokensRespuesta(Long usuarioId, Integer rol) {
        String accessToken = generarAccessToken(usuarioId, rol);
        String refreshToken = generarRefreshToken(usuarioId);
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("access_token", accessToken);
        respuesta.put("refresh_token", refreshToken);
        return respuesta;
    }
}
