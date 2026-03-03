package com.yeahmobi.everything.agentscope.tools;

import io.agentscope.core.tool.Toolkit;

import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public web research toolkit for information retrieval skills.
 * <p>
 * This tool is intentionally lightweight and dependency-free:
 * - Uses DuckDuckGo HTML endpoint for public search
 * - Supports optional site restriction
 * - Fetches and extracts plain text from public pages
 * </p>
 */
public class WebResearchTool extends Toolkit {

    private static final String UA = "Mozilla/5.0 (EverythingAssistantBot/1.0)";
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final int DEFAULT_FETCH_CHARS = 4000;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> TRUSTED_DOMAIN_SUFFIXES = List.of(
            "docs.oracle.com", "openjdk.org", "oracle.com", "github.com", "stackoverflow.com",
            "developer.mozilla.org", "w3.org", "ietf.org", "rfc-editor.org",
            "spring.io", "microsoft.com", "aws.amazon.com", "cloud.google.com",
            "alibabacloud.com", "vercel.com", "zhihu.com", "juejin.cn", "csdn.net"
    );
    private static final List<String> BLOCKED_HOST_SUFFIXES = List.of(
            ".local", ".internal", ".localhost"
    );

    /**
     * AI-friendly entrypoint: infer intent + search strategy from user question.
     * Strategy:
     * 1) detect preferred site from question/site arg
     * 2) generate query variants by intent
     * 3) site-priority search then broad fallback
     */
    public String smartSearch(String question, String preferredSite, Integer limit) {
        String q = question == null ? "" : question.trim();
        if (q.isBlank()) {
            return "question 不能为空";
        }
        int n = normalizeLimit(limit);
        String site = normalizeSite(preferredSite);
        if (site.isBlank()) {
            site = inferPreferredSiteFromQuestion(q);
        }
        String intent = classifyIntent(q);
        List<String> variants = buildQueryVariants(q, intent);
        try {
            List<SearchItem> merged = new ArrayList<>();
            boolean fallbackUsed = false;
            // Pass 1: preferred site
            if (!site.isBlank()) {
                for (String variant : variants) {
                    List<SearchItem> items = searchOnce(buildScopedQuery(variant, site), n);
                    mergeSearchItems(merged, items, n);
                    if (merged.size() >= n) {
                        break;
                    }
                }
            }
            // Pass 2: broad web fallback
            if (merged.isEmpty() || merged.size() < Math.max(2, n / 2)) {
                fallbackUsed = true;
                for (String variant : variants) {
                    List<SearchItem> items = searchOnce(variant, n);
                    mergeSearchItems(merged, items, n);
                    if (merged.size() >= n) {
                        break;
                    }
                }
            }
            if (merged.isEmpty()) {
                return "未检索到公开结果（已尝试问题改写与全网回退），请补充更具体关键词。";
            }
            merged = rankByQuality(merged, site, q, intent);
            merged = limitDomainRepetition(merged, n, 2);
            merged = enrichWithPageTitle(merged, 3);
            StringBuilder out = new StringBuilder();
            out.append("问题: ").append(q).append("\n")
                    .append("意图识别: ").append(intent).append("\n");
            if (!site.isBlank()) {
                out.append("优先站点: ").append(site).append("\n");
            }
            out.append("Query 变体: ").append(String.join(" | ", variants)).append("\n");
            if (fallbackUsed) {
                out.append("检索策略: 已自动回退到全网检索\n");
            }
            out.append("命中结果: ").append(merged.size()).append("\n\n");
            String fetchedAt = LocalDateTime.now().format(TS_FMT);
            int i = 1;
            for (SearchItem it : merged) {
                int score = calculateQualityScore(it, site, q, intent);
                out.append(i).append(". ").append(it.title()).append("\n")
                        .append("URL: ").append(it.url()).append("\n")
                        .append("来源域名: ").append(extractHost(it.url())).append("\n")
                        .append("置信度: ").append(toConfidenceLevel(score)).append("\n")
                        .append("抓取时间: ").append(fetchedAt).append("\n");
                if (!it.snippet().isBlank()) {
                    out.append("摘要: ").append(it.snippet()).append("\n");
                }
                out.append("\n");
                i++;
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "智能检索失败: " + e.getMessage();
        }
    }

    public String webSearch(String query, String site, Integer limit) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            return "query 不能为空";
        }
        int n = normalizeLimit(limit);
        try {
            String scoped = buildScopedQuery(q, site);
            List<SearchItem> items = searchOnce(scoped, n);
            boolean fallbackUsed = false;

            // If site-restricted search has no result, automatically fallback to broader web search.
            if (items.isEmpty() && site != null && !site.isBlank()) {
                fallbackUsed = true;
                items = searchOnce(q, n);
            }

            if (items.isEmpty()) {
                // Last attempt: simplify query by removing punctuations/quotes.
                String relaxed = relaxQuery(q);
                if (!relaxed.equals(q)) {
                    fallbackUsed = true;
                    items = searchOnce(relaxed, n);
                }
            }

            if (items.isEmpty()) {
                return "未检索到公开结果（已尝试站点限定与全网回退），请调整关键词。";
            }
            items = rankByQuality(items, normalizeSite(site), q, classifyIntent(q));
            items = limitDomainRepetition(items, n, 2);
            items = enrichWithPageTitle(items, 3);
            StringBuilder out = new StringBuilder();
            out.append("检索词: ").append(q).append("\n");
            if (site != null && !site.isBlank()) {
                out.append("优先站点: ").append(site).append("\n");
            }
            if (fallbackUsed) {
                out.append("检索策略: 已自动回退到更宽范围检索\n");
            }
            out.append("命中结果: ").append(items.size()).append("\n\n");
            String fetchedAt = LocalDateTime.now().format(TS_FMT);
            int i = 1;
            for (SearchItem it : items) {
                int score = calculateQualityScore(it, normalizeSite(site), q, classifyIntent(q));
                out.append(i).append(". ").append(it.title()).append("\n")
                        .append("URL: ").append(it.url()).append("\n")
                        .append("来源域名: ").append(extractHost(it.url())).append("\n")
                        .append("置信度: ").append(toConfidenceLevel(score)).append("\n")
                        .append("抓取时间: ").append(fetchedAt).append("\n");
                if (!it.snippet().isBlank()) {
                    out.append("摘要: ").append(it.snippet()).append("\n");
                }
                out.append("\n");
                i++;
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "检索失败: " + e.getMessage();
        }
    }

