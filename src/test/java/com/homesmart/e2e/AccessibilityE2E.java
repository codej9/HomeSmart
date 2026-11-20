package com.homesmart.e2e;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs axe-core on each main page and writes every violation to target/e2e-reports.
 * Fails only on serious or critical violations.
 */
final class AccessibilityE2E extends E2EBase {

    private static final Set<String> BLOCKING_IMPACTS = Set.of("serious", "critical");
    private static final Path REPORT = Path.of("target", "e2e-reports", "axe-summary.txt");

    @Test
    void pagesHaveNoSeriousOrCriticalViolations() throws IOException {
        List<String> report = new ArrayList<>();
        List<String> blocking = new ArrayList<>();

        open("/login");
        scan("/login", report, blocking);

        signIn();
        for (String path : List.of("/devices", "/activity", "/schedules")) {
            open(path);
            scan(path, report, blocking);
        }

        Files.createDirectories(REPORT.getParent());
        Files.write(REPORT, report);
        assertTrue(blocking.isEmpty(), () -> "Serious or critical violations: " + blocking);
    }

    private void scan(String path, List<String> report, List<String> blocking) {
        Results results = new AxeBuilder().analyze(driver);
        if (results.isErrored()) {
            throw new IllegalStateException("axe-core failed on " + path + ": " + results.getErrorMessage());
        }
        report.add(path + " violations=" + results.getViolations().size()
                + " passes=" + results.getPasses().size());
        for (Rule rule : results.getViolations()) {
            String line = path + " " + rule.getImpact() + " " + rule.getId() + " nodes=" + rule.getNodes().size();
            report.add(line);
            if (BLOCKING_IMPACTS.contains(rule.getImpact())) {
                blocking.add(line);
            }
        }
    }
}
