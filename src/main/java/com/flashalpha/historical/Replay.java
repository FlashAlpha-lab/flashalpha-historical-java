package com.flashalpha.historical;

import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Backtesting helpers — point-in-time replay loops over the Historical API.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link #iterDays(LocalDate, LocalDate)} — session-close stamps for trading days</li>
 *   <li>{@link #iterMinutes(LocalDate, LocalDate, int)} — RTH minute stamps</li>
 *   <li>{@link #run(FlashAlphaHistoricalClient, AtEndpoint, String, List)} — walk an endpoint</li>
 * </ul>
 */
public final class Replay {

    private static final Set<LocalDate> FULL_CLOSE_HOLIDAYS = new HashSet<>(Arrays.asList(
            // 2018
            LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 15), LocalDate.of(2018, 2, 19),
            LocalDate.of(2018, 3, 30), LocalDate.of(2018, 5, 28), LocalDate.of(2018, 7, 4),
            LocalDate.of(2018, 9, 3), LocalDate.of(2018, 11, 22), LocalDate.of(2018, 12, 5),
            LocalDate.of(2018, 12, 25),
            // 2019
            LocalDate.of(2019, 1, 1), LocalDate.of(2019, 1, 21), LocalDate.of(2019, 2, 18),
            LocalDate.of(2019, 4, 19), LocalDate.of(2019, 5, 27), LocalDate.of(2019, 7, 4),
            LocalDate.of(2019, 9, 2), LocalDate.of(2019, 11, 28), LocalDate.of(2019, 12, 25),
            // 2020
            LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 20), LocalDate.of(2020, 2, 17),
            LocalDate.of(2020, 4, 10), LocalDate.of(2020, 5, 25), LocalDate.of(2020, 7, 3),
            LocalDate.of(2020, 9, 7), LocalDate.of(2020, 11, 26), LocalDate.of(2020, 12, 25),
            // 2021
            LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 18), LocalDate.of(2021, 2, 15),
            LocalDate.of(2021, 4, 2), LocalDate.of(2021, 5, 31), LocalDate.of(2021, 7, 5),
            LocalDate.of(2021, 9, 6), LocalDate.of(2021, 11, 25), LocalDate.of(2021, 12, 24),
            // 2022
            LocalDate.of(2022, 1, 17), LocalDate.of(2022, 2, 21), LocalDate.of(2022, 4, 15),
            LocalDate.of(2022, 5, 30), LocalDate.of(2022, 6, 20), LocalDate.of(2022, 7, 4),
            LocalDate.of(2022, 9, 5), LocalDate.of(2022, 11, 24), LocalDate.of(2022, 12, 26),
            // 2023
            LocalDate.of(2023, 1, 2), LocalDate.of(2023, 1, 16), LocalDate.of(2023, 2, 20),
            LocalDate.of(2023, 4, 7), LocalDate.of(2023, 5, 29), LocalDate.of(2023, 6, 19),
            LocalDate.of(2023, 7, 4), LocalDate.of(2023, 9, 4), LocalDate.of(2023, 11, 23),
            LocalDate.of(2023, 12, 25),
            // 2024
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 2, 19),
            LocalDate.of(2024, 3, 29), LocalDate.of(2024, 5, 27), LocalDate.of(2024, 6, 19),
            LocalDate.of(2024, 7, 4), LocalDate.of(2024, 9, 2), LocalDate.of(2024, 11, 28),
            LocalDate.of(2024, 12, 25),
            // 2025
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 9), LocalDate.of(2025, 1, 20),
            LocalDate.of(2025, 2, 17), LocalDate.of(2025, 4, 18), LocalDate.of(2025, 5, 26),
            LocalDate.of(2025, 6, 19), LocalDate.of(2025, 7, 4), LocalDate.of(2025, 9, 1),
            LocalDate.of(2025, 11, 27), LocalDate.of(2025, 12, 25),
            // 2026
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 19), LocalDate.of(2026, 2, 16),
            LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 19),
            LocalDate.of(2026, 7, 3), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 11, 26),
            LocalDate.of(2026, 12, 25)
    ));

    private Replay() {}

    /** Best-effort NYSE trading-day check: weekday and not a known full-close holiday. */
    public static boolean isTradingDay(LocalDate d) {
        switch (d.getDayOfWeek()) {
            case SATURDAY:
            case SUNDAY:
                return false;
            default:
                return !FULL_CLOSE_HOLIDAYS.contains(d);
        }
    }

    /** Yield one {@link LocalDateTime} per trading day in [start, end] inclusive, stamped at 16:00. */
    public static List<LocalDateTime> iterDays(LocalDate start, LocalDate end) {
        return iterDays(start, end, LocalTime.of(16, 0));
    }

    public static List<LocalDateTime> iterDays(LocalDate start, LocalDate end, LocalTime closeAt) {
        List<LocalDateTime> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isTradingDay(d)) out.add(d.atTime(closeAt));
        }
        return out;
    }

    /** Yield ET wall-clock minute stamps inside RTH for every trading day in [start, end]. */
    public static List<LocalDateTime> iterMinutes(LocalDate start, LocalDate end, int stepMinutes) {
        return iterMinutes(start, end, stepMinutes, LocalTime.of(9, 30), LocalTime.of(16, 0));
    }

    public static List<LocalDateTime> iterMinutes(
            LocalDate start, LocalDate end, int stepMinutes, LocalTime openAt, LocalTime closeAt) {
        if (stepMinutes <= 0) throw new IllegalArgumentException("stepMinutes must be positive");
        List<LocalDateTime> out = new ArrayList<>();
        for (LocalDateTime dayClose : iterDays(start, end, closeAt)) {
            LocalDate d = dayClose.toLocalDate();
            LocalDateTime endStamp = d.atTime(closeAt);
            for (LocalDateTime cur = d.atTime(openAt);
                 !cur.isAfter(endStamp);
                 cur = cur.plusMinutes(stepMinutes)) {
                out.add(cur);
            }
        }
        return out;
    }

    /** Function signature for any client method that takes (symbol, at) → JsonObject. */
    @FunctionalInterface
    public interface AtEndpoint {
        JsonObject call(FlashAlphaHistoricalClient client, String symbol, String at);
    }

    /** One step of a replay run. */
    public static final class Step {
        public final String at;
        public final JsonObject response;
        public Step(String at, JsonObject response) { this.at = at; this.response = response; }
    }

    /** Replay an endpoint over a sequence of timestamps. Skips data-gap days silently by default. */
    public static List<Step> run(
            FlashAlphaHistoricalClient client,
            AtEndpoint endpoint,
            String symbol,
            List<LocalDateTime> timestamps) {
        return run(client, endpoint, symbol, timestamps, true, null);
    }

    public static List<Step> run(
            FlashAlphaHistoricalClient client,
            AtEndpoint endpoint,
            String symbol,
            List<LocalDateTime> timestamps,
            boolean skipMissing,
            BiConsumer<LocalDateTime, FlashAlphaHistoricalException> onError) {
        List<Step> out = new ArrayList<>();
        for (LocalDateTime ts : timestamps) {
            String at = FlashAlphaHistoricalClient.formatAt(ts);
            try {
                out.add(new Step(at, endpoint.call(client, symbol, at)));
            } catch (NoDataException | SymbolNotFoundException | InsufficientDataException ex) {
                if (!skipMissing) throw ex;
                if (onError != null) onError.accept(ts, ex);
            }
        }
        return out;
    }
}