    public String webFetch(String url, Integer maxChars) {
        if (!isSafePublicUrl(url)) {
            return "URL 不符合安全策略（仅允许公网 http/https，禁止内网/本地地址）";
        }
        int budget = (maxChars == null || maxChars <= 0) ? DEFAULT_FETCH_CHARS : Math.min(maxChars, 12000);
        try {
            String html = httpGet(url, 12000);
            String text = htmlToText(html);
            if (text.length() > budget) {
                text = text.substring(0, budget) + "\n...[内容已截断]";
            }
            if (text.isBlank()) {
                return "抓取成功但未提取到有效正文";
            }
            return "URL: " + url + "\n\n" + text;
        } catch (Exception e) {
            return "抓取失败: " + e.getMessage();
        }
    }

    public String webSearchWithFetch(String query, String site) {
        String search = webSearch(query, site, 3);
        List<String> urls = extractUrlsFromSearchResult(search);
        if (urls.isEmpty()) {
            return search;
        }
        StringBuilder out = new StringBuilder(search).append("\n\n=== 页面正文摘录 ===\n");
        int index = 1;
        for (String u : urls) {
            String fetched = webFetch(u, 1200);
            out.append("\n[").append(index).append("] ").append(u).append("\n");
            out.append(fetched).append("\n");
            index++;
        }
        return out.toString();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String buildScopedQuery(String query, String site) {
        String s = site == null ? "" : site.trim();
        if (s.isBlank()) {
            return query;
        }
        String normalized = s.replace("https://", "").replace("http://", "");
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        int slash = normalized.indexOf('/');
        if (slash > 0) {
            normalized = normalized.substring(0, slash);
        }
        return "site:" + normalized + " " + query;
    }

    private String normalizeSite(String site) {
        String s = site == null ? "" : site.trim();
        if (s.isBlank()) {
            return "";
        }
        s = s.replace("https://", "").replace("http://", "");
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        int slash = s.indexOf('/');
        if (slash > 0) {
            s = s.substring(0, slash);
        }
        return s;
    }

    private String httpGet(String url, int timeoutMs) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", UA)
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        return resp.body() != null ? resp.body() : "";
    }

