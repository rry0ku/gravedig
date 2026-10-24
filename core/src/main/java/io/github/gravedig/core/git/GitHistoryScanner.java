package io.github.gravedig.core.git;

import io.github.gravedig.core.baseline.BaselineManager;
import io.github.gravedig.core.config.GravedigConfig;
import io.github.gravedig.core.entropy.EntropyCalculator;
import io.github.gravedig.core.entropy.EntropyDetector;
import io.github.gravedig.core.filter.FalsePositiveFilter;
import io.github.gravedig.core.model.ExposureWindow;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.core.model.Occurrence;
import io.github.gravedig.core.model.RiskBreakdown;
import io.github.gravedig.core.model.Rule;
import io.github.gravedig.core.model.ScanResult;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.core.model.Severity;
import io.github.gravedig.core.util.FingerprintUtil;
import io.github.gravedig.core.util.Redactor;
import io.github.gravedig.core.validator.OfflineValidator;
import io.github.gravedig.core.validator.ValidationResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

public class GitHistoryScanner {
    private static final Logger log = LoggerFactory.getLogger(GitHistoryScanner.class);
    private static final String INLINE_IGNORE = "gravedig:ignore";

    private final List<Rule> rules;
    private final GravedigConfig config;
    private final BaselineManager baselineManager;
    private final FalsePositiveFilter falsePositiveFilter;
    private final EntropyDetector entropyDetector;
    private final int threadCount;

    public GitHistoryScanner(List<Rule> rules, GravedigConfig config, BaselineManager baselineManager, int threads) {
        this.config = config != null ? config : GravedigConfig.defaultConfiguration();
        this.baselineManager = baselineManager != null ? baselineManager : new BaselineManager();
        this.threadCount = threads > 0 ? threads : Math.max(1, Runtime.getRuntime().availableProcessors());

        List<Rule> mergedRules = new ArrayList<>(rules != null ? rules : List.of());
        if (this.config.getRules() != null) {
            mergedRules.addAll(this.config.getRules());
        }
        this.rules = List.copyOf(mergedRules);

        this.falsePositiveFilter = new FalsePositiveFilter(
                this.config.getIgnorePlaceholders(),
                this.config.getIgnorePaths()
        );

        double entropyThreshold = this.config.getEntropy() != null ? this.config.getEntropy().getThreshold() : 4.0;
        this.entropyDetector = new EntropyDetector(entropyThreshold, this.falsePositiveFilter);
    }

