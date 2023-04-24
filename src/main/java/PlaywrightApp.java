import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PlaywrightApp {


  static ThreadLocal<Playwright> playwrightThreadLocal = ThreadLocal.withInitial(Playwright::create);

  static ThreadLocal<BrowserContext> browserContextThreadLocal = ThreadLocal.withInitial(() -> {
    BrowserContext browserContext = playwrightThreadLocal.get().chromium().launch().newContext();
    browserContext.newPage(); // hold the browser context.
    return browserContext;
  });

  static ExecutorService executorService;

  public static void main(String[] args) throws ExecutionException, InterruptedException, IOException, TimeoutException {
    ImageServer.start();
    int total = Integer.parseInt(System.getProperty("TOTAL_PDFS"));
    int poolSize = Integer.parseInt(System.getProperty("POOL_SIZE"));

    executorService = Executors.newFixedThreadPool(poolSize);

    // warm up
    printNPdfs(poolSize * 3);

    long begin = System.currentTimeMillis();
    printNPdfs(total);

    System.out.println("====================================");
    System.out.println("Total PDFs: " + total);
    System.out.println("Total Time taken: " + (System.currentTimeMillis() - begin) + " ms");
    System.out.println("CPU: " + Runtime.getRuntime().availableProcessors());
    System.out.println("Max Heap Size: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
    OperatingSystemMXBean operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    System.out.println("Total Physical Memory: " + operatingSystemMXBean.getTotalMemorySize() / 1024 / 1024 + " MB");
    System.out.println("Playwright Pool Size: " + System.getProperty("POOL_SIZE", "8"));
    System.out.println("====================================");

    executorService.shutdown();
    executorService.awaitTermination(5, TimeUnit.SECONDS);
    ImageServer.stop();
    System.exit(0);
  }

  static void printNPdfs(int n) throws ExecutionException, InterruptedException, IOException, TimeoutException {
    String prefix = String.valueOf(System.currentTimeMillis());
    Queue<Future> q = new LinkedList<>();
    for (int i = 0; i < n; i++) {
      Path outputPath = Paths.get(prefix + "-" + i + ".pdf");
      Future<?> future = executorService.submit(() -> {
        printPdf(outputPath);
      });
      q.offer(future);
    }

    while (!q.isEmpty()) {
      q.poll().get();
    }
  }

  static void printPdf(Path outputPath) {
    try (Page page = browserContextThreadLocal.get().newPage()) {
      Path path = Paths.get("src/main/resources/test_print_pdf_body.html");
      page.navigate("file://" + path.toAbsolutePath());
      page.pdf(new Page.PdfOptions()
          .setMargin(new Margin()
              .setTop("2cm").setBottom("1cm")
              .setLeft("1cm").setRight("1cm"))
          .setFormat("A4")
          .setDisplayHeaderFooter(true)
          .setPath(outputPath)
          .setFooterTemplate(Files.readString(Paths.get("src/main/resources/test_print_pdf_footer.html")))
          .setHeaderTemplate(Files.readString(Paths.get("src/main/resources/test_print_pdf_header.html"))));
    } catch (Throwable t){
      throw new RuntimeException(t);
    } finally {
      browserContextThreadLocal.get().clearCookies();
    }
  }

}
