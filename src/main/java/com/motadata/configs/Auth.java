package com.motadata.configs;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.core.json.JsonObject;


public class Auth {

    private static JWTAuth jwtAuth;

    // Static block to initialize JWTAuth when it's needed
    public static void initialize(Vertx vertx, JsonObject config)
    {
        if (jwtAuth == null)
        {
            synchronized (Auth.class)
            {
                String secretKey = config.getString("jwt_secret_key", "default_secret_key");

                jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions().addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer("your_secret_key")));
            }
        }
    }

    // Getter to return the jwtAuth instance
    public static JWTAuth getJwtAuth()
    {
        return jwtAuth;
    }
}