    private List<SearchItem> searchOnce(String query, int limit) throws Exception {
        String url = "https://duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String html = httpGet(url, 10000);
        return parseSearchResults(html, limit);
    }

    private void mergeSearchItems(List<SearchItem> target, List<SearchItem> incoming, int maxSize) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        for (SearchItem item : incoming) {
            boolean exists = target.stream().anyMatch(s -> s.url().equalsIgnoreCase(item.url()));
            if (!exists) {
                target.add(item);
                if (target.size() >= maxSize) {
                    break;
                }
            }
        }
    }

    private String inferPreferredSiteFromQuestion(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (q.contains("知乎")) {
            return "zhihu.com";
        }
        if (q.contains("github")) {
            return "github.com";
        }
        if (q.contains("stackoverflow")) {
            return "stackoverflow.com";
        }
        if (q.contains("官网") || q.contains("official")) {
            // let broad search choose best domains if only "official site" intent detected
            return "";
        }
        return "";
    }

    private String classifyIntent(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (q.contains("对比") || q.contains("区别") || q.contains("compare")) {
            return "对比选型";
        }
        if (q.contains("怎么") || q.contains("如何") || q.contains("教程") || q.contains("步骤")) {
            return "操作步骤";
        }
        if (q.contains("报错") || q.contains("错误") || q.contains("异常") || q.contains("bug")) {
            return "故障排查";
        }
        if (q.contains("最新") || q.contains("今天") || q.contains("动态") || q.contains("news")) {
            return "时效信息";
        }
        return "概念解释";
    }

    private List<String> buildQueryVariants(String question, String intent) {
        String q = relaxQuery(question);
        List<String> variants = new ArrayList<>();
        variants.add(q);
        if ("操作步骤".equals(intent)) {
            variants.add(q + " 教程");
            variants.add(q + " guide");
        } else if ("故障排查".equals(intent)) {
            variants.add(q + " error fix");
            variants.add(q + " 解决方案");
        } else if ("对比选型".equals(intent)) {
            variants.add(q + " 对比");
            variants.add(q + " comparison");
        } else if ("时效信息".equals(intent)) {
            variants.add(q + " 最新");
            variants.add(q + " update");
        } else {
            variants.add(q + " 详解");
            variants.add(q + " explanation");
        }
        // dedupe
        List<String> deduped = new ArrayList<>();
        for (String v : variants) {
            String t = v == null ? "" : v.trim();
            if (t.isBlank()) {
                continue;
            }
            if (!deduped.contains(t)) {
                deduped.add(t);
            }
        }
        return deduped;
    }

    private String relaxQuery(String query) {
        if (query == null) {
            return "";
        }
        String relaxed = query
                .replace("“", " ")
                .replace("”", " ")
                .replace("\"", " ")
                .replace("‘", " ")
                .replace("’", " ")
                .replace("'", " ")
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .replace(".", " ")
                .replace("？", " ")
                .replace("?", " ")
                .replace("！", " ")
                .replace("!", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return relaxed.isBlank() ? query : relaxed;
    }

    private List<SearchItem> parseSearchResults(String html, int limit) {
        List<SearchItem> items = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return items;
        }
        Pattern p = Pattern.compile(
                "(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
        Matcher m = p.matcher(html);
        int count = 0;
        while (m.find() && count < limit) {
            String href = unescapeHtml(m.group(1));
            href = resolveDuckDuckGoRedirect(href);
            String title = cleanText(unescapeHtml(m.group(2)));
            if (!isSafePublicUrl(href) || title.isBlank()) {
                continue;
            }
            String snippet = extractNearbySnippet(html, m.end(), 240);
            items.add(new SearchItem(title, href, snippet));
            count++;
        }
        return items;
    }

    private String resolveDuckDuckGoRedirect(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String v = href.trim();
        if (v.startsWith("//")) {
            v = "https:" + v;
        }
        if (v.contains("duckduckgo.com/l/?") || v.contains("duckduckgo.com/l/")) {
            Matcher m = Pattern.compile("[?&]uddg=([^&]+)").matcher(v);
            if (m.find()) {
                try {
                    return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    return v;
                }
            }
        }
        return v;
    }

    private String extractNearbySnippet(String html, int start, int window) {
        int end = Math.min(html.length(), start + Math.max(window, 120));
        String seg = html.substring(start, end);
        seg = seg.replaceAll("(?is)<[^>]+>", " ");
        seg = unescapeHtml(seg);
        return cleanText(seg);
    }

    private List<String> extractUrlsFromSearchResult(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return urls;
        }
        Matcher m = Pattern.compile("(?im)^URL:\\s*(https?://\\S+)\\s*$").matcher(text);
        while (m.find()) {
            urls.add(m.group(1).trim());
            if (urls.size() >= 3) {
                break;
            }
        }
        return urls;
    }

    private String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        return cleanText(unescapeHtml(text));
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\u00A0', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private String unescapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("http://") || v.startsWith("https://");
    }

    private boolean isSafePublicUrl(String value) {
        if (!isHttpUrl(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h)) {
                return false;
            }
            for (String suffix : BLOCKED_HOST_SUFFIXES) {
                if (h.endsWith(suffix)) {
                    return false;
                }
            }
            // Basic private-network protection for numeric IPv4 hosts.
            if (h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("127.")
                        || h.startsWith("169.254.")) {
                    return false;
                }
                Matcher m = Pattern.compile("^(172)\\.(\\d+)\\..+").matcher(h);
                if (m.find()) {
                    int second = Integer.parseInt(m.group(2));
                    if (second >= 16 && second <= 31) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<SearchItem> rankByQuality(List<SearchItem> input, String preferredSite, String question, String intent) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        String site = preferredSite == null ? "" : preferredSite.toLowerCase(Locale.ROOT);
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String i = intent == null ? "" : intent;
        List<ScoredItem> scored = new ArrayList<>();
        for (SearchItem item : input) {
            if (item == null || !isSafePublicUrl(item.url())) {
                continue;
            }
            int score = calculateQualityScore(item, site, q, i);
            scored.add(new ScoredItem(item, score));
        }
        scored.sort(Comparator.comparingInt(ScoredItem::score).reversed());
        List<SearchItem> ranked = new ArrayList<>();
        for (ScoredItem s : scored) {
            ranked.add(s.item());
        }
        return ranked;
    }

    private int calculateQualityScore(SearchItem item, String preferredSite, String question, String intent) {
        if (item == null) {
            return 0;
        }
        String site = preferredSite == null ? "" : preferredSite.toLowerCase(Locale.ROOT);
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String i = intent == null ? "" : intent;
        int score = 0;
        String url = item.url() == null ? "" : item.url().toLowerCase(Locale.ROOT);
        String title = item.title() == null ? "" : item.title().toLowerCase(Locale.ROOT);
        String snippet = item.snippet() == null ? "" : item.snippet().toLowerCase(Locale.ROOT);
        String host = extractHost(url);
        if (!site.isBlank() && host.endsWith(site)) {
            score += 120;
        }
        if ("时效信息".equals(i) && (title.contains("2026") || title.contains("2025")
                || snippet.contains("2026") || snippet.contains("2025"))) {
            score += 40;
        }
        if ("故障排查".equals(i) && (title.contains("error") || title.contains("异常") || snippet.contains("fix"))) {
            score += 25;
        }
        if ("操作步骤".equals(i) && (title.contains("guide") || title.contains("教程") || snippet.contains("步骤"))) {
            score += 25;
        }
        for (String trusted : TRUSTED_DOMAIN_SUFFIXES) {
            if (host.endsWith(trusted)) {
                score += 80;
                break;
            }
        }
        if (url.startsWith("https://")) {
            score += 10;
        }
        if (!q.isBlank() && (title.contains(q) || snippet.contains(q))) {
            score += 15;
        }
        score += Math.min(20, snippet.length() / 30);
        return score;
    }

    private String toConfidenceLevel(int score) {
        if (score >= 150) {
            return "高";
        }
        if (score >= 90) {
            return "中";
        }
        return "低";
    }

    private List<SearchItem> limitDomainRepetition(List<SearchItem> ranked, int maxTotal, int maxPerDomain) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, maxTotal);
        int domainCap = Math.max(1, maxPerDomain);
        Map<String, Integer> domainCount = new HashMap<>();
        List<SearchItem> selected = new ArrayList<>();
        List<SearchItem> overflow = new ArrayList<>();
        for (SearchItem item : ranked) {
            String host = extractHost(item.url());
            int used = domainCount.getOrDefault(host, 0);
            if (used < domainCap) {
                selected.add(item);
                domainCount.put(host, used + 1);
            } else {
                overflow.add(item);
            }
            if (selected.size() >= limit) {
                return selected;
            }
        }
        for (SearchItem item : overflow) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(item);
        }
        return selected;
    }

    private List<SearchItem> enrichWithPageTitle(List<SearchItem> items, int maxFetch) {
        if (items == null || items.isEmpty() || maxFetch <= 0) {
            return items == null ? List.of() : items;
        }
        List<SearchItem> enriched = new ArrayList<>();
        int fetchCount = 0;
        for (SearchItem item : items) {
            if (item == null) {
                continue;
            }
            SearchItem current = item;
            if (fetchCount < maxFetch) {
                String title = fetchHtmlTitle(item.url(), 3500);
                if (isValidFetchedTitle(title, item.title())) {
                    current = new SearchItem(title, item.url(), item.snippet());
                }
                fetchCount++;
            }
            enriched.add(current);
        }
        return enriched;
    }

    private String fetchHtmlTitle(String url, int timeoutMs) {
        if (!isSafePublicUrl(url)) {
            return "";
        }
        try {
            String html = httpGet(url, timeoutMs);
            if (html == null || html.isBlank()) {
                return "";
            }
            Matcher m = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
            if (!m.find()) {
                return "";
            }
            String raw = unescapeHtml(m.group(1));
            String title = cleanText(raw);
            return trimTitle(title);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isValidFetchedTitle(String fetchedTitle, String originalTitle) {
        String f = fetchedTitle == null ? "" : fetchedTitle.trim();
        if (f.isBlank() || f.length() < 3) {
            return false;
        }
        String lowered = f.toLowerCase(Locale.ROOT);
        if ("access denied".equals(lowered) || "just a moment...".equals(lowered)) {
            return false;
        }
        String o = originalTitle == null ? "" : originalTitle.trim();
        return !f.equalsIgnoreCase(o);
    }

    private String trimTitle(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim();
        if (t.length() <= 120) {
            return t;
        }
        return t.substring(0, 120) + "...";
    }

    private String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private record SearchItem(String title, String url, String snippet) {
    }

    private record ScoredItem(SearchItem item, int score) {
    }
}
