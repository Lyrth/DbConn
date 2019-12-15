package lyr.test.DbConn.loaders;

import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Paths;

public class FilePgSettingsLoader extends PgSettingsLoader {

    // File: <user> <password> <host> <port> <database name>
    public Mono<PgSettingsLoader> load(final String fileName) {
        return Mono.fromCallable(() -> Files.readAllBytes(Paths.get(fileName)))
            .map(f -> {
                String[] s = new String(f).split("\\s+");
                return setUser(s[0])
                    .setPass(s[1])
                    .setHost(s[2])
                    .setPort(Integer.parseInt(s[3]))
                    .setDbName(s[4]);
            });
    }

}
