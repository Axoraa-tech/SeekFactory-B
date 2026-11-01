package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Real SDK HTTP against a disposable, loopback-only S3 protocol emulator; never a cloud bucket. */
@EnabledIfSystemProperty(named="seekfactory.s3Local",matches="true")
class LocalS3ProtocolTest {
    @Test
    void realVideoRangesCacheCorsAndExpiringLinks(@TempDir Path temp) throws Exception {
        String module=System.getProperty("seekfactory.s3Module");
        String ffmpeg=System.getenv("FFMPEG_PATH");
        assertThat(module).isNotBlank(); assertThat(ffmpeg).isNotBlank();
        var launch=new ProcessBuilder(System.getProperty("seekfactory.node","node"),
                Path.of("scripts/local-s3-emulator.cjs").toAbsolutePath().toString(),temp.resolve("bucket").toString());
        launch.environment().clear();
        launch.environment().put("SystemRoot",System.getenv().getOrDefault("SystemRoot","C:\\Windows"));
        launch.environment().put("S3RVER_MODULE",module);
        launch.redirectError(temp.resolve("s3-errors.log").toFile());
        Process emulator=launch.start();
        try {
            String ready=new java.io.BufferedReader(new java.io.InputStreamReader(emulator.getInputStream())).readLine();
            assertThat(ready).contains("port");
            int port=Integer.parseInt(ready.replaceAll(".*\\\"port\\\":(\\d+).*","$1"));
            String endpoint="http://127.0.0.1:"+port;
            Path source=temp.resolve("synthetic.mp4");
            Process generate=new ProcessBuilder(ffmpeg,"-hide_banner","-loglevel","error","-y","-f","lavfi","-i",
                    "testsrc2=size=1280x720:rate=30:duration=2","-c:v","libx264","-preset","ultrafast","-crf","8","-movflags","+faststart",source.toString())
                    .redirectErrorStream(true).redirectOutput(temp.resolve("ffmpeg.log").toFile()).start();
            assertThat(generate.waitFor(60,TimeUnit.SECONDS)).isTrue(); assertThat(generate.exitValue()).isZero();
            var storage=new S3MediaStorageService(endpoint,"us-east-1","synthetic-media","S3RVER","S3RVER",true,"",
                    Duration.ofSeconds(2),temp.resolve("work").toString(),DataSize.ofMegabytes(20),DataSize.ofMegabytes(100),
                    DataSize.ofMegabytes(50),2048,.85f,ffmpeg,23,"veryfast");
            try {
                var upload=storage.store(new MockMultipartFile("file","synthetic.mp4","video/mp4",Files.readAllBytes(source)),"video");
                String key=upload.getUrl().substring(upload.getUrl().lastIndexOf('/')+1);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
                while(storage.isProcessing(key) && System.nanoTime()<deadline) Thread.sleep(100);
                assertThat(storage.isProcessing(key)).isFalse();
                HttpClient http=HttpClient.newHttpClient();
                URI url=URI.create(storage.publicUrl(key).orElseThrow());
                var range=http.send(HttpRequest.newBuilder(url).header("Range","bytes=0-99")
                        .header("Origin","http://127.0.0.1:3000").build(),HttpResponse.BodyHandlers.ofByteArray());
                assertThat(range.statusCode()).isEqualTo(206);
                assertThat(range.body()).hasSize(100);
                assertThat(range.headers().firstValue("Content-Range").orElse("")).startsWith("bytes 0-99/");
                assertThat(range.headers().firstValue("Cache-Control").orElse("")).contains("immutable");
                assertThat(range.headers().firstValue("Access-Control-Allow-Origin").orElse("")).isEqualTo("http://127.0.0.1:3000");
                var blocked=http.send(HttpRequest.newBuilder(url).header("Origin","https://blocked.invalid").build(),HttpResponse.BodyHandlers.discarding());
                assertThat(blocked.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
                var options=http.send(HttpRequest.newBuilder(url).method("OPTIONS",HttpRequest.BodyPublishers.noBody())
                        .header("Origin","http://127.0.0.1:3000").header("Access-Control-Request-Method","GET")
                        .header("Access-Control-Request-Headers","range").build(),HttpResponse.BodyHandlers.discarding());
                assertThat(options.statusCode()).isBetween(200,299);
                assertThat(options.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase()).contains("range");
                String privateKey=storage.storePrivate("test-conversation",new MockMultipartFile("file","synthetic.pdf","application/pdf","%PDF-1.4 synthetic".getBytes()));
                URI privateUrl=URI.create(storage.privateUrl("test-conversation",privateKey).orElseThrow());
                assertThat(http.send(HttpRequest.newBuilder(privateUrl).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
                Thread.sleep(3500);
                assertThat(http.send(HttpRequest.newBuilder(privateUrl).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
                URI renewed=URI.create(storage.privateUrl("test-conversation",privateKey).orElseThrow());
                assertThat(http.send(HttpRequest.newBuilder(renewed).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
                Files.writeString(Path.of("target/local-s3-report.txt"),"Loopback S3rver: real H264 upload/compression, SDK PUT/GET, range206/100 bytes, immutable cache, allowed/blocked origins, Range preflight, signed-link expiry403 and renewal200 passed.\nS3rver does not verify SigV4 signatures or enforce production bucket ACLs; no R2/CDN/provider security certification.\n");
            } finally {storage.shutdown();}
        } finally {emulator.destroy(); if(!emulator.waitFor(5,TimeUnit.SECONDS)) emulator.destroyForcibly();}
    }
}
