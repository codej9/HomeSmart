package com.homesmart.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Browser setup shared by the end-to-end classes. Each test gets a new browser,
 * so no session carries over between tests.
 */
abstract class E2EBase {

    static final String BASE_URL = requiredProperty("e2e.baseUrl");
    static final String USER = requiredProperty("e2e.user");
    static final String PASSWORD = requiredProperty("e2e.password");
    static final String SETUP_CODE = System.getProperty("e2e.setupCode", "");

    private static final Path SCREENSHOT_DIR = Path.of("target", "e2e-screenshots");
    private static final Duration WAIT = Duration.ofSeconds(10);

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeAll
    static void createFirstAccount() {
        if (SETUP_CODE.isBlank()) {
            return;
        }
        WebDriver setupDriver = newDriver();
        try {
            setupDriver.get(BASE_URL + "/login");
            if (!setupDriver.getCurrentUrl().endsWith("/setup")) {
                return;
            }
            setupDriver.findElement(By.name("code")).sendKeys(SETUP_CODE);
            setupDriver.findElement(By.name("username")).sendKeys(USER);
            setupDriver.findElement(By.name("password")).sendKeys(PASSWORD);
            submitFormOf(setupDriver.findElement(By.name("password")));
            new WebDriverWait(setupDriver, WAIT).until(d -> d.getCurrentUrl().endsWith("/login"));
        } finally {
            setupDriver.quit();
        }
    }

    @BeforeEach
    void startBrowser() {
        driver = newDriver();
        wait = new WebDriverWait(driver, WAIT);
    }

    @AfterEach
    void stopBrowser() {
        driver.quit();
    }

    protected void signIn() {
        open("/login");
        typeInto(By.name("username"), USER);
        typeInto(By.name("password"), PASSWORD);
        submitFormOf(driver.findElement(By.name("password")));
        wait.until(d -> d.getCurrentUrl().endsWith("/devices"));
    }

    protected void open(String path) {
        driver.get(BASE_URL + path);
    }

    protected Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    protected WebElement find(String cssSelector) {
        return driver.findElement(By.cssSelector(cssSelector));
    }

    protected void typeInto(By locator, String text) {
        WebElement field = driver.findElement(locator);
        field.clear();
        field.sendKeys(text);
    }

    protected String bodyText() {
        return driver.findElement(By.tagName("body")).getText();
    }

    protected void waitForText(String text) {
        wait.until(d -> bodyText().contains(text));
    }

    protected void snapshot(String name) {
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.write(SCREENSHOT_DIR.resolve(name + ".png"),
                    ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void submitFormOf(WebElement field) {
        WebElement form = field.findElement(By.xpath("ancestor::form[1]"));
        form.findElement(By.cssSelector("button[type='submit'], button:not([type]), input[type='submit']")).click();
    }

    static WebDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        if (Boolean.parseBoolean(System.getProperty("e2e.headless", "true"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--disable-dev-shm-usage", "--window-size=1280,900");
        if (Boolean.parseBoolean(System.getProperty("e2e.noSandbox", "false"))) {
            options.addArguments("--no-sandbox");
        }
        String binary = System.getProperty("e2e.chromeBinary", "");
        if (!binary.isBlank()) {
            options.setBinary(binary);
        }
        return new ChromeDriver(options);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name, "");
        if (value.isBlank()) {
            throw new IllegalStateException("Set -D" + name + " to run the end-to-end tests");
        }
        return value;
    }
}
