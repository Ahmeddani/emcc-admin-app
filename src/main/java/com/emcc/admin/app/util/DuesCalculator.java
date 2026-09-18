package com.emcc.admin.app.util;

import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

/**
 * Single source of truth for "does this member owe dues for this month" —
 * used by the Dashboard, Payments, and Reports pages so the answer is
 * consistent everywhere instead of each page guessing independently from
 * raw Payment rows.
 */
public final class DuesCalculator {

    public enum Status { PAID, UNDERPAID, MISSING, NOT_DUE, NO_DUES_SET }

    private DuesCalculator() {}

    public static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace("$", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static BigDecimal duesFor(Member member) {
        return parseAmount(member.getMonthlyDues());
    }

    /** True once the member has joined and the given month is not in the future. */
    public static boolean isActive(Member member, int year, int month, LocalDate today) {
        YearMonth target = YearMonth.of(year, month);
        if (target.isAfter(YearMonth.from(today))) return false;
        LocalDate joined = parseDate(member.getCreatedDate());
        return joined == null || !joined.isAfter(target.atEndOfMonth());
    }

    public static boolean hasActiveMonthInScope(Member member, int year, Collection<Integer> months, LocalDate today) {
        for (int m : months) {
            if (isActive(member, year, m, today)) return true;
        }
        return false;
    }

    public static BigDecimal amountPaid(Member member, List<Payment> payments, int year, int month) {
        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : payments) {
            if (!"Completed".equalsIgnoreCase(p.getStatus())) continue;
            if (!matches(member, p)) continue;
            LocalDate d = parseDate(p.getDate());
            if (d == null || d.getYear() != year || d.getMonthValue() != month) continue;
            total = total.add(parseAmount(p.getAmount()));
        }
        return total;
    }

    public static Status statusFor(Member member, List<Payment> payments, int year, int month, LocalDate today) {
        if (!isActive(member, year, month, today)) return Status.NOT_DUE;
        BigDecimal dues = duesFor(member);
        if (dues.signum() <= 0) return Status.NO_DUES_SET;
        BigDecimal paid = amountPaid(member, payments, year, month);
        if (paid.compareTo(dues) >= 0) return Status.PAID;
        if (paid.signum() > 0) return Status.UNDERPAID;
        return Status.MISSING;
    }

    /** True if the member made at least one completed payment within the given months. */
    public static boolean hasPaidInScope(Member member, List<Payment> payments, int year, Collection<Integer> months) {
        for (Payment p : payments) {
            if (!"Completed".equalsIgnoreCase(p.getStatus())) continue;
            if (!matches(member, p)) continue;
            LocalDate d = parseDate(p.getDate());
            if (d != null && d.getYear() == year && months.contains(d.getMonthValue())) return true;
        }
        return false;
    }

    private static boolean matches(Member member, Payment p) {
        String memberId = member.getMembershipId();
        if (memberId != null && !memberId.isEmpty() && memberId.equals(p.getMemberId())) return true;
        String name = member.getName();
        return name != null && !name.isEmpty() && name.equals(p.getMemberName());
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
