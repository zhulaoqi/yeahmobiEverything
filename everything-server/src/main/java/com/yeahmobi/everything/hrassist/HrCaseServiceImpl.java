package com.yeahmobi.everything.hrassist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local-file implementation for HR execution domain.
 */
public class HrCaseServiceImpl implements HrCaseService {

    private static final Logger log = LoggerFactory.getLogger(HrCaseServiceImpl.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Path BASE_DIR = Path.of(System.getProperty("user.home"), ".everything-assistant");
    private static final Path CASES_FILE = BASE_DIR.resolve("hr-cases.tsv");
    private static final Path ACTIONS_FILE = BASE_DIR.resolve("hr-actions.tsv");
    private static final Path EVIDENCE_FILE = BASE_DIR.resolve("hr-evidence.tsv");
    private static final Path REMINDERS_FILE = BASE_DIR.resolve("hr-reminders.tsv");

    private final Map<String, CandidateCase> cases = new ConcurrentHashMap<>();
    private final Map<String, ActionItem> actions = new ConcurrentHashMap<>();
    private final Map<String, EvidenceRef> evidences = new ConcurrentHashMap<>();
    private final Map<String, ReminderEvent> reminders = new ConcurrentHashMap<>();

    public HrCaseServiceImpl() {
        loadAll();
    }

    @Override
    public synchronized CandidateCase createCase(String candidateName,
                                                 String position,
                                                 HrCaseStage stage,
                                                 String owner,
                                                 HrRiskLevel riskLevel,
                                                 String nextAction,
                                                 String dueAt) {
        String name = safe(candidateName);
        if (name.isBlank()) {
            return null;
        }
        String now = nowText();
        String id = shortId();
        CandidateCase created = new CandidateCase(
                id,
                name,
                safe(position),
                stage == null ? HrCaseStage.SCREENING : stage,
                safe(owner),
                riskLevel == null ? HrRiskLevel.MEDIUM : riskLevel,
                safe(nextAction),
                safe(dueAt),
                HrCaseStatus.OPEN,
                now,
                now
        );
        cases.put(id, created);
        persistAll();
        return created;
    }

    @Override
    public synchronized CandidateCase updateCase(String caseId,
                                                 HrCaseStage stage,
                                                 HrRiskLevel riskLevel,
                                                 String nextAction,
                                                 String dueAt,
                                                 HrCaseStatus status) {
        if (caseId == null || caseId.isBlank()) {
            return null;
        }
        CandidateCase old = cases.get(caseId.trim());
        if (old == null) {
            return null;
        }
        CandidateCase updated = new CandidateCase(
                old.caseId(),
                old.candidateName(),
                old.position(),
                stage == null ? old.stage() : stage,
                old.owner(),
                riskLevel == null ? old.riskLevel() : riskLevel,
                nextAction == null ? old.nextAction() : nextAction.trim(),
                dueAt == null ? old.dueAt() : dueAt.trim(),
                status == null ? old.status() : status,
                old.createdAt(),
                nowText()
        );
        cases.put(updated.caseId(), updated);
        persistAll();
        return updated;
    }

    @Override
    public synchronized ActionItem addAction(String caseId,
                                             String actionType,
                                             String title,
                                             String dueAt,
                                             String priority,
                                             String sourceEvidence) {
        if (caseId == null || caseId.isBlank() || !cases.containsKey(caseId.trim())) {
            return null;
        }
        String t = safe(title);
        if (t.isBlank()) {
            return null;
        }
        String now = nowText();
        ActionItem item = new ActionItem(
                shortId(),
                caseId.trim(),
                safe(actionType),
                t,
                safe(dueAt),
                normalizePriority(priority),
                HrActionStatus.TODO,
                safe(sourceEvidence),
                now,
                now
        );
        actions.put(item.id(), item);
        persistAll();
        return item;
    }

    @Override
    public synchronized ActionItem updateActionStatus(String actionId, HrActionStatus status) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        ActionItem old = actions.get(actionId.trim());
        if (old == null) {
            return null;
        }
        ActionItem updated = new ActionItem(
                old.id(),
                old.caseId(),
                old.actionType(),
                old.title(),
                old.dueAt(),
                old.priority(),
                status == null ? old.status() : status,
                old.sourceEvidence(),
                old.createdAt(),
                nowText()
        );
        actions.put(updated.id(), updated);
        persistAll();
        return updated;
    }

    @Override
    public synchronized EvidenceRef addEvidence(String caseId,
                                                String sourceType,
                                                String sourcePathOrUrl,
                                                String snippet,
                                                double confidence) {
        if (caseId == null || caseId.isBlank() || !cases.containsKey(caseId.trim())) {
            return null;
        }
        EvidenceRef ref = new EvidenceRef(
                shortId(),
                caseId.trim(),
                safe(sourceType),
                safe(sourcePathOrUrl),
                safe(snippet),
                Math.max(0.0, Math.min(1.0, confidence)),
                nowText()
        );
        evidences.put(ref.id(), ref);
        persistAll();
        return ref;
    }

