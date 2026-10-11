package com.loopia.gameanimationshow.common;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;


/**
 * JwtUtil 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 13:31 
 */


@Component
public class JwtUtil {

    @Value("${loopia.jwt.secret}")
    private String secret;

    @Value("${loopia.jwt.expiration}")
    private Long expiration;

    public String createToken(Long userId, Integer role) {
        Date expireDate = new Date(System.currentTimeMillis() + expiration);
        Algorithm algorithm = Algorithm.HMAC256(secret);

        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("role", role)
                .withExpiresAt(expireDate)
                .sign(algorithm);
    }

    public DecodedJWT verifyToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }
}