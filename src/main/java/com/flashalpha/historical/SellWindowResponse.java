package com.flashalpha.historical;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed response for {@code GET /v1/strategies/sell-window/{symbol}} with {@code ?at=}: should you sell
 * options today? One answer per session for SPY, QQQ and IWM, built from more than 5
 * million backtests.
 *
 * <p>Own response shape, not the strategy decision envelope. {@link #spot},
 * {@link #quotedAt} and {@link #legsIndicative} are set when a window sells, and null
 * otherwise. Use the Typed client method to obtain an instance.
 *
 * <p>Docs: <a href="https://flashalpha.com/docs/historical-sell-window">https://flashalpha.com/docs/historical-sell-window</a>
 */
public final class SellWindowResponse extends FlashAlphaResponse {

    /** The symbol, upper case. */
    public String symbol;

    /** {@code yyyy-MM-dd}. The trading session the answer applies to. */
    public String session;

    /** Underlying mid the strikes were picked against. */
    public Double spot;

    /** UTC time of the quotes behind the strikes. */
    @SerializedName("quoted_at")
    public String quotedAt;

    /**
     * True when the quotes are from before that session's 09:35 ET entry: call again at or
     * after 09:35 ET for the strikes to trade.
     */
    @SerializedName("legs_indicative")
    public Boolean legsIndicative;

    /** Always four entries: same day, then about one week, two weeks and one month out. */
    public List<Window> windows;

    /** One of the four windows, named by its listed expiry. */
    public static final class Window {
        /** {@code yyyy-MM-dd}. Null when no listed expiry falls in the window's range. */
        public String expiry;

        /** Calendar days from the session to {@link #expiry}. */
        @SerializedName("days_to_expiry")
        public Integer daysToExpiry;

        /** {@code sell}, {@code skip} or {@code unavailable}. Treat unavailable like a skip. */
        public String action;

        /**
         * Set only when {@link #action} is {@code sell}. Can be empty: a trade whose legs
         * cannot all be found in the chain is left out.
         */
        public List<Candidate> candidates;
    }

    /** One trade to consider on a sell window. */
    public static final class Candidate {
        /**
         * {@code short_put}, {@code short_call}, {@code short_strangle}, {@code short_straddle},
         * {@code tail_hedged_put}, {@code put_credit_spread} or {@code put_bwb}.
         */
        public String structure;

        /** {@code expiry}: hold to expiration. {@code tp50}: close at half of the credit, otherwise hold. */
        public String exit;

        /** Null when no option chain could be loaded for the answer. */
        public List<Leg> legs;

        /** Net credit per share at mid. Multiply by 100 for one unit in dollars. */
        @SerializedName("mid_credit")
        public Double midCredit;
    }

    /** One contract of a sell-window trade. */
    public static final class Leg {
        /** {@code sell} or {@code buy}. */
        public String side;

        /** Contracts per unit of the trade: 2 for the sold body of {@code put_bwb}, otherwise 1. */
        public Integer quantity;

        /** {@code call} or {@code put}. */
        public String type;

        public Double strike;

        /** The contract's own delta when the answer was served (negative for puts). */
        public Double delta;

        public Double bid;

        public Double ask;
    }
}