    public ScanResult scanRepository(Path repoPath) throws IOException {
        long startTime = System.currentTimeMillis();
        Path normalizedPath = repoPath.toAbsolutePath().normalize();

        File gitDir = findGitDir(normalizedPath.toFile());
        if (gitDir == null) {
            throw new IllegalArgumentException("Not a valid git repository: " + repoPath);
        }

        Repository repository = new RepositoryBuilder().setGitDir(gitDir).readEnvironment().findGitDir().build();

        try (repository; RevWalk revWalk = new RevWalk(repository)) {
            // 1. Discover all branches and tags
            Map<String, ObjectId> branchHeads = new HashMap<>();
            Map<String, ObjectId> tagHeads = new HashMap<>();

            for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
                branchHeads.put(ref.getName().substring(Constants.R_HEADS.length()), ref.getObjectId());
            }
            for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES)) {
                branchHeads.put(ref.getName().substring(Constants.R_REMOTES.length()), ref.getObjectId());
            }
            for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)) {
                tagHeads.put(ref.getName().substring(Constants.R_TAGS.length()), ref.getObjectId());
            }

            // 2. Identify secrets present in HEAD (ALIVE determination)
            Set<String> aliveFingerprints = scanHeadSecrets(repository);

            // 3. Mark start points for full history traversal
            Set<ObjectId> startPoints = new HashSet<>();
            startPoints.addAll(branchHeads.values());
            startPoints.addAll(tagHeads.values());

            ObjectId headId = repository.resolve(Constants.HEAD);
            if (headId != null) {
                startPoints.add(headId);
            }

            List<RevCommit> allCommits = new ArrayList<>();
            for (ObjectId id : startPoints) {
                try {
                    RevCommit commit = revWalk.parseCommit(id);
                    revWalk.markStart(commit);
                } catch (Exception e) {
                    log.debug("Skipping unparseable ref {}: {}", id.name(), e.getMessage());
                }
            }

            for (RevCommit commit : revWalk) {
                allCommits.add(commit);
            }

            // 4. Parallel commit diff scanning
            AtomicLong totalCommitsScanned = new AtomicLong(0);
            AtomicLong totalFilesScanned = new AtomicLong(0);
            List<RawGitFinding> rawFindings = Collections.synchronizedList(new ArrayList<>());

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (RevCommit commit : allCommits) {
                futures.add(executor.submit(() -> {
                    scanCommitDiff(repository, commit, rawFindings, totalFilesScanned);
                    totalCommitsScanned.incrementAndGet();
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Error during parallel commit scanning", e);
                }
            }
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            // 5. Group secrets by fingerprint and compute exposure windows
            List<Finding> groupedFindings = groupAndComputeExposure(
                    repository, rawFindings, aliveFingerprints, branchHeads, tagHeads);

            long durationMs = Math.max(1, System.currentTimeMillis() - startTime);

            int aliveCount = 0;
            int buriedCount = 0;
            Map<Severity, Integer> severityCounts = new EnumMap<>(Severity.class);

            for (Finding f : groupedFindings) {
                if (f.getPresence() == FindingPresence.ALIVE) {
                    aliveCount++;
                } else {
                    buriedCount++;
                }
                severityCounts.merge(f.getSeverity(), 1, Integer::sum);
            }

            ScanSummary summary = new ScanSummary(
                    totalFilesScanned.get(),
                    totalCommitsScanned.get(),
                    durationMs,
                    groupedFindings.size(),
                    aliveCount,
                    buriedCount,
                    severityCounts
            );

            return new ScanResult(
                    null,
                    normalizedPath.toString(),
                    groupedFindings,
                    summary,
                    Instant.now()
            );
        }
    }

    private Set<String> scanHeadSecrets(Repository repo) {
        Set<String> headFingerprints = new HashSet<>();
        try {
            ObjectId head = repo.resolve(Constants.HEAD);
            if (head == null) return headFingerprints;

            try (RevWalk revWalk = new RevWalk(repo)) {
                RevCommit commit = revWalk.parseCommit(head);
                try (TreeWalk treeWalk = new TreeWalk(repo)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);

                    while (treeWalk.next()) {
                        String path = treeWalk.getPathString();
                        if (falsePositiveFilter.isIgnoredFile(Path.of(path))) continue;

                        byte[] data = repo.open(treeWalk.getObjectId(0)).getBytes();
                        String content = new String(data, StandardCharsets.UTF_8);
                        String[] lines = content.split("\\R");
                        for (String line : lines) {
                            if (line.contains(INLINE_IGNORE)) continue;
                            for (Rule rule : rules) {
                                if (!rule.matchesFastCheck(line)) continue;
                                Matcher m = rule.getCompiledPattern().matcher(line);
                                while (m.find()) {
                                    String secret = (m.groupCount() >= 1 && m.group(1) != null) ? m.group(1) : m.group();
                                    if (!falsePositiveFilter.isPlaceholder(secret)) {
                                        ValidationResult vr = OfflineValidator.validate(rule.getId(), secret);
                                        if (!vr.shouldDrop()) {
                                            headFingerprints.add(FingerprintUtil.calculateFingerprint(secret));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not inspect HEAD tree: {}", e.getMessage());
        }
        return headFingerprints;
    }

    private void scanCommitDiff(
            Repository repo,
            RevCommit commit,
            List<RawGitFinding> findings,
            AtomicLong totalFilesScanned) {

        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repo.newObjectReader()) {

            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            AbstractTreeIterator oldTreeIter;
            if (commit.getParentCount() > 0) {
                RevCommit parent = commit.getParent(0);
                try (RevWalk rw = new RevWalk(repo)) {
                    rw.parseHeaders(parent);
                    oldTreeIter = new CanonicalTreeParser(null, reader, parent.getTree());
                }
            } else {
                oldTreeIter = new EmptyTreeIterator();
            }

            AbstractTreeIterator newTreeIter = new CanonicalTreeParser(null, reader, commit.getTree());
            List<DiffEntry> diffs = df.scan(oldTreeIter, newTreeIter);

            for (DiffEntry diff : diffs) {
                String path = diff.getNewPath();
                if (DiffEntry.DEV_NULL.equals(path)) continue;
                if (falsePositiveFilter.isIgnoredFile(Path.of(path))) continue;

                totalFilesScanned.incrementAndGet();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DiffFormatter fileDf = new DiffFormatter(out)) {
                    fileDf.setRepository(repo);
                    fileDf.setDiffComparator(RawTextComparator.DEFAULT);
                    fileDf.setDetectRenames(true);
                    fileDf.format(diff);
                }

                String patch = out.toString(StandardCharsets.UTF_8);
                String[] lines = patch.split("\\R");
                int lineNum = 1;

                for (String line : lines) {
                    if (line.startsWith("@@")) {
                        int plusIdx = line.indexOf('+');
                        if (plusIdx > 0) {
                            int commaOrSpace = line.indexOf(',', plusIdx);
                            int spaceIdx = line.indexOf(' ', plusIdx);
                            int endIdx = (commaOrSpace > 0 && commaOrSpace < spaceIdx) ? commaOrSpace : spaceIdx;
                            if (endIdx > plusIdx) {
                                try {
                                    lineNum = Integer.parseInt(line.substring(plusIdx + 1, endIdx));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    } else if (line.startsWith("+") && !line.startsWith("+++")) {
                        String addedLine = line.substring(1);
                        if (!addedLine.contains(INLINE_IGNORE)) {
                            scanAddedLine(addedLine, path, lineNum, commit, findings);
                        }
                        lineNum++;
                    } else if (!line.startsWith("-")) {
                        lineNum++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error analyzing commit {}: {}", commit.name(), e.getMessage(), e);
        }
    }

    private void scanAddedLine(
            String line,
            String filePath,
            int lineNumber,
            RevCommit commit,
            List<RawGitFinding> findings) {

        for (Rule rule : rules) {
            if (!rule.matchesFastCheck(line)) continue;

            Matcher matcher = rule.getCompiledPattern().matcher(line);
            while (matcher.find()) {
                String rawSecret = (matcher.groupCount() >= 1 && matcher.group(1) != null) ? matcher.group(1) : matcher.group();
                if (falsePositiveFilter.isPlaceholder(rawSecret)) continue;

                ValidationResult validation = OfflineValidator.validate(rule.getId(), rawSecret);
                if (validation.shouldDrop()) continue;

                String fingerprint = FingerprintUtil.calculateFingerprint(rawSecret);
                if (config.getAllowlist().isAllowed(fingerprint, filePath, rule.getId())) continue;

                double entropy = EntropyCalculator.calculate(rawSecret);
                String redactedSecret = Redactor.redact(rawSecret);
                String snippet = Redactor.redactSnippet(line, rawSecret);

                findings.add(new RawGitFinding(
                        rule, filePath, lineNumber, matcher.start() + 1,
                        rawSecret, redactedSecret, snippet, fingerprint, entropy,
                        validation, commit
                ));
            }
        }

        // Generic entropy detection
        if (config.getEntropy() != null && config.getEntropy().isEnabled()) {
            List<Finding> entropyFindings = entropyDetector.scanLineForEntropy(line, filePath, lineNumber);
            for (Finding ef : entropyFindings) {
                findings.add(new RawGitFinding(
                        new Rule(ef.getRuleId(), ef.getRuleName(), ".*", List.of(), 4.0, ef.getSeverity(), ef.getDescription(), ef.getRemediation()),
                        filePath, lineNumber, ef.getColumnNumber(),
                        "", ef.getSecretRedacted(), ef.getSnippetRedacted(),
                        ef.getFingerprint(), ef.getEntropy(),
                        ValidationResult.valid("Entropy token", 0.7),
                        commit
                ));
            }
        }
    }

    private List<Finding> groupAndComputeExposure(
            Repository repo,
            List<RawGitFinding> rawFindings,
            Set<String> aliveFingerprints,
            Map<String, ObjectId> branchHeads,
            Map<String, ObjectId> tagHeads) {

        Map<String, List<RawGitFinding>> groupedByFingerprint = new HashMap<>();
        for (RawGitFinding r : rawFindings) {
            groupedByFingerprint.computeIfAbsent(r.fingerprint, k -> new ArrayList<>()).add(r);
        }

        List<Finding> result = new ArrayList<>();

        for (Map.Entry<String, List<RawGitFinding>> entry : groupedByFingerprint.entrySet()) {
            String fingerprint = entry.getKey();
            List<RawGitFinding> group = entry.getValue();

            // Sort occurrences by commit date ascending
            group.sort((a, b) -> Long.compare(a.commit.getCommitTime(), b.commit.getCommitTime()));

            RawGitFinding earliest = group.get(0);
            RawGitFinding latest = group.get(group.size() - 1);

            boolean isAlive = aliveFingerprints.contains(fingerprint);
            FindingPresence presence = isAlive ? FindingPresence.ALIVE : FindingPresence.BURIED;

            // Find branches and tags containing earliest commit
            List<String> containingBranches = findBranchesContaining(repo, earliest.commit, branchHeads);
            List<String> containingTags = findTagsContaining(repo, earliest.commit, tagHeads);

            Instant firstDate = Instant.ofEpochSecond(earliest.commit.getCommitTime());
            String firstAuthor = earliest.commit.getAuthorIdent().getName() + " <" + earliest.commit.getAuthorIdent().getEmailAddress() + ">";

            String removedCommit = null;
            Instant removedDate = null;
            if (!isAlive) {
                removedCommit = latest.commit.getName().substring(0, 8);
                removedDate = Instant.ofEpochSecond(latest.commit.getCommitTime());
            }

            ExposureWindow exposure = ExposureWindow.compute(
                    earliest.commit.getName().substring(0, 8),
                    firstDate,
                    firstAuthor,
                    removedCommit,
                    removedDate,
                    containingBranches,
                    containingTags,
                    isAlive
            );

            // Calculate explainable Risk Score
            RiskBreakdown riskBreakdown = RiskBreakdown.compute(
                    earliest.rule.getSeverity(),
                    presence,
                    exposure.getDurationMillis() != null ? exposure.getDurationMillis() : 0L,
                    containingBranches.size(),
                    earliest.validation.getConfidence()
            );

            // Build occurrence records
            List<Occurrence> occurrences = new ArrayList<>();
            for (RawGitFinding item : group) {
                occurrences.add(new Occurrence(
                        item.commit.getName().substring(0, 8),
                        item.commit.getAuthorIdent().getName(),
                        Instant.ofEpochSecond(item.commit.getCommitTime()),
                        item.filePath,
                        item.lineNumber,
                        item.snippetRedacted
                ));
            }

            FindingStatus status = FindingStatus.OPEN;
            Finding sampleFinding = Finding.builder()
                    .ruleId(earliest.rule.getId())
                    .ruleName(earliest.rule.getName())
                    .secretRedacted(earliest.secretRedacted)
                    .fingerprint(fingerprint)
                    .filePath(earliest.filePath)
                    .build();

            if (baselineManager.isBaselined(sampleFinding)) {
                status = FindingStatus.ACCEPTED_RISK;
            }

            Finding finalFinding = Finding.builder()
                    .ruleId(earliest.rule.getId())
                    .ruleName(earliest.rule.getName())
                    .severity(earliest.rule.getSeverity())
                    .filePath(earliest.filePath)
                    .lineNumber(earliest.lineNumber)
                    .columnNumber(earliest.columnNumber)
                    .secretRedacted(earliest.secretRedacted)
                    .snippetRedacted(earliest.snippetRedacted)
                    .fingerprint(fingerprint)
                    .entropy(earliest.entropy)
                    .validationReason(earliest.validation.getReason())
                    .riskScore(riskBreakdown.getTotalScore())
                    .occurrences(group.size())
                    .occurrencesList(occurrences)
                    .exposureWindow(exposure)
                    .riskBreakdown(riskBreakdown)
                    .presence(presence)
                    .status(status)
                    .commitHash(earliest.commit.getName().substring(0, 8))
                    .commitAuthor(firstAuthor)
                    .commitDate(firstDate)
                    .branch(containingBranches.isEmpty() ? "HEAD" : containingBranches.get(0))
                    .description(earliest.rule.getDescription())
                    .remediation(earliest.rule.getRemediation())
                    .build();

            result.add(finalFinding);
        }

        // Sort findings by risk score descending
        result.sort((a, b) -> Integer.compare(b.getRiskScore(), a.getRiskScore()));
        return result;
    }

    private List<String> findBranchesContaining(Repository repo, RevCommit target, Map<String, ObjectId> branchHeads) {
        List<String> result = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repo)) {
            for (Map.Entry<String, ObjectId> entry : branchHeads.entrySet()) {
                try {
                    RevCommit headCommit = walk.parseCommit(entry.getValue());
                    if (walk.isMergedInto(target, headCommit)) {
                        result.add(entry.getKey());
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private List<String> findTagsContaining(Repository repo, RevCommit target, Map<String, ObjectId> tagHeads) {
        List<String> result = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repo)) {
            for (Map.Entry<String, ObjectId> entry : tagHeads.entrySet()) {
                try {
                    RevCommit tagCommit = walk.parseCommit(entry.getValue());
                    if (walk.isMergedInto(target, tagCommit)) {
                        result.add(entry.getKey());
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private static File findGitDir(File start) {
        File cur = start;
        while (cur != null) {
            File dotGit = new File(cur, ".git");
            if (dotGit.exists()) {
                return dotGit;
            }
            if (new File(cur, "HEAD").exists() && new File(cur, "objects").exists()) {
                return cur;
            }
            cur = cur.getParentFile();
        }
        return null;
    }

    private static class RawGitFinding {
        final Rule rule;
        final String filePath;
        final int lineNumber;
        final int columnNumber;
        final String rawSecret;
        final String secretRedacted;
        final String snippetRedacted;
        final String fingerprint;
        final double entropy;
        final ValidationResult validation;
        final RevCommit commit;

        RawGitFinding(Rule rule, String filePath, int lineNumber, int columnNumber,
                      String rawSecret, String secretRedacted, String snippetRedacted,
                      String fingerprint, double entropy, ValidationResult validation,
                      RevCommit commit) {
            this.rule = rule;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.rawSecret = rawSecret;
            this.secretRedacted = secretRedacted;
            this.snippetRedacted = snippetRedacted;
            this.fingerprint = fingerprint;
            this.entropy = entropy;
            this.validation = validation;
            this.commit = commit;
        }
    }
}
