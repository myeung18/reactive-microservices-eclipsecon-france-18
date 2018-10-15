package me.escoffier.demo;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;

public class MyShoppingList extends AbstractVerticle {

  private WebClient shopping, pricer;
  private CircuitBreaker circuit;

  @Override
  public void start() {

    Router router = Router.router(vertx);
    router.route("/health").handler(rc -> rc.response().end("OK"));
    router.route("/").handler(this::getShoppingList);

    ServiceDiscovery.create(vertx, discovery -> {
      // Get pricer-service
      Single<WebClient> s1 = HttpEndpoint.rxGetWebClient(discovery,
        rec -> rec.getName().equals("pricer-service"));

      // Get shopping-backend
      Single<WebClient> s2 = HttpEndpoint.rxGetWebClient(discovery,
              rec -> rec.getName().equals("shopping-backend"));

      // Assigned the field and start the HTTP server When both have been retrieved
      Single.zip(s1, s2, (p, s) -> {

          this.shopping = s;
          this.pricer = p;
          return vertx.createHttpServer().requestHandler(router::accept)
                  .listen(8080);
      }).subscribe();

//      vertx.createHttpServer()
//        .requestHandler(router::accept)
//        .listen(8080);

      });
  }

  private void getShoppingList(RoutingContext rc) {
    HttpServerResponse serverResponse =
      rc.response().setChunked(true);

       /*
         +--> Retrieve shopping list
           +
           +-->  for each item, call the pricer, concurrently
                    +
                    |
                    +-->  For each completed evaluation (line),
                          write it to the HTTP response
         */

    Single<JsonObject> single = shopping.get("/shopping")
      .rxSend()
      .map(HttpResponse::bodyAsJsonObject);
       
         /*
                               shopping          pricer
                               backend
                 +                +                +
                 |                |                |
                 +--------------> |                |
                 |                |                |
                 |                |                |
                 +-------------------------------> |
                 |                |                |
                 +-------------------------------> |
        write <--|                |                |
                 +-------------------------------> |
        write <--|                +                +
                 |
        write <--|
                 |
          end <--|
         */

    single.flatMapPublisher(json -> Flowable.fromIterable(json))
      .flatMapSingle(entry -> Shopping.retrievePrice(pricer, entry))
      .subscribe(json -> Shopping.writeProductLine(serverResponse, json),
              err -> rc.fail(err),
              () -> serverResponse.end());

  }

}
