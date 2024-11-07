package com.motadata.configs;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;

public class Auth {

    private final JWTAuth jwtAuth;

    public Auth(Vertx vertx) {
        JWTAuthOptions options = new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer("your_secret_key")); // Set your secret key

        this.jwtAuth = JWTAuth.create(vertx, options);
    }

    public JWTAuth getJwtAuth() {
        return jwtAuth;
    }
}
