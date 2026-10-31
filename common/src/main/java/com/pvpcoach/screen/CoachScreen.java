package com.pvpcoach.screen;

import com.pvpcoach.CoachConfig;
import com.pvpcoach.coach.Coach;
import com.pvpcoach.coach.CoachReport;
import com.pvpcoach.coach.Tip;
import com.pvpcoach.fight.FightHistory;
import com.pvpcoach.fight.FightRecord;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The coach window: the report of one fight, progress over all fights, and settings. */
public class CoachScreen extends Screen {
    public enum Tab { REPORT, PROGRESS, SETTINGS }

    private static final int PANEL = 0xF0111018;
    private static final int CARD = 0xFF1A1826;
    private static final int ROW = 0xFF15131F;
    private static final int BORDER = 0xFF2E2846;
    private static final int ACCENT = 0xFFA78BFA;
    private static final int TEXT = 0xFFE8E6F0;
    private static final int DIM = 0xFF9A97AB;
    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = 0xFFF87171;
    private static final int YELLOW = 0xFFFACC15;
    private static final int GRAY = 0xFF9CA3AF;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);
    private static final int MISTAKE_WINDOW = 20;

    /** Remembered between openings, so K brings you back to where you were. */
    private static Tab lastTab = Tab.REPORT;

    private Tab tab;
    private int index;
    private double scroll;
    private int contentHeight;
    private boolean confirmClear;
    private int left;
    private int right;
    private int top;
    private int bottom;
    private int contentTop;
    private int contentBottom;
    private final Map<FightRecord, CoachReport> reports = new HashMap<>();

    public CoachScreen() {
        this(lastTab);
    }

    public CoachScreen(Tab tab) {
        super(Component.translatable("pvpcoach.title"));
        this.tab = tab;
    }

    /** Opens the report on the given fight (0 = newest) instead of the newest one. */
    public CoachScreen showingFight(int fightIndex) {
        index = fightIndex;
        return this;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(460, width - 16);
        left = (width - panelWidth) / 2;
        right = left + panelWidth;
        top = 6;
        bottom = height - 6;
        contentTop = top + 38;
        contentBottom = bottom - 30;

        int tabWidth = 66;
        int x = right - 8 - Tab.values().length * (tabWidth + 2) + 2;
        for (Tab t : Tab.values()) {
            Button button = addRenderableWidget(Button.builder(
                Component.translatable("pvpcoach.tab." + t.name().toLowerCase()), b -> switchTab(t))
                .bounds(x, top + 7, tabWidth, 18).build());
            button.active = t != tab;
            x += tabWidth + 2;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(width / 2 - 50, bottom - 25, 100, 20).build());

        List<FightRecord> fights = FightHistory.all();
        index = Math.max(0, Math.min(index, fights.size() - 1));
        if (tab == Tab.REPORT && fights.size() > 1) {
            Button newer = addRenderableWidget(Button.builder(Component.translatable("pvpcoach.nav.newer"), b -> showFight(index - 1))
                .bounds(left + 8, bottom - 25, 70, 20).build());
            newer.active = index > 0;
            Button older = addRenderableWidget(Button.builder(Component.translatable("pvpcoach.nav.older"), b -> showFight(index + 1))
                .bounds(right - 78, bottom - 25, 70, 20).build());
            older.active = index < fights.size() - 1;
        }
        if (tab == Tab.SETTINGS) initSettings();
    }

    private void initSettings() {
        CoachConfig config = CoachConfig.get();
        int w = Math.min(280, right - left - 24);
        int x = (left + right) / 2 - w / 2;
        int y = contentTop + 22;
        addRenderableWidget(Button.builder(Component.translatable("pvpcoach.settings.chat", onOff(config.chatSummary)), b -> {
            config.chatSummary = !config.chatSummary;
            saveSettings();
        }).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.translatable("pvpcoach.settings.chat.tooltip"))).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("pvpcoach.settings.mobs", onOff(config.trackMobs)), b -> {
            config.trackMobs = !config.trackMobs;
            saveSettings();
        }).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.translatable("pvpcoach.settings.mobs.tooltip"))).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("pvpcoach.settings.timeout", config.fightTimeoutSeconds), b -> {
            config.fightTimeoutSeconds = CoachConfig.next(CoachConfig.TIMEOUT_CHOICES, config.fightTimeoutSeconds);
            saveSettings();
        }).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.translatable("pvpcoach.settings.timeout.tooltip"))).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("pvpcoach.settings.min", config.minExchanges), b -> {
            config.minExchanges = CoachConfig.next(CoachConfig.MIN_EXCHANGE_CHOICES, config.minExchanges);
            saveSettings();
        }).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.translatable("pvpcoach.settings.min.tooltip"))).build());
        y += 36;
        Component clearLabel = confirmClear
            ? Component.translatable("pvpcoach.settings.clear.confirm")
            : Component.translatable("pvpcoach.settings.clear", FightHistory.all().size());
        Button clear = addRenderableWidget(Button.builder(clearLabel, b -> {
            if (confirmClear) {
                FightHistory.clear();
                reports.clear();
                confirmClear = false;
            } else {
                confirmClear = true;
            }
            rebuildWidgets();
        }).bounds(x, y, w, 20).build());
        clear.active = !FightHistory.all().isEmpty();
    }

    private void saveSettings() {
        CoachConfig.save();
        rebuildWidgets();
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        scroll = 0;
        confirmClear = false;
        rebuildWidgets();
    }

    private void showFight(int i) {
        index = i;
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = clampScroll(scroll - scrollY * 18);
        return true;
    }

    private double clampScroll(double value) {
        return Math.max(0, Math.min(value, Math.max(0, contentHeight - (contentBottom - contentTop))));
    }

    // ---- Rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(left, top, right, bottom, PANEL);
        border(g, left, top, right, bottom, BORDER);
        g.text(font, title, left + 10, top + 12, ACCENT, true);
        g.fill(left + 8, top + 31, right - 8, top + 32, BORDER);

        if (tab != Tab.SETTINGS) {
            scroll = clampScroll(scroll);
            g.enableScissor(left + 1, contentTop, right - 1, contentBottom);
            int y = contentTop + 4 - (int) scroll;
            int end = tab == Tab.REPORT ? drawReport(g, y) : drawProgress(g, y);
            contentHeight = end + (int) scroll - contentTop + 6;
            g.disableScissor();
            if (contentHeight > contentBottom - contentTop) drawScrollbar(g);
        } else {
            g.centeredText(font, Component.translatable("pvpcoach.settings.hint"), (left + right) / 2, contentTop + 6, DIM);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private int drawReport(GuiGraphicsExtractor g, int y) {
        List<FightRecord> fights = FightHistory.all();
        int x0 = left + 12;
        int x1 = right - 12;
        if (fights.isEmpty()) return drawEmpty(g, y, x0, x1);

        FightRecord f = fights.get(index);
        CoachReport report = report(f);

        // Header card: result, opponent, date, score.
        g.fill(x0, y, x1, y + 46, CARD);
        Component result = Component.translatable("pvpcoach.result." + f.result.name().toLowerCase());
        int badgeWidth = font.width(result) + 10;
        g.fill(x0 + 8, y + 8, x0 + 8 + badgeWidth, y + 21, darken(resultColor(f.result)));
        g.text(font, result, x0 + 13, y + 11, resultColor(f.result), false);
        g.text(font, Component.translatable("pvpcoach.report.versus", f.opponent), x0 + 14 + badgeWidth, y + 11, TEXT, false);
        String date = DATE.format(Instant.ofEpochMilli(f.timestamp).atZone(ZoneId.systemDefault()));
        g.text(font, Component.translatable("pvpcoach.report.meta", date, duration(f.durationTicks), index + 1, fights.size()),
            x0 + 8, y + 30, DIM, false);

        String score = Integer.toString(report.score());
        int gradeWidth = font.width(report.grade()) * 2;
        int scoreWidth = font.width(score) * 3;
        int scoreX = x1 - 10 - gradeWidth - 6 - scoreWidth;
        drawScaled(g, score, scoreX, y + 10, 3, TEXT);
        drawScaled(g, report.grade(), x1 - 10 - gradeWidth, y + 17, 2, gradeColor(report.score()));
        y += 54;

        // Stats in two columns.
        g.text(font, Component.translatable("pvpcoach.report.stats"), x0, y, ACCENT, false);
        y += 13;
        List<String[]> rows = statRows(f);
        int columnWidth = (x1 - x0 - 8) / 2;
        for (int i = 0; i < rows.size(); i++) {
            int cx = x0 + (i % 2) * (columnWidth + 8);
            int cy = y + (i / 2) * 13;
            if ((i / 2) % 2 == 0) g.fill(cx, cy - 2, cx + columnWidth, cy + 10, ROW);
            drawRow(g, cx, cy, columnWidth, rows.get(i)[0], rows.get(i)[1], TEXT);
        }
        y += (rows.size() + 1) / 2 * 13 + 10;

        // Tips.
        g.text(font, Component.translatable("pvpcoach.report.tips"), x0, y, ACCENT, false);
        y += 14;
        if (report.tips().isEmpty()) {
            g.text(font, Component.translatable("pvpcoach.report.no_tips"), x0, y, DIM, false);
            return y + 12;
        }
        for (Tip tip : report.tips()) {
            int color = kindColor(tip.kind());
            Component line = Component.translatable("pvpcoach.kind." + tip.kind().name().toLowerCase())
                .withColor(color & 0xFFFFFF).append(" ").append(tip.text().copy().withColor(TEXT & 0xFFFFFF));
            List<FormattedCharSequence> lines = font.split(line, x1 - x0 - 12);
            g.fill(x0, y + 1, x0 + 5, y + 7, color);
            for (FormattedCharSequence l : lines) {
                g.text(font, l, x0 + 12, y, TEXT, false);
                y += 10;
            }
            y += 5;
        }
        return y;
    }

    private int drawProgress(GuiGraphicsExtractor g, int y) {
        List<FightRecord> fights = FightHistory.all();
        int x0 = left + 12;
        int x1 = right - 12;
        if (fights.isEmpty()) return drawEmpty(g, y, x0, x1);

        // Score chart, oldest on the left.
        int n = Math.min(30, fights.size());
        g.text(font, Component.translatable("pvpcoach.progress.chart", n), x0, y, ACCENT, false);
        y += 13;
        int chartTop = y;
        int chartHeight = 70;
        int chartBottom = chartTop + chartHeight;
        g.fill(x0, chartTop, x1, chartBottom + 8, CARD);
        for (int mark : new int[] {50, 80}) {
            int my = chartBottom - mark * chartHeight / 100;
            g.fill(x0 + 22, my, x1 - 4, my + 1, 0xFF2A2638);
            g.text(font, Integer.toString(mark), x0 + 4, my - 4, DIM, false);
        }
        int areaLeft = x0 + 24;
        int areaWidth = x1 - 6 - areaLeft;
        int barWidth = Math.max(2, Math.min(14, (areaWidth - (n - 1) * 2) / n));
        for (int i = 0; i < n; i++) {
            FightRecord f = fights.get(n - 1 - i);
            int bx = areaLeft + i * (barWidth + 2);
            int h = Math.max(2, f.score * (chartHeight - 4) / 100);
            g.fill(bx, chartBottom - h, bx + barWidth, chartBottom, gradeColor(f.score));
            g.fill(bx, chartBottom + 3, bx + barWidth, chartBottom + 5, resultColor(f.result));
        }
        y = chartBottom + 16;

        // Overview.
        g.text(font, Component.translatable("pvpcoach.progress.summary"), x0, y, ACCENT, false);
        y += 13;
        List<String[]> rows = progressRows(fights);
        int columnWidth = (x1 - x0 - 8) / 2;
        for (int i = 0; i < rows.size(); i++) {
            int cx = x0 + (i % 2) * (columnWidth + 8);
            int cy = y + (i / 2) * 13;
            if ((i / 2) % 2 == 0) g.fill(cx, cy - 2, cx + columnWidth, cy + 10, ROW);
            int color = rows.get(i).length > 2 ? Integer.parseUnsignedInt(rows.get(i)[2], 16) : TEXT;
            drawRow(g, cx, cy, columnWidth, rows.get(i)[0], rows.get(i)[1], color);
        }
        y += (rows.size() + 1) / 2 * 13 + 10;

        // Most common mistakes over the last fights.
        g.text(font, Component.translatable("pvpcoach.progress.mistakes"), x0, y, ACCENT, false);
        y += 14;
        int window = Math.min(MISTAKE_WINDOW, fights.size());
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Tip> examples = new HashMap<>();
        for (FightRecord f : fights.subList(0, window)) {
            for (Tip tip : report(f).tips()) {
                if (tip.kind() == Tip.Kind.GOOD) continue;
                counts.merge(tip.id(), 1, Integer::sum);
                examples.putIfAbsent(tip.id(), tip);
            }
        }
        if (counts.isEmpty()) {
            g.text(font, Component.translatable("pvpcoach.progress.no_mistakes"), x0, y, GREEN, false);
            return y + 12;
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        // Most frequent first; on a tie, FIX before IMPROVE.
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
            .thenComparing(entry -> examples.get(entry.getKey()).kind()));
        for (Map.Entry<String, Integer> entry : sorted.subList(0, Math.min(3, sorted.size()))) {
            Tip tip = examples.get(entry.getKey());
            g.fill(x0, y + 1, x0 + 5, y + 7, kindColor(tip.kind()));
            g.text(font, tip.shortText(), x0 + 12, y, TEXT, false);
            Component count = Component.translatable("pvpcoach.progress.mistake_count", entry.getValue(), window);
            g.text(font, count, x1 - font.width(count), y, DIM, false);
            y += 13;
        }
        return y;
    }

    private int drawEmpty(GuiGraphicsExtractor g, int y, int x0, int x1) {
        int center = (x0 + x1) / 2;
        y += 30;
        g.centeredText(font, Component.translatable("pvpcoach.empty.title"), center, y, TEXT);
        y += 18;
        for (Component text : List.of(Component.translatable("pvpcoach.empty.text"), Component.translatable("pvpcoach.empty.practice"))) {
            for (FormattedCharSequence line : font.split(text, Math.min(320, x1 - x0))) {
                g.centeredText(font, line, center, y, DIM);
                y += 10;
            }
            y += 8;
        }
        return y;
    }

    private void drawScrollbar(GuiGraphicsExtractor g) {
        int trackHeight = contentBottom - contentTop;
        int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
        double maxScroll = contentHeight - trackHeight;
        int thumbY = contentTop + (int) ((trackHeight - thumbHeight) * (scroll / maxScroll));
        g.fill(right - 5, contentTop, right - 3, contentBottom, 0xFF1C1A28);
        g.fill(right - 5, thumbY, right - 3, thumbY + thumbHeight, BORDER);
    }

    // ---- Data for the tables ----

    private static List<String[]> statRows(FightRecord f) {
        List<String[]> rows = new ArrayList<>();
        rows.add(row("pvpcoach.stat.hits", f.hitsLanded + " / " + f.hitsTaken));
        rows.add(row("pvpcoach.stat.accuracy", f.swings == 0 ? "-"
            : Coach.percent(f.accuracy()) + "% (" + (f.swings - f.misses) + "/" + f.swings + ")"));
        rows.add(row("pvpcoach.stat.charged", f.attacks == 0 ? "-" : Coach.percent(f.chargedRatio()) + "%"));
        rows.add(row("pvpcoach.stat.sprint", f.meleeHits == 0 ? "-" : Coach.percent(f.sprintRatio()) + "%"));
        rows.add(row("pvpcoach.stat.crits", Integer.toString(f.critHits)));
        rows.add(row("pvpcoach.stat.combo", f.longestCombo + " / " + f.longestComboTaken));
        rows.add(row("pvpcoach.stat.damage", tr("pvpcoach.unit.hearts", Coach.decimal(f.damageDealt / 2) + " / " + Coach.decimal(f.damageTaken / 2))));
        rows.add(row("pvpcoach.stat.lowest", tr("pvpcoach.unit.hearts", Coach.decimal(f.lowestHealth / 2))));
        rows.add(row("pvpcoach.stat.reach", f.reachSamples == 0 ? "-" : tr("pvpcoach.unit.blocks", Coach.decimal(f.averageReach()))));
        rows.add(row("pvpcoach.stat.totems", f.totemPops + " / " + f.opponentPops));
        rows.add(row("pvpcoach.stat.retotem", f.retotemTicks.isEmpty() ? "-" : tr("pvpcoach.unit.seconds", Coach.seconds(f.averageRetotemSeconds()))));
        rows.add(row("pvpcoach.stat.pearls", Integer.toString(f.pearls)));
        rows.add(row("pvpcoach.stat.eats", f.eats + " / " + f.eatsInterrupted));
        if (f.hadShield) rows.add(row("pvpcoach.stat.shield", tr("pvpcoach.unit.times", f.shieldsDisabled)));
        if (f.maceSmashes > 0) rows.add(row("pvpcoach.stat.mace", Integer.toString(f.maceSmashes)));
        if (f.projectileHits > 0) rows.add(row("pvpcoach.stat.projectiles", Integer.toString(f.projectileHits)));
        return rows;
    }

    private static List<String[]> progressRows(List<FightRecord> fights) {
        List<String[]> rows = new ArrayList<>();
        int wins = 0;
        int losses = 0;
        int best = 0;
        for (FightRecord f : fights) {
            if (f.result == FightRecord.Result.WIN) wins++;
            if (f.result == FightRecord.Result.LOSS) losses++;
            best = Math.max(best, f.score);
        }
        rows.add(row("pvpcoach.progress.fights", Integer.toString(fights.size())));
        rows.add(row("pvpcoach.progress.winrate", Coach.percent((double) wins / fights.size()) + "% (" + wins + "W " + losses + "L)"));

        double recent = averageScore(fights, 0, 10);
        double previous = averageScore(fights, 10, 20);
        String average = Integer.toString((int) Math.round(recent));
        String color = Integer.toHexString(TEXT);
        if (!Double.isNaN(previous)) {
            int delta = (int) Math.round(recent - previous);
            average += delta >= 0 ? "  +" + delta + " ▲" : "  " + delta + " ▼";
            color = Integer.toHexString(delta >= 0 ? GREEN : RED);
        }
        rows.add(new String[] {tr("pvpcoach.progress.average"), average, color});
        rows.add(row("pvpcoach.progress.best", Integer.toString(best)));

        double accuracy = 0, charged = 0, sprint = 0, retotem = 0;
        int accuracyN = 0, chargedN = 0, sprintN = 0, retotemN = 0;
        for (FightRecord f : fights) {
            if (f.swings > 0) { accuracy += f.accuracy(); accuracyN++; }
            if (f.attacks > 0) { charged += f.chargedRatio(); chargedN++; }
            if (f.meleeHits > 0) { sprint += f.sprintRatio(); sprintN++; }
            if (!f.retotemTicks.isEmpty()) { retotem += f.averageRetotemSeconds(); retotemN++; }
        }
        rows.add(row("pvpcoach.progress.accuracy", accuracyN == 0 ? "-" : Coach.percent(accuracy / accuracyN) + "%"));
        rows.add(row("pvpcoach.progress.charged", chargedN == 0 ? "-" : Coach.percent(charged / chargedN) + "%"));
        rows.add(row("pvpcoach.progress.sprint", sprintN == 0 ? "-" : Coach.percent(sprint / sprintN) + "%"));
        rows.add(row("pvpcoach.progress.retotem", retotemN == 0 ? "-" : tr("pvpcoach.unit.seconds", Coach.seconds(retotem / retotemN))));
        return rows;
    }

    private static double averageScore(List<FightRecord> fights, int from, int to) {
        if (fights.size() <= from) return Double.NaN;
        List<FightRecord> slice = fights.subList(from, Math.min(to, fights.size()));
        double sum = 0;
        for (FightRecord f : slice) sum += f.score;
        return sum / slice.size();
    }

    private CoachReport report(FightRecord f) {
        return reports.computeIfAbsent(f, Coach::analyze);
    }

    // ---- Small helpers ----

    private static String[] row(String key, String value) {
        return new String[] {tr(key), value};
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "pvpcoach.on" : "pvpcoach.off");
    }

    private static String duration(int ticks) {
        int seconds = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** A label on the left and a value on the right; the label is cut with "..." when the column is too narrow. */
    private void drawRow(GuiGraphicsExtractor g, int x, int y, int width, String label, String value, int valueColor) {
        int valueWidth = font.width(value);
        int labelSpace = width - 8 - valueWidth - 6;
        if (font.width(label) > labelSpace) label = font.plainSubstrByWidth(label, labelSpace - font.width("...")) + "...";
        g.text(font, label, x + 4, y, DIM, false);
        g.text(font, value, x + width - 4 - valueWidth, y, valueColor, false);
    }

    private void drawScaled(GuiGraphicsExtractor g, String text, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, true);
        g.pose().popMatrix();
    }

    private static void border(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    private static int resultColor(FightRecord.Result result) {
        return switch (result) {
            case WIN -> GREEN;
            case LOSS -> RED;
            case ENDED -> GRAY;
        };
    }

    private static int gradeColor(int score) {
        if (score >= 80) return GREEN;
        if (score >= 65) return ACCENT;
        if (score >= 50) return YELLOW;
        return RED;
    }

    private static int kindColor(Tip.Kind kind) {
        return switch (kind) {
            case FIX -> RED;
            case IMPROVE -> YELLOW;
            case GOOD -> GREEN;
        };
    }

    /** The same hue at a quarter brightness, for badge backgrounds. */
    private static int darken(int color) {
        int r = (color >> 16 & 0xFF) / 4;
        int gr = (color >> 8 & 0xFF) / 4;
        int b = (color & 0xFF) / 4;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }
}
