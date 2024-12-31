package com.motadata.api;

import com.motadata.constants.Constants;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class Object
{
    public static void init(Router router)
    {
        router.post("/provision").handler(context -> context.vertx().eventBus().request(Constants.POLLER_VERTICLE,context.body().asJsonObject(), reply ->
                {
                    if (reply.succeeded())
                    {
                        context.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
                    }
                    else
                    {
                        context.response().setStatusCode(500).end(new JsonObject().put(Constants.ERROR, "Failed to start discovery").encodePrettily());
                    }
                })
        );
    }
}
