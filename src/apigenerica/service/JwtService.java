/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;

/**
 * @author Grupo1 
 * Gestiona tokens JWT, utilizados para identificar al cliente 
 * de cada petición HTTP
 */
public class JwtService {

    private static final String SECRET_KEY = System.getenv("SECRET_KEY");
    private static final Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);

    public String generarToken(Long usuarioId, String rol) {
        return JWT.create()
                .withIssuer("tu_empresa_erp")
                .withClaim("id", usuarioId)
                .withClaim("rol", rol)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600 * 1000)) // Caduca en 1 hora
                .sign(algorithm);
    }

    public DecodedJWT verificarToken(String token) {
        return JWT.require(algorithm)
                .withIssuer("tu_empresa_erp")
                .build()
                .verify(token); // Lanza excepción si la firma es inválida o expiró
    }
}
