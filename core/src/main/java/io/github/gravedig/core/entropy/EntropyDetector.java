package io.github.gravedig.core.entropy;

import io.github.gravedig.core.filter.FalsePositiveFilter;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.Severity;
import io.github.gravedig.core.util.FingerprintUtil;
import io.github.gravedig.core.util.Redactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntropyDetector {

    private static final Pattern CANDIDATE_TOKEN_PATTERN = Pattern.compile("(?i)(?:secret|token|password|passwd|api_key|apikey|auth|key|credential|private|bearer)\\s*[:=]\\s*[\"']?([A-Za-z0-9+/=_-]{16,128})[\"']?");

    private static final double DEFAULT_ENTROPY_THRESHOLD = 4.0;
    private static final int MIN_LENGTH = 16;

    private final double threshold;
    private final FalsePositiveFilter falsePositiveFilter;

    public EntropyDetector(double threshold, FalsePositiveFilter falsePositiveFilter) {
        this.threshold = threshold > 0 ? threshold : DEFAULT_ENTROPY_THRESHOLD;
        this.falsePositiveFilter = falsePositiveFilter != null ? falsePositiveFilter : new FalsePositiveFilter();
    }

    public List<Finding> scanLineForEntropy(String line, String filePath, int lineNumber) {
        List<Finding> findings = new ArrayList<>();
        if (line == null || line.length() < MIN_LENGTH) {
            return findings;
        }

        Matcher matcher = CANDIDATE_TOKEN_PATTERN.matcher(line);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate == null || candidate.length() < MIN_LENGTH) {
                continue;
            }

            if (falsePositiveFilter.isPlaceholder(candidate)) {
                continue;
            }

            double entropy = EntropyCalculator.calculate(candidate);
            if (entropy >= threshold && EntropyCalculator.hasSufficientDiversity(candidate, 6)) {
                int col = matcher.start(1) + 1;
                String redacted = Redactor.redact(candidate);
                String snippet = Redactor.redactSnippet(line, candidate);
                String fingerprint = FingerprintUtil.calculateFingerprint(candidate);

                Finding finding = Finding.builder()
                        .ruleId("generic-high-entropy-secret")
                        .ruleName("High Entropy Secret Assignment")
                        .severity(Severity.MEDIUM)
                        .filePath(filePath)
                        .lineNumber(lineNumber)
                        .columnNumber(col)
                        .secretRedacted(redacted)
                        .snippetRedacted(snippet)
                        .fingerprint(fingerprint)
                        .entropy(entropy)
                        .riskScore((int) Math.min(100, Math.round(entropy * 18)))
                        .presence(FindingPresence.ALIVE)
                        .status(FindingStatus.OPEN)
                        .description(String.format("Unidentified high-entropy token (entropy: %.2f) assigned with credential keyword", entropy))
                        .remediation("Review token usage and move plaintext credentials into environment variables or secrets manager.")
                        .build();

                findings.add(finding);
            }
        }

        return findings;
    }
}
