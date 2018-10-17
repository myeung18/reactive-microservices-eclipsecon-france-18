package io.escoffier.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.redis.RedisClient;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.RedisDataSource;

import javax.xml.ws.spi.ServiceDelegate;
import java.util.HashMap;
import java.util.Map;

public class ShoppingBackendVerticle extends AbstractVerticle {

  private RedisClient redis;
  private Map<String, Integer> list = new HashMap<>();

  @Override
  public void start() {
    Router router = Router.router(vertx);

    router.get("/").handler(rc -> rc.response().end("Bonjour!"));
    router.get("/shopping").handler(this::getList);

    router.route().handler(BodyHandler.create());

    router.post("/shopping").handler(this::addToList);
    router.delete("/shopping/:name").handler(this::removeFromList);

    ServiceDiscovery.create(vertx, discovery -> {

    RedisDataSource.getRedisClient(discovery, svc -> svc.getName().equals("redis"),
          ar -> {
            if (ar.failed()) {
              System.out.println("Holy cow!");
            } else {
              redis = ar.result();
              vertx.createHttpServer()
                      .requestHandler(router::accept)
                      .listen(8080);
            }
          });
    });
  }

  private void removeFromList(RoutingContext rc) {
    String name = rc.pathParam("name");
    redis.hdel("SHOPPING", name, x -> {
      getList(rc);
    });
  }

  private void addToList(RoutingContext rc) {
    JsonObject body = rc.getBodyAsJson();
    String name = body.getString("name");
    Integer quantity = body.getInteger("quantity", 1);

    redis.hset("SHOPPING", name, quantity.toString(), x -> {
      getList(rc);
    });

  }

  private void getList(RoutingContext rc) {
      redis.hgetall("SHOPPING", ar -> {
        if (ar.failed()) {
          rc.fail(500);
        } else {
          rc.response().end(ar.result().encodePrettily());
        }
      });
  }

}
