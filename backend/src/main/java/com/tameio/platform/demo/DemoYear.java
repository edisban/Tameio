package com.tameio.platform.demo;

import com.tameio.platform.validation.Iban;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A believable year of money in and out for a demo business, so a local stack has something to show.
 * Pure and deterministic: the same profile, end date and seed always give the same movements, so the
 * banking module (account statement) and the accounting module (expense book) build matching records
 * from it without talking to each other.
 *
 * <p>Every figure is invented. Suppliers and clients are fictional; their ΑΦΜ and IBANs only follow the
 * check-digit rules.
 */
public final class DemoYear {

    /** The kinds of business the demo has, each with its own takings, costs and seasons. */
    public enum Profile { CAFE, BAKERY, STUDIO, TAVERNA }

    public enum Flow { INCOME, EXPENSE }

    /**
     * One movement of money.
     *
     * @param category for an expense, an expense category code (FUEL, RENT …); null for income
     */
    public record Movement(LocalDate date, Flow flow, String category, String counterparty, String counterpartyAfm,
                           String counterpartyIban, BigDecimal net, BigDecimal vat, BigDecimal total, String reference) {
    }

    /**
     * A year: what the business starts with, then its movements, oldest first. The opening capital is
     * enough that the balance never goes below zero on any day.
     */
    public record Year(BigDecimal openingCapital, List<Movement> movements) {
    }

    /** A cost that comes back every month or week, with its usual size, spread and VAT rate. */
    private record Recurring(String category, String supplier, Cadence cadence, int day, double amount, double spread, double vatRate) {
    }

    /** A cost that happens once, in a given month counted back from the end (0 is the last month). */
    private record OneOff(String category, String supplier, int monthsBeforeEnd, double amount, double vatRate, String reference) {
    }

    private enum Cadence { MONTHLY, WEEKLY, TWICE_WEEKLY }

    private DemoYear() {
    }

    /** What the business usually starts the year with; raised when a quiet start would overdraw. */
    private static BigDecimal baseCapital(Profile profile) {
        return switch (profile) {
            case CAFE -> new BigDecimal("18000.00");
            case BAKERY -> new BigDecimal("25000.00");
            case STUDIO -> new BigDecimal("12000.00");
            case TAVERNA -> new BigDecimal("30000.00");
        };
    }

    /** The twelve months ending on {@code end}. */
    public static Year plan(Profile profile, LocalDate end, long seed) {
        List<Movement> movements = movements(profile, end, seed);
        BigDecimal balance = baseCapital(profile);
        BigDecimal lowest = balance;
        for (Movement m : movements) {
            balance = m.flow() == Flow.INCOME ? balance.add(m.total()) : balance.subtract(m.total());
            lowest = lowest.min(balance);
        }
        // Top up to the next thousand above the deepest dip, keeping a cushion of a thousand
        BigDecimal topUp = lowest.compareTo(new BigDecimal("1000")) >= 0 ? BigDecimal.ZERO
            : new BigDecimal("1000").subtract(lowest).divide(new BigDecimal("1000"), 0, RoundingMode.CEILING).multiply(new BigDecimal("1000"));
        return new Year(baseCapital(profile).add(topUp).setScale(2), movements);
    }

