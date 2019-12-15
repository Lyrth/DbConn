package lyr.test.DbConn;

import io.r2dbc.client.Handle;
import io.r2dbc.client.R2dbc;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import lyr.test.DbConn.loaders.HerokuPgSettingsLoader;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        System.setProperty("log4j.skipJansi","false");  // Color support for logging.
        System.out.println("Starting...");
        Log.log("> Starting...");
        new Main();
    }

    private Main(){
        Log.log("> Reading database credentials...");

        PostgresqlConnectionConfiguration configuration = new HerokuPgSettingsLoader()
            .putOption("lock_timeout", "2s")
            .putOption("statement_timeout", "5s")
            .load("herokucred.txt")
            .block()
            .buildDefault();

        Log.log("> Initializing connection...");
        R2dbc rdbc = new R2dbc(new PostgresqlConnectionFactory(configuration));

        Optional<Boolean> error = rdbc.inTransaction(Handle::beginTransaction)
            .singleOrEmpty()
            .thenReturn(false)
            .timeout(Duration.ofSeconds(15))
            .doOnError(Log::logError)
            .onErrorReturn(true)
            .blockOptional();
        if (!error.isPresent() || error.get()) {
            Log.logError(">>> An error has occured! Quitting...");
            return;
        } // exit if fail

        Log.log("> Connect successful. Testing");

        /*
CREATE TABLE __test_chars (
    charid  TEXT NOT NULL PRIMARY KEY,
    userid  BIGINT NOT NULL,
    name    VARCHAR(32),
    gender  VARCHAR(20),
    age     VARCHAR(20),
    bio     VARCHAR(140),
    avatar  TEXT,
    longbio TEXT,
    weight  VARCHAR(20),
    height  VARCHAR(20)
);
         */
        String sql1 =
            "CREATE TABLE __test_chars (\n" +
            "    charid  TEXT NOT NULL PRIMARY KEY,\n" +
            "    userid  BIGINT NOT NULL,\n" +
            "    name    VARCHAR(32),\n" +
            "    gender  VARCHAR(20),\n" +
            "    age     VARCHAR(20),\n" +
            "    bio     VARCHAR(140),\n" +
            "    avatar  TEXT,\n" +
            "    longbio TEXT,\n" +
            "    weight  VARCHAR(20),\n" +
            "    height  VARCHAR(20)\n" +
            ");";

        /*
INSERT INTO __test_chars VALUES (
    'lyrth',
    368727799189733376,
    'Lyrthras',
    'Unknown',
    'Around ?? years.',
    'Is a derg.',
    '<avatarUrl>',
    '<longBioUrl>',
    'Quite heavy!',
    'Smol.'
);
         */
        String sql2 =
            "INSERT INTO __test_chars VALUES (\n" +
            "    'lyrth',\n" +
            "    368727799189733376,\n" +
            "    'Lyrthras',\n" +
            "    'Unknown',\n" +
            "    'Around ?? years.',\n" +
            "    'Is a derg.',\n" +
            "    '<avatarUrl>',\n" +
            "    '<longBioUrl>',\n" +
            "    'Quite heavy!',\n" +
            "    'Smol.'\n" +
            ");";

        /*
SELECT name FROM __test_chars;
         */
        String sql3 = "SELECT name FROM __test_chars;";


        /*
DROP TABLE __test_chars;
         */

        Mono.just(sql1)
            .doOnNext(this::logSQL)
            .flatMapMany(sql -> rdbc.inTransaction(h -> h.execute(sql)))
            .then(Mono.just(sql2))
            .doOnNext(this::logSQL)
            .flatMapMany(sql -> rdbc.inTransaction(h -> h.execute(sql)))
            .then(Mono.just(sql3))
            .doOnNext(this::logSQL)
            .flatMapMany(sql -> rdbc.inTransaction(h ->
                    h.select(sql)
                        .mapResult(res -> res.map((row, rowMetadata) -> row.get("name", String.class)))))
            .doOnNext(Log::log)
            .doOnError(Log::logError)
            .blockLast();

        Log.log("> Done.");
    }

    private void logSQL(String query){
        StringBuilder str = new StringBuilder("> Executing SQL...:");
        final String[] q = query.split("[\r\n]+");
        for (String l : q){
            str.append("\n\t\t")
                .append(l);
        }
        Log.log(str);
    }
}
