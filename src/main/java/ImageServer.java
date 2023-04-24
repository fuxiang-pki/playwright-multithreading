import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageServer {

  static HttpServer httpServer;

  static ExecutorService executorService;

  public static void main(String[] args) throws Exception {
    executorService = Executors.newCachedThreadPool();
    executorService.submit(() -> {
      try {
        start();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).get();
  }

  public static void start() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(48080), 0);

    httpServer.createContext("/stop")
            .setHandler(exchange -> {
              exchange.sendResponseHeaders(200, 0);
              exchange.close();
              try {
                stop();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            });

    httpServer.createContext("/")
        .setHandler(exchange -> {
          byte[] bytes = Files.readAllBytes(Paths.get("src/main/resources/untitled.png"));
          exchange.sendResponseHeaders(200, bytes.length);
          OutputStream responseBody = exchange.getResponseBody();
          responseBody.write(bytes);
          responseBody.flush();
          exchange.close();
        });

    httpServer.start();
  }

  public static void stop() throws InterruptedException {
    httpServer.stop(0);
    if (executorService != null) {
      executorService.shutdown();
      executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

}