    @Override
    public synchronized ReminderEvent addReminder(String actionId, String remindAt, String channel) {
        if (actionId == null || actionId.isBlank() || !actions.containsKey(actionId.trim())) {
            return null;
        }
        ReminderEvent event = new ReminderEvent(
                shortId(),
                actionId.trim(),
                safe(remindAt),
                channel == null || channel.isBlank() ? "in-app" : channel.trim(),
                "todo",
                nowText()
        );
        reminders.put(event.id(), event);
        persistAll();
        return event;
    }

    @Override
    public synchronized List<CandidateCase> listCases(HrCaseStatus statusFilter) {
        return cases.values().stream()
                .filter(c -> statusFilter == null || c.status() == statusFilter)
                .sorted(Comparator.comparing(CandidateCase::updatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized HrCaseSnapshot getSnapshot(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return null;
        }
        CandidateCase candidateCase = cases.get(caseId.trim());
        if (candidateCase == null) {
            return null;
        }
        List<ActionItem> caseActions = actions.values().stream()
                .filter(a -> a != null && caseId.equals(a.caseId()))
                .sorted(Comparator.comparing(ActionItem::updatedAt).reversed())
                .toList();
        List<EvidenceRef> caseEvidence = evidences.values().stream()
                .filter(e -> e != null && caseId.equals(e.caseId()))
                .sorted(Comparator.comparing(EvidenceRef::createdAt).reversed())
                .toList();
        List<String> actionIds = caseActions.stream().map(ActionItem::id).toList();
        List<ReminderEvent> caseReminders = reminders.values().stream()
                .filter(r -> r != null && actionIds.contains(r.actionId()))
                .sorted(Comparator.comparing(ReminderEvent::createdAt).reversed())
                .toList();
        return new HrCaseSnapshot(candidateCase, caseActions, caseEvidence, caseReminders);
    }

    private String nowText() {
        return LocalDateTime.now().format(DT);
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private String normalizePriority(String priority) {
        String p = priority == null ? "" : priority.trim().toLowerCase(Locale.ROOT);
        if ("high".equals(p) || "h".equals(p) || "高".equals(p)) {
            return "high";
        }
        if ("low".equals(p) || "l".equals(p) || "低".equals(p)) {
            return "low";
        }
        return "medium";
    }

    private void loadAll() {
        loadCases();
        loadActions();
        loadEvidence();
        loadReminders();
    }

    private void loadCases() {
        for (String line : readLines(CASES_FILE)) {
            String[] p = line.split("\t", -1);
            if (p.length < 11) {
                continue;
            }
            CandidateCase c = new CandidateCase(
                    unesc(p[0]),
                    unesc(p[1]),
                    unesc(p[2]),
                    parseStage(unesc(p[3])),
                    unesc(p[4]),
                    parseRisk(unesc(p[5])),
                    unesc(p[6]),
                    unesc(p[7]),
                    parseCaseStatus(unesc(p[8])),
                    unesc(p[9]),
                    unesc(p[10])
            );
            if (c.caseId() != null && !c.caseId().isBlank()) {
                cases.put(c.caseId(), c);
            }
        }
    }

    private void loadActions() {
        for (String line : readLines(ACTIONS_FILE)) {
            String[] p = line.split("\t", -1);
            if (p.length < 10) {
                continue;
            }
            ActionItem a = new ActionItem(
                    unesc(p[0]),
                    unesc(p[1]),
                    unesc(p[2]),
                    unesc(p[3]),
                    unesc(p[4]),
                    unesc(p[5]),
                    parseActionStatus(unesc(p[6])),
                    unesc(p[7]),
                    unesc(p[8]),
                    unesc(p[9])
            );
            if (a.id() != null && !a.id().isBlank()) {
                actions.put(a.id(), a);
            }
        }
    }

    private void loadEvidence() {
        for (String line : readLines(EVIDENCE_FILE)) {
            String[] p = line.split("\t", -1);
            if (p.length < 7) {
                continue;
            }
            double confidence;
            try {
                confidence = Double.parseDouble(unesc(p[5]));
            } catch (Exception ignored) {
                log.debug("Could not parse confidence value, defaulting to 0.0");
                confidence = 0.0;
            }
            EvidenceRef e = new EvidenceRef(
                    unesc(p[0]),
                    unesc(p[1]),
                    unesc(p[2]),
                    unesc(p[3]),
                    unesc(p[4]),
                    confidence,
                    unesc(p[6])
            );
            if (e.id() != null && !e.id().isBlank()) {
                evidences.put(e.id(), e);
            }
        }
    }

    private void loadReminders() {
        for (String line : readLines(REMINDERS_FILE)) {
            String[] p = line.split("\t", -1);
            if (p.length < 6) {
                continue;
            }
            ReminderEvent r = new ReminderEvent(
                    unesc(p[0]),
                    unesc(p[1]),
                    unesc(p[2]),
                    unesc(p[3]),
                    unesc(p[4]),
                    unesc(p[5])
            );
            if (r.id() != null && !r.id().isBlank()) {
                reminders.put(r.id(), r);
            }
        }
    }

    private List<String> readLines(Path file) {
        try {
            if (!Files.exists(file)) {
                return List.of();
            }
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            log.debug("Could not read lines from file '{}', returning empty: {}", file, ignored.getMessage());
            return List.of();
        }
    }

    private void persistAll() {
        try {
            Files.createDirectories(BASE_DIR);
        } catch (Exception ignored) {
            log.warn("Could not create HR base directory, persistence may fail: {}", ignored.getMessage());
        }
        writeLines(CASES_FILE, encodeCases());
        writeLines(ACTIONS_FILE, encodeActions());
        writeLines(EVIDENCE_FILE, encodeEvidence());
        writeLines(REMINDERS_FILE, encodeReminders());
    }

    private List<String> encodeCases() {
        List<String> out = new ArrayList<>();
        for (CandidateCase c : cases.values()) {
            out.add(String.join("\t",
                    esc(c.caseId()), esc(c.candidateName()), esc(c.position()),
                    esc(c.stage().name()), esc(c.owner()), esc(c.riskLevel().name()),
                    esc(c.nextAction()), esc(c.dueAt()), esc(c.status().name()),
                    esc(c.createdAt()), esc(c.updatedAt())
            ));
        }
        return out;
    }

    private List<String> encodeActions() {
        List<String> out = new ArrayList<>();
        for (ActionItem a : actions.values()) {
            out.add(String.join("\t",
                    esc(a.id()), esc(a.caseId()), esc(a.actionType()),
                    esc(a.title()), esc(a.dueAt()), esc(a.priority()),
                    esc(a.status().name()), esc(a.sourceEvidence()),
                    esc(a.createdAt()), esc(a.updatedAt())
            ));
        }
        return out;
    }

    private List<String> encodeEvidence() {
        List<String> out = new ArrayList<>();
        for (EvidenceRef e : evidences.values()) {
            out.add(String.join("\t",
                    esc(e.id()), esc(e.caseId()), esc(e.sourceType()),
                    esc(e.sourcePathOrUrl()), esc(e.snippet()),
                    esc(String.valueOf(e.confidence())), esc(e.createdAt())
            ));
        }
        return out;
    }

    private List<String> encodeReminders() {
        List<String> out = new ArrayList<>();
        for (ReminderEvent r : reminders.values()) {
            out.add(String.join("\t",
                    esc(r.id()), esc(r.actionId()), esc(r.remindAt()),
                    esc(r.channel()), esc(r.status()), esc(r.createdAt())
            ));
        }
        return out;
    }

    private void writeLines(Path file, List<String> lines) {
        try {
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception ignored) {
            log.debug("Could not write HR data to file '{}', in-memory state is preserved: {}", file, ignored.getMessage());
        }
    }

    private HrCaseStage parseStage(String v) {
        try {
            return HrCaseStage.valueOf(v);
        } catch (Exception ignored) {
            log.debug("Unknown HrCaseStage value '{}', defaulting to SCREENING", v);
            return HrCaseStage.SCREENING;
        }
    }

    private HrRiskLevel parseRisk(String v) {
        try {
            return HrRiskLevel.valueOf(v);
        } catch (Exception ignored) {
            log.debug("Unknown HrRiskLevel value '{}', defaulting to MEDIUM", v);
            return HrRiskLevel.MEDIUM;
        }
    }

    private HrCaseStatus parseCaseStatus(String v) {
        try {
            return HrCaseStatus.valueOf(v);
        } catch (Exception ignored) {
            log.debug("Unknown HrCaseStatus value '{}', defaulting to OPEN", v);
            return HrCaseStatus.OPEN;
        }
    }

    private HrActionStatus parseActionStatus(String v) {
        try {
            return HrActionStatus.valueOf(v);
        } catch (Exception ignored) {
            log.debug("Unknown HrActionStatus value '{}', defaulting to TODO", v);
            return HrActionStatus.TODO;
        }
    }

    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String unesc(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                switch (ch) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case '\\' -> out.append('\\');
                    default -> out.append(ch);
                }
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
            } else {
                out.append(ch);
            }
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }
}

