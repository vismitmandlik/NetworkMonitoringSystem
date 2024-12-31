package com.motadata.configs;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.core.json.JsonObject;


public class Auth {

    private static JWTAuth JWT_AUTH;

    /* takes jwt configs from config.json and creates token using it */
    public static void initialize(Vertx vertx, JsonObject config)
    {
        try
        {
            if (JWT_AUTH == null)
            {
                synchronized (Auth.class)
                {
                    var secretKey = config.getString("jwt_secret_key", "default_secret_key");

                    JWT_AUTH = JWTAuth.create(vertx, new JWTAuthOptions().addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer(secretKey)));
                }
            }
        }
        catch (Exception exception)
        {
            System.err.println("Failed to initialize jwt auth token. " + exception);
        }

    }

    // Getter to return the jwtAuth instance
    public static JWTAuth jwtAuth()
    {
        return JWT_AUTH;
    }
}
