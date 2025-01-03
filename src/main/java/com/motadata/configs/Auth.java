package com.motadata.configs;

import com.motadata.constants.Constants;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Auth {

    private static JWTAuth JWT_AUTH;

    private static final Logger LOGGER = LoggerFactory.getLogger(Auth.class);

    /* takes jwt configs from config.json and creates token using it */
    public static void initialize(Vertx vertx, JsonObject config)
    {
        try
        {
            if (JWT_AUTH == null)
            {
                synchronized (Auth.class)
                {
                    var secretKey = config.getString(Constants.JWT_SECRET_KEY, "default_secret_key");

                    JWT_AUTH = JWTAuth.create(vertx, new JWTAuthOptions().addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer(secretKey)).setJWTOptions(new JWTOptions().setExpiresInSeconds(Constants.JWT_EXPIRY_TIME_IN_SECONDS)));
                }
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to initialize jwt auth token. {}", exception.getMessage());
        }
    }

    // Getter to return the jwtAuth instance
    public static JWTAuth jwtAuth()
    {
        return JWT_AUTH;
    }
}
