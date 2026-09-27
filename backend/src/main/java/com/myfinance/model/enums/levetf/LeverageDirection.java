package com.myfinance.model.enums.levetf;

/** Direction of a leveraged ETF relative to its underlying benchmark. Never assumed — always explicit. */
public enum LeverageDirection {
    /** Moves with the benchmark (e.g. 2x/3x long). */
    LONG,
    /** Moves opposite the benchmark (e.g. -1x/-2x inverse). */
    INVERSE
}
