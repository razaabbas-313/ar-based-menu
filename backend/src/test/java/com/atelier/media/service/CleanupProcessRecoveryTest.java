package com.atelier.media.service;

import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Two separate JVMs and a new, disposable file database: no Spring application startup. */
class CleanupProcessRecoveryTest {
    @TempDir Path directory;
    @Test void failedDeletionSurvivesProcessRestart() throws Exception {
        assertThat(run("fail")).isZero(); assertThat(run("retry")).isZero();
    }
    @Test void processDeathAfterRemoteAckCanBeReplayedIdempotently() throws Exception {
        assertThat(run("crash")).isEqualTo(17); assertThat(run("retry")).isZero();
        assertThat(Files.readAllLines(directory.resolve("acknowledged.txt"))).hasSize(2);
    }
    private int run(String mode) throws Exception {
        Path args=directory.resolve(mode+".args");
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Files.writeString(args,"-cp\n\""+classpath.replace("\\","/")+"\"\n"+Probe.class.getName()+"\n\""+directory.toAbsolutePath().toString().replace("\\","/")+"\"\n"+mode+"\n");
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"@"+args)
                .redirectErrorStream(true).redirectOutput(directory.resolve(mode+".log").toFile()).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Recovery probe timed out");}
        return process.exitValue();
    }
    public static class Probe {
        public static void main(String[] args) throws Exception {
            Path dir=Path.of(args[0]); String mode=args[1];
            var ds=new DriverManagerDataSource("jdbc:h2:file:"+dir.resolve("cleanup").toString().replace("\\","/")+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE","sa","");
            Flyway.configure().dataSource(ds).locations("classpath:db/migration/h2").load().migrate();
            var db=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
            S3Storage storage=mock(S3Storage.class);
            when(storage.bucket()).thenReturn("atelier-test-bucket");when(storage.tenantPrefix("restart")).thenReturn("restaurant-atelier/restart/");when(storage.privateTenantPrefix("restart")).thenReturn("restaurant-atelier-private/restart/");
            var worker=new DurableCleanup(db,tx,storage,TestFixtures.atelierProperties());
            String key="restaurant-atelier/restart/image.webp";
            if(!mode.equals("retry")) {
                db.update("insert into restaurants(name,slug,owner_name,email,password_hash,created_at) values('Restart','restart','Owner','restart@example.test','test-placeholder',CURRENT_TIMESTAMP)");
                long tenant=db.queryForObject("select id from restaurants where slug='restart'",Long.class);
                worker.record(tenant,"atelier-test-bucket",key,"PROCESS_TEST");
            } else {
                String state=db.queryForObject("select state from s3_cleanup_tasks",String.class);
                if(!Set.of("RETRY","LEASED").contains(state)) throw new AssertionError("Committed failure/lease was lost");
            }
            db.update("update s3_cleanup_tasks set available_at=TIMESTAMP '1970-01-01 00:00:00',lease_until=TIMESTAMP '1970-01-01 00:00:00'");
            when(storage.deleteKeys(anyString(),anyCollection())).thenAnswer(i->{
                if(mode.equals("fail"))return new S3DeletionResult(Set.of(),Map.of(),Set.of(key),true);
                Files.writeString(dir.resolve("acknowledged.txt"),"synthetic acknowledgement\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                if(mode.equals("crash"))Runtime.getRuntime().halt(17);
                return new S3DeletionResult(Set.of(key),Map.of(),Set.of(),true);
            });
            worker.runBatch();
            String state=db.queryForObject("select state from s3_cleanup_tasks",String.class);
            if(!state.equals(mode.equals("fail")?"RETRY":"DONE"))throw new AssertionError("Unexpected settlement state: "+state);
        }
    }
}