    private static List<Movement> movements(Profile profile, LocalDate end, long seed) {
        Random random = new Random(seed);
        LocalDate start = end.minusMonths(12).plusDays(1);
        List<Movement> movements = new ArrayList<>();

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            income(profile, day, random, movements);
            for (Recurring cost : recurring(profile)) {
                if (falls(cost, day)) {
                    double amount = cost.amount() * season(profile, day, cost.category()) * (1 + (random.nextDouble() * 2 - 1) * cost.spread());
                    movements.add(expense(day, cost.category(), cost.supplier(), amount, cost.vatRate(), reference(cost, day)));
                }
            }
        }
        for (OneOff cost : oneOffs(profile)) {
            LocalDate day = end.minusMonths(cost.monthsBeforeEnd()).withDayOfMonth(Math.min(12, end.getDayOfMonth()));
            if (!day.isBefore(start)) {
                movements.add(expense(day, cost.category(), cost.supplier(), cost.amount(), cost.vatRate(), cost.reference()));
            }
        }
        movements.sort(Comparator.comparing(Movement::date).thenComparing(m -> m.flow() == Flow.EXPENSE));
        return movements;
    }

    // Takings

    private static final String[] STUDIO_CLIENTS = {
        "ΟΙΝΟΠΟΙΕΙΑ ΚΥΚΛΑΔΩΝ Α.Ε.", "ΞΕΝΟΔΟΧΕΙΑ ΘΑΛΑΣΣΑ Α.Ε.", "ΒΙΟΛΟΓΙΚΑ ΠΡΟΪΟΝΤΑ ΗΠΕΙΡΟΥ Ο.Ε.",
        "ΣΧΟΛΗ ΧΟΡΟΥ ΡΥΘΜΟΣ", "ΚΤΗΜΑ ΕΛΙΑΣ Ι.Κ.Ε.", "ΤΕΧΝΙΚΗ ΕΤΑΙΡΕΙΑ ΔΟΜΗ Α.Τ.Ε.",
    };

    private static void income(Profile profile, LocalDate day, Random random, List<Movement> out) {
        if (profile == Profile.STUDIO) {
            // A design studio is paid by invoice, a few times a month
            if (day.getDayOfWeek().getValue() <= 5 && random.nextDouble() < 0.19 * season(profile, day, null)) {
                String client = STUDIO_CLIENTS[random.nextInt(STUDIO_CLIENTS.length)];
                double amount = 650 + random.nextDouble() * 3_900;
                out.add(new Movement(day, Flow.INCOME, null, client, afm(client), iban(client), money(amount), BigDecimal.ZERO.setScale(2),
                    money(amount), "Τιμολόγιο ΤΠΥ " + (100 + random.nextInt(900))));
            }
            return;
        }
        double base = switch (profile) {
            case CAFE -> 420;
            case BAKERY -> 640;
            case TAVERNA -> 900;
            default -> 0;
        };
        double weekday = switch (day.getDayOfWeek()) {
            case SATURDAY -> 1.45;
            case SUNDAY -> profile == Profile.BAKERY ? 0.55 : 1.35;
            case FRIDAY -> 1.15;
            default -> 1.0;
        };
        double season = season(profile, day, null);
        if (season == 0) {
            return;
        }
        double amount = base * weekday * season * (0.8 + random.nextDouble() * 0.4);
        String payer = "Εισπράξεις ημέρας (κάρτες POS)";
        out.add(new Movement(day, Flow.INCOME, null, payer, null, iban("ACQUIRER"), money(amount), BigDecimal.ZERO.setScale(2),
            money(amount), "POS " + day));
    }

    /** How busy a month is, 1 being ordinary. The taverna is on an island: shut in January and February. */
    private static double season(Profile profile, LocalDate day, String category) {
        int m = day.getMonthValue() - 1;
        double[] factors = switch (profile) {
            case CAFE -> new double[] {0.85, 0.85, 0.95, 1.0, 1.1, 1.25, 1.35, 1.3, 1.1, 1.0, 0.9, 1.05};
            case BAKERY -> new double[] {1.0, 0.95, 1.05, 1.25, 1.0, 0.95, 0.9, 0.85, 1.0, 1.0, 1.0, 1.3};
            case STUDIO -> new double[] {0.8, 1.0, 1.1, 1.15, 1.1, 1.0, 0.7, 0.45, 1.2, 1.25, 1.2, 1.0};
            case TAVERNA -> new double[] {0, 0, 0.3, 0.6, 1.0, 1.5, 2.0, 2.1, 1.3, 0.7, 0.35, 0.3};
        };
        double factor = factors[m];
        // Rent and the like do not follow the season; supplies and energy do, but never stop entirely
        if ("RENT".equals(category) || "TELECOM".equals(category) || "PROFESSIONAL_SERVICES".equals(category)) {
            return 1;
        }
        return category == null ? factor : Math.max(0.35, factor);
    }

    // Costs

    private static List<Recurring> recurring(Profile profile) {
        return switch (profile) {
            case CAFE -> List.of(
                new Recurring("RENT", "ΚΑΤΣΑΡΟΣ ΙΩΑΝΝΗΣ", Cadence.MONTHLY, 1, 1_200, 0, 0),
                new Recurring("UTILITIES", "ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΑΙΓΑΙΟΥ Α.Ε.", Cadence.MONTHLY, 18, 290, 0.2, 0.06),
                new Recurring("TELECOM", "ΤΗΛΕΠΙΚΟΙΝΩΝΙΕΣ ΑΣΤΕΡΙ Α.Ε.", Cadence.MONTHLY, 22, 49.90, 0, 0.24),
                new Recurring("GROCERIES", "ΚΑΦΕΚΟΠΤΕΙΟ ΑΡΩΜΑ Ο.Ε.", Cadence.WEEKLY, 2, 340, 0.25, 0.13),
                new Recurring("GROCERIES", "ΓΑΛΑΚΤΟΚΟΜΙΚΑ ΒΟΡΕΙΟΥ Α.Ε.", Cadence.WEEKLY, 4, 180, 0.3, 0.13),
                new Recurring("PROFESSIONAL_SERVICES", "ΛΟΓΙΣΤΙΚΟ ΓΡΑΦΕΙΟ ΜΕΡΙΣΜΑ", Cadence.MONTHLY, 5, 250, 0, 0.24),
                new Recurring("OFFICE_SUPPLIES", "ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.", Cadence.MONTHLY, 10, 85, 0.4, 0.24));
            case BAKERY -> List.of(
                new Recurring("RENT", "ΔΗΜΗΤΡΙΟΥ ΑΙΚΑΤΕΡΙΝΗ", Cadence.MONTHLY, 1, 1_500, 0, 0),
                new Recurring("UTILITIES", "ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΑΙΓΑΙΟΥ Α.Ε.", Cadence.MONTHLY, 18, 640, 0.15, 0.06),
                new Recurring("TELECOM", "ΤΗΛΕΠΙΚΟΙΝΩΝΙΕΣ ΑΣΤΕΡΙ Α.Ε.", Cadence.MONTHLY, 22, 39.90, 0, 0.24),
                new Recurring("GROCERIES", "ΑΛΕΥΡΟΜΥΛΟΙ ΘΕΣΣΑΛΙΑΣ Α.Ε.", Cadence.TWICE_WEEKLY, 1, 520, 0.2, 0.13),
                new Recurring("FUEL", "ΚΑΥΣΙΜΑ ΚΑΡΑΓΙΑΝΝΗ Ε.Ε.", Cadence.WEEKLY, 5, 72, 0.25, 0.24),
                new Recurring("PROFESSIONAL_SERVICES", "ΛΟΓΙΣΤΙΚΟ ΓΡΑΦΕΙΟ ΜΕΡΙΣΜΑ", Cadence.MONTHLY, 5, 300, 0, 0.24));
            case STUDIO -> List.of(
                new Recurring("RENT", "ΣΤΑΥΡΟΥ ΕΛΕΝΗ", Cadence.MONTHLY, 1, 900, 0, 0),
                new Recurring("TELECOM", "ΤΗΛΕΠΙΚΟΙΝΩΝΙΕΣ ΑΣΤΕΡΙ Α.Ε.", Cadence.MONTHLY, 22, 79.90, 0, 0.24),
                new Recurring("UTILITIES", "ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΑΙΓΑΙΟΥ Α.Ε.", Cadence.MONTHLY, 18, 95, 0.25, 0.06),
                new Recurring("OFFICE_SUPPLIES", "ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.", Cadence.MONTHLY, 12, 120, 0.5, 0.24),
                new Recurring("PROFESSIONAL_SERVICES", "ΓΕΩΡΓΙΟΥ ΝΙΚΟΛΑΟΣ", Cadence.MONTHLY, 25, 1_100, 0.35, 0.24),
                new Recurring("TRANSPORT", "ΜΕΤΑΦΟΡΙΚΗ ΘΕΣΣΑΛΙΑΣ Ι.Κ.Ε.", Cadence.MONTHLY, 15, 140, 0.5, 0.24),
                new Recurring("RESTAURANT", "ΤΑΒΕΡΝΑ ΤΟ ΛΙΜΑΝΑΚΙ", Cadence.MONTHLY, 20, 95, 0.5, 0.13));
            case TAVERNA -> List.of(
                new Recurring("RENT", "ΚΑΤΣΑΡΟΣ ΙΩΑΝΝΗΣ", Cadence.MONTHLY, 1, 1_800, 0, 0),
                new Recurring("UTILITIES", "ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΑΙΓΑΙΟΥ Α.Ε.", Cadence.MONTHLY, 18, 520, 0.2, 0.06),
                new Recurring("TELECOM", "ΤΗΛΕΠΙΚΟΙΝΩΝΙΕΣ ΑΣΤΕΡΙ Α.Ε.", Cadence.MONTHLY, 22, 49.90, 0, 0.24),
                new Recurring("GROCERIES", "ΑΛΙΕΙΑ ΑΙΓΑΙΟΥ Ο.Ε.", Cadence.TWICE_WEEKLY, 2, 620, 0.3, 0.13),
                new Recurring("GROCERIES", "ΚΡΕΟΠΩΛΕΙΟ ΤΟ ΒΟΣΚΟΤΟΠΙ", Cadence.WEEKLY, 3, 540, 0.3, 0.13),
                new Recurring("FUEL", "ΚΑΥΣΙΜΑ ΚΑΡΑΓΙΑΝΝΗ Ε.Ε.", Cadence.WEEKLY, 6, 60, 0.3, 0.24),
                new Recurring("PROFESSIONAL_SERVICES", "ΛΟΓΙΣΤΙΚΟ ΓΡΑΦΕΙΟ ΜΕΡΙΣΜΑ", Cadence.MONTHLY, 5, 280, 0, 0.24));
        };
    }

    /** The big purchases of the year; the one in each profile is what an anomaly check should notice. */
    private static List<OneOff> oneOffs(Profile profile) {
        return switch (profile) {
            case CAFE -> List.of(new OneOff("EQUIPMENT", "ΕΞΟΠΛΙΣΜΟΣ ΚΑΦΕ ΤΕΧΝΙΚΗ Α.Ε.", 8, 3_850, 0.24, "Μηχανή espresso 2 δόσεων"));
            case BAKERY -> List.of(new OneOff("EQUIPMENT", "ΦΟΥΡΝΟΙ ΕΛΛΑΔΟΣ Α.Ε.", 2, 6_480, 0.24, "Φούρνος καμπίνας"));
            case STUDIO -> List.of(new OneOff("EQUIPMENT", "ΠΛΗΡΟΦΟΡΙΚΗ ΣΥΣΤΗΜΑΤΑ Ι.Κ.Ε.", 6, 2_390, 0.24, "Φορητός υπολογιστής"));
            case TAVERNA -> List.of(new OneOff("EQUIPMENT", "ΨΥΚΤΙΚΑ ΝΗΣΙΩΝ Ο.Ε.", 7, 4_200, 0.24, "Ψυκτικός θάλαμος"));
        };
    }

    private static boolean falls(Recurring cost, LocalDate day) {
        return switch (cost.cadence()) {
            case MONTHLY -> day.getDayOfMonth() == Math.min(cost.day(), day.lengthOfMonth());
            case WEEKLY -> day.getDayOfWeek().getValue() == cost.day();
            case TWICE_WEEKLY -> day.getDayOfWeek() == DayOfWeek.of(cost.day()) || day.getDayOfWeek() == DayOfWeek.of(cost.day() + 3);
        };
    }

    private static String reference(Recurring cost, LocalDate day) {
        return switch (cost.category()) {
            case "RENT" -> "Ενοίκιο " + day.getMonthValue() + "/" + day.getYear();
            case "UTILITIES" -> "Λογαριασμός ρεύματος " + day.getMonthValue() + "/" + day.getYear();
            case "TELECOM" -> "Πάγιο " + day.getMonthValue() + "/" + day.getYear();
            default -> "Τιμολόγιο " + (1_000 + Math.floorMod(cost.supplier().hashCode() + day.getDayOfYear(), 9_000));
        };
    }

    private static Movement expense(LocalDate day, String category, String supplier, double amount, double vatRate, String reference) {
        BigDecimal total = money(amount);
        BigDecimal net = total.divide(BigDecimal.valueOf(1 + vatRate), 2, RoundingMode.HALF_UP);
        return new Movement(day, Flow.EXPENSE, category, supplier, afm(supplier), iban(supplier), net, total.subtract(net), total, reference);
    }

    private static BigDecimal money(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /** A valid ΑΦΜ derived from the name, so a supplier keeps the same one all year. */
    public static String afm(String name) {
        long seed = Math.floorMod(name.hashCode(), 90_000_000L) + 10_000_000L;
        String first = Long.toString(seed);
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (first.charAt(i) - '0') << (8 - i);
        }
        return first + sum % 11 % 10;
    }

    /** A valid Greek IBAN at one of the fictional demo banks (codes 991 to 994), derived from the name. */
    static String iban(String name) {
        int bank = 991 + Math.floorMod(name.hashCode(), 4);
        long account = Math.floorMod((long) name.hashCode() * 31 + 7, 9_000_000_000L) + 1_000_000_000L;
        return Iban.of("GR", bank + "0001" + "%016d".formatted(account));
    }
}
