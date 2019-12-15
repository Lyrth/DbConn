package lyr.test.DbConn.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lyr.test.DbConn.Log;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

public class HerokuPgSettingsLoader extends PgSettingsLoader {

    public static final String ETAG_STORE = "heroku_etag.txt";
    public static final String DB_CRED_STORE = "heroku_dbcred.txt";

    public static final String HEROKU_API_CONFIG_VARS = "https://api.heroku.com/apps/%s/config-vars";
    public static final String HEROKU_ACCEPT_HEADER = "application/vnd.heroku+json; version=3";
    public static final String USER_AGENT = "Test (Test 1.0)";
    public static final String DATABASE_URL_VAR = "DATABASE_URL";

    private static Gson gson = new GsonBuilder()/*.setPrettyPrinting()*/.disableHtmlEscaping().create();

    // File: <app name> <Bearer/Basic> <api key/OAuth creds>
    public Mono<PgSettingsLoader> load(final String herokuCreds) {
        Mono<String> etag = Mono.fromCallable(() -> new String(Files.readAllBytes(Paths.get(ETAG_STORE))))
            .flatMap(s -> s.matches("\".+?\"") ? Mono.just(s) : Mono.error(new IOException("File has invalid ETag!")))
            .doOnError(Log::logError)
            .onErrorResume(t -> Mono.fromCallable(() -> new File(ETAG_STORE).createNewFile()).thenReturn(""))
            .doOnError(Log::logError)
            .onErrorReturn("");
        Mono<HerokuCreds> creds = Mono.fromCallable(() -> Files.readAllBytes(Paths.get(herokuCreds)))
            .map(f -> {
                String[] s = new String(f).split("\\s+");
                return new HerokuCreds(s[0], s[1], s[2]);
            });
        HttpClient client = HttpClient.create()
            .headersWhen(h ->
                creds.map(c ->
                    h.set("Accept", HEROKU_ACCEPT_HEADER)
                        .set("User-Agent", USER_AGENT)
                        .set("Authorization", c.getAuth()))
                .zipWith(etag, (h1,etag1) ->
                    h1.set("If-None-Match",etag))
            );

        return creds
            .map(HerokuCreds::getAppName)
            .map(n -> client.baseUrl(String.format(HEROKU_API_CONFIG_VARS,n)))
            .flatMap(c -> c.get().responseSingle( (r,b) -> {
                if (r.status().code() == 200) return b.asString().filter(s -> !s.isEmpty()).zipWith(Mono.just(r.responseHeaders()));  // Tuple2<json, headers>
                else if (r.status().code() == 304) return Mono.empty();
                else return Mono.error(
                    new IllegalStateException("Network error: " + r.status().code() + ": " + r.status().reasonPhrase())
                    );
            }).retry(3, t -> t.toString().contains("java.net")))
            .flatMap(s -> Mono.zip(
                Mono.just(gson.<HashMap<String,String>>fromJson(s.getT1(), HashMap.class).get(DATABASE_URL_VAR)),
                Mono.just(s.getT2().get("Etag"))
            ))  // Tuple2<dbURL, etag>
            .flatMap(s -> Mono.when(
                Mono.fromCallable(() -> {
                    FileWriter f = new FileWriter(DB_CRED_STORE, false);
                    f.write(getCredsFromUrl(s.getT1())); f.close();
                    return (Void) null;
                }),
                Mono.fromCallable(() -> {
                    FileWriter f = new FileWriter(ETAG_STORE, false);
                    f.write(s.getT2()); f.close();
                    return (Void) null;
                })
            ))
            .then(new FilePgSettingsLoader().load(DB_CRED_STORE));
    }

    // postgres://username:password@host:port/database
    // to: <user> <password> <host> <port> <database name>
    private String getCredsFromUrl(String url){
        return url.replaceFirst("postgres://([^:]+):([^@]+)@([^:]+):(\\d+)/(.+)$","$1 $2 $3 $4 $5");
    }

    private static class HerokuCreds {
        private String appName, authType, token;

        private HerokuCreds(String appName, String authType, String token) {
            this.appName = appName;
            this.authType = authType;
            this.token = token;
        }

        public String getAppName() {
            return appName;
        }

        public String getAuth() {
            return authType + " " + token;
        }
    }
}
