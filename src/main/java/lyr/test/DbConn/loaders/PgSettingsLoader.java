package lyr.test.DbConn.loaders;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.client.SSLMode;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

public abstract class PgSettingsLoader {
    
    PgSettings settings = new PgSettings("","", "127.0.0.1", "", 5432, new HashMap<>(), SSLMode.REQUIRE);

    public abstract Mono<PgSettingsLoader> load(String file);

    public PgSettings getSettings(){
        return settings;
    };
    
    public PostgresqlConnectionConfiguration.Builder applyTo(PostgresqlConnectionConfiguration.Builder builder){
        return builder.host(settings.host)
            .port(settings.port)  // optional, defaults to 5432
            .username(settings.user)
            .password(settings.pass)
            .database(settings.dbName)  // optional
            .sslMode(settings.sslMode)
            .options(settings.options);
    }

    public PostgresqlConnectionConfiguration buildDefault(){
        return applyTo(PostgresqlConnectionConfiguration.builder()).build();
    }
    
    public PgSettingsLoader setUser(String user) {
        settings.user = user;
        return this;
    }

    public PgSettingsLoader setPass(String pass) {
        settings.pass = pass;
        return this;
    }

    public PgSettingsLoader setHost(String host) {
        settings.host = host;
        return this;
    }

    public PgSettingsLoader setDbName(String dbName) {
        settings.dbName = dbName;
        return this;
    }

    public PgSettingsLoader setPort(int port) {
        settings.port = port;
        return this;
    }

    public PgSettingsLoader setSslMode(SSLMode sslMode) {
        settings.sslMode = sslMode;
        return this;
    }
    
    public PgSettingsLoader setOptions(Map<String, String> options){
        settings.options = options;
        return this;
    }

    public PgSettingsLoader putOption(String key, String value){
        settings.options.put(key,value);
        return this;
    }

    public Map<String, String> getOptions(){
        return settings.options;
    }
    
    public static final class PgSettings {
        public String user, host, dbName;
        String pass;
        public int port;
        public Map<String, String> options;
        public SSLMode sslMode;

        PgSettings(String user, String pass, String host, String dbName, int port, Map<String, String> options, SSLMode sslMode) {
            this.user = user;
            this.pass = pass;
            this.host = host;
            this.dbName = dbName;
            this.port = port;
            this.options = options;
            this.sslMode = sslMode;
        }
    }
    
}
