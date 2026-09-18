package com.emcc.admin.app.util;

import java.util.List;

/** Shared Monthly/Quarterly/Yearly period selection logic used by the report tabs. */
public final class PeriodScope {

    public static final String[] MONTHS_SHORT = {
        "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"
    };
    public static final String[] MONTHS_FULL = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };
    public static final String[] QUARTERS = {
        "Q1 (Jan–Mar)","Q2 (Apr–Jun)","Q3 (Jul–Sep)","Q4 (Oct–Dec)"
    };

    private PeriodScope() {}

    public static List<Integer> scopeMonths(String period, int month, int quarter) {
        return switch (period) {
            case "Monthly"   -> List.of(month);
            case "Quarterly" -> { int s = (quarter - 1) * 3 + 1; yield List.of(s, s + 1, s + 2); }
            default          -> List.of(1,2,3,4,5,6,7,8,9,10,11,12);
        };
    }

    public static String periodLabel(String period, int year, int month, int quarter) {
        return switch (period) {
            case "Monthly"   -> MONTHS_FULL[month - 1] + " " + year;
            case "Quarterly" -> QUARTERS[quarter - 1] + " " + year;
            default          -> String.valueOf(year);
        };
    }

    public static int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return 0;
    }
}
