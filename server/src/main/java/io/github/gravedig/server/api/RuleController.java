package io.github.gravedig.server.api;

import io.github.gravedig.core.entropy.EntropyCalculator;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.util.Redactor;
import io.github.gravedig.server.model.RuleTestRequest;
import io.github.gravedig.server.model.RuleTestResponse;
import io.github.gravedig.server.service.ScanService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleController {

    private final ScanService scanService;

    public RuleController(ScanService scanService) {
        this.scanService = scanService;
    }

    public void listRules(Context ctx) {
        ctx.json(scanService.getRules());
    }

    public void testRule(Context ctx) {
        try {
            RuleTestRequest req = ctx.bodyAsClass(RuleTestRequest.class);
            if (req.getSample() == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(RuleTestResponse.error("Sample text must not be null"));
                return;
            }

            Pattern pattern;
            if (req.getPattern() != null && !req.getPattern().isBlank()) {
                pattern = Pattern.compile(req.getPattern());
            } else if (req.getRuleId() != null && !req.getRuleId().isBlank()) {
                Optional<Rule> ruleOpt = scanService.getRules().stream()
                        .filter(r -> r.getId().equalsIgnoreCase(req.getRuleId()))
                        .findFirst();
                if (ruleOpt.isEmpty()) {
                    ctx.status(HttpStatus.NOT_FOUND).json(RuleTestResponse.error("Rule not found: " + req.getRuleId()));
                    return;
                }
                pattern = ruleOpt.get().getCompiledPattern();
            } else {
                ctx.status(HttpStatus.BAD_REQUEST).json(RuleTestResponse.error("Must provide either 'pattern' or 'ruleId'"));
                return;
            }

            Matcher matcher = pattern.matcher(req.getSample());
            List<RuleTestResponse.MatchDetail> matches = new ArrayList<>();

            while (matcher.find()) {
                String matchedText;
                int start;
                int end;
                if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
                    matchedText = matcher.group(1);
                    start = matcher.start(1);
                    end = matcher.end(1);
                } else {
                    matchedText = matcher.group();
                    start = matcher.start();
                    end = matcher.end();
                }

                double entropy = EntropyCalculator.calculate(matchedText);
                String redacted = Redactor.redact(matchedText);
                matches.add(new RuleTestResponse.MatchDetail(matchedText, redacted, start, end, entropy));
            }

            ctx.json(RuleTestResponse.success(matches));

        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(RuleTestResponse.error("Regex evaluation failed: " + e.getMessage()));
        }
    }
}
