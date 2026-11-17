"""Synthetic Greek receipts, labelled with their category and their true fields.

There is no public dataset of labelled Greek receipts, so the first models learn from generated
ones. Every receipt is built from known values, so it also measures field extraction: the true
ΑΦΜ, date and amounts are known. Shop names are invented. Real receipts corrected by people will
replace this data as they are collected.

Each category has its own shops. Evaluation keeps whole shops out of training, so a good score
means the model learned what fuel or groceries look like, not the names of the shops it saw.
"""

import random
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal

CATEGORIES: dict[str, dict] = {
    "FUEL": {
        "shops": [
            "ΠΡΑΤΗΡΙΟ ΚΑΥΣΙΜΩΝ ΠΑΠΑΔΟΠΟΥΛΟΣ",
            "ΚΑΥΣΙΜΑ ΒΟΡΕΙΟΥ Ο.Ε.",
            "ΠΕΤΡΕΛΑΙΟΕΙΔΗ ΝΙΚΟΛΑΟΥ",
            "ΠΡΑΤΗΡΙΟ ΑΙΓΑΙΟΥ",
            "ΣΤΑΘΜΟΣ ΚΑΥΣΙΜΩΝ ΟΛΥΜΠΟΣ",
            "ΚΑΥΣΙΜΑ ΚΑΡΑΓΙΑΝΝΗ Ε.Ε.",
            "ΠΡΑΤΗΡΙΟ ΕΘΝΙΚΗΣ ΟΔΟΥ",
            "FUEL POINT ΙΚΕ",
        ],
        "items": [
            ("ΑΜΟΛΥΒΔΗ 95", 1.70, 1.95, True),
            ("ΑΜΟΛΥΒΔΗ 100", 1.85, 2.10, True),
            ("DIESEL", 1.50, 1.75, True),
            ("ΠΕΤΡΕΛΑΙΟ ΚΙΝΗΣΗΣ", 1.50, 1.75, True),
            ("ΥΓΡΑΕΡΙΟ LPG", 0.85, 1.05, True),
            ("ADBLUE", 0.60, 1.20, True),
        ],
        "vat": "0.24",
    },
    "GROCERIES": {
        "shops": [
            "ΣΟΥΠΕΡ ΜΑΡΚΕΤ Η ΓΕΙΤΟΝΙΑ",
            "ΠΑΝΤΟΠΩΛΕΙΟ ΔΗΜΗΤΡΙΟΥ",
            "MINI MARKET ΑΓΙΑ ΣΟΦΙΑ",
            "ΚΑΤΑΣΤΗΜΑ ΤΡΟΦΙΜΩΝ ΣΤΑΘΗΣ",
            "ΧΟΝΔΡΕΜΠΟΡΙΟ ΤΡΟΦΙΜΩΝ ΚΡΗΤΗΣ",
            "ΑΓΟΡΑ ΦΡΕΣΚΩΝ ΠΡΟΪΟΝΤΩΝ",
            "ΜΑΝΑΒΙΚΗ ΤΟΥ ΠΑΖΑΡΙΟΥ",
            "ΚΡΕΟΠΩΛΕΙΟ ΜΑΚΡΗΣ",
        ],
        "items": [
            ("ΓΑΛΑ ΦΡΕΣΚΟ 1L", 1.20, 1.80, False),
            ("ΨΩΜΙ ΧΩΡΙΑΤΙΚΟ", 1.00, 2.50, False),
            ("ΤΥΡΙ ΦΕΤΑ", 4.00, 9.00, False),
            ("ΕΛΑΙΟΛΑΔΟ 1L", 8.00, 12.00, False),
            ("ΝΤΟΜΑΤΕΣ ΚΙΛΟ", 1.50, 3.50, False),
            ("ΚΑΦΕΣ ΕΛΛΗΝΙΚΟΣ", 2.50, 4.50, False),
            ("ΖΑΧΑΡΗ 1KG", 1.10, 1.60, False),
            ("ΜΑΚΑΡΟΝΙΑ", 0.90, 2.20, False),
            ("ΑΠΟΡΡΥΠΑΝΤΙΚΟ", 3.00, 8.00, False),
        ],
        "vat": "0.13",
    },
    "RESTAURANT": {
        "shops": [
            "ΤΑΒΕΡΝΑ ΤΟ ΠΕΥΚΟ",
            "ΨΗΤΟΠΩΛΕΙΟ Ο ΜΑΝΘΟΣ",
            "ΕΣΤΙΑΤΟΡΙΟ ΘΑΛΑΣΣΑ",
            "ΚΑΦΕΤΕΡΙΑ ΠΛΑΤΕΙΑ",
            "ΟΥΖΕΡΙ ΤΟ ΛΙΜΑΝΙ",
            "ΣΝΑΚ ΜΠΑΡ ΓΩΝΙΑ",
            "ΜΕΖΕΔΟΠΩΛΕΙΟ ΑΥΛΗ",
            "ΠΙΤΣΑΡΙΑ ΝΑΠΟΛΗ",
        ],
        "items": [
            ("ΣΟΥΒΛΑΚΙ ΧΟΙΡΙΝΟ", 3.00, 4.50, False),
            ("ΜΟΥΣΑΚΑΣ", 8.00, 12.00, False),
            ("ΣΑΛΑΤΑ ΧΩΡΙΑΤΙΚΗ", 6.00, 9.00, False),
            ("ΦΡΕΝΤΟ ΕΣΠΡΕΣΟ", 2.50, 4.00, False),
            ("ΜΠΥΡΑ 500ML", 3.50, 5.50, False),
            ("ΠΙΤΣΑ ΜΑΡΓΑΡΙΤΑ", 8.00, 12.00, False),
            ("ΚΡΑΣΙ ΧΥΜΑ 1/2", 4.00, 7.00, False),
            ("ΓΥΡΟΣ ΠΙΤΑ", 3.50, 4.50, False),
            ("ΚΟΥΒΕΡ", 0.50, 1.50, False),
        ],
        "vat": "0.13",
    },
    "UTILITIES": {
        "shops": [
            "ΕΤΑΙΡΕΙΑ ΗΛΕΚΤΡΙΣΜΟΥ ΑΤΤΙΚΗΣ Α.Ε.",
            "ΔΗΜΟΤΙΚΗ ΕΠΙΧΕΙΡΗΣΗ ΥΔΡΕΥΣΗΣ",
            "ΠΑΡΟΧΟΣ ΦΥΣΙΚΟΥ ΑΕΡΙΟΥ Α.Ε.",
            "ΕΝΕΡΓΕΙΑΚΗ ΒΟΡΕΙΟΥ ΕΛΛΑΔΟΣ",
            "ΥΔΡΕΥΣΗ ΑΠΟΧΕΤΕΥΣΗ ΠΟΛΗΣ",
            "ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΝΗΣΩΝ",
        ],
        "items": [
            ("ΚΑΤΑΝΑΛΩΣΗ ΡΕΥΜΑΤΟΣ KWH", 40.00, 250.00, False),
            ("ΠΑΓΙΟ ΤΕΛΟΣ", 3.00, 12.00, False),
            ("ΚΑΤΑΝΑΛΩΣΗ ΝΕΡΟΥ M3", 10.00, 80.00, False),
            ("ΦΥΣΙΚΟ ΑΕΡΙΟ KWH", 30.00, 200.00, False),
            ("ΧΡΕΩΣΗ ΔΙΚΤΥΟΥ", 5.00, 30.00, False),
            ("ΤΕΛΟΣ ΑΠΟΧΕΤΕΥΣΗΣ", 5.00, 20.00, False),
        ],
        "vat": "0.06",
    },
    "TELECOM": {
        "shops": [
            "ΤΗΛΕΠΙΚΟΙΝΩΝΙΕΣ ΕΛΛΑΔΟΣ Α.Ε.",
            "ΚΙΝΗΤΗ ΤΗΛΕΦΩΝΙΑ ΔΕΛΤΑ",
            "ΔΙΚΤΥΑ ΙΝΤΕΡΝΕΤ ΑΘΗΝΩΝ",
            "ΣΥΝΔΕΣΗ ΟΠΤΙΚΩΝ ΙΝΩΝ Α.Ε.",
            "ΤΗΛΕΦΩΝΙΑ ΚΑΙ ΔΕΔΟΜΕΝΑ ΙΚΕ",
            "ΠΑΡΟΧΟΣ ΔΙΑΔΙΚΤΥΟΥ ΒΟΡΡΑΣ",
        ],
        "items": [
            ("ΠΑΓΙΟ ΠΡΟΓΡΑΜΜΑ ΚΙΝΗΤΗΣ", 15.00, 45.00, False),
            ("ΣΥΝΔΕΣΗ INTERNET VDSL", 25.00, 45.00, False),
            ("FIBER 200MBPS", 30.00, 50.00, False),
            ("ΧΡΕΩΣΕΙΣ ΚΛΗΣΕΩΝ", 2.00, 20.00, False),
            ("ΔΕΔΟΜΕΝΑ ΚΙΝΗΤΗΣ GB", 5.00, 15.00, False),
            ("ΤΕΛΟΣ ΣΥΝΔΡΟΜΗΤΙΚΗΣ ΤΗΛΕΟΡΑΣΗΣ", 10.00, 30.00, False),
        ],
        "vat": "0.24",
    },
    "RENT": {
        "shops": [
            "ΑΚΙΝΗΤΑ ΠΑΠΑΓΕΩΡΓΙΟΥ",
            "ΕΚΜΙΣΘΩΤΗΣ ΚΩΝΣΤΑΝΤΙΝΟΥ ΜΑΡΙΑ",
            "ΔΙΑΧΕΙΡΙΣΗ ΑΚΙΝΗΤΩΝ ΚΕΝΤΡΟΥ",
            "ΕΜΠΟΡΙΚΟ ΚΕΝΤΡΟ ΑΝΑΤΟΛΗ",
            "ΑΚΙΝΗΤΑ ΣΤΟΑΣ Α.Ε.",
            "ΙΔΙΟΚΤΗΣΙΑ ΒΛΑΧΟΥ ΑΦΟΙ",
        ],
        "items": [
            ("ΜΙΣΘΩΜΑ ΚΑΤΑΣΤΗΜΑΤΟΣ ΜΗΝΟΣ", 400.00, 2500.00, False),
            ("ΕΝΟΙΚΙΟ ΓΡΑΦΕΙΟΥ", 300.00, 1500.00, False),
            ("ΚΟΙΝΟΧΡΗΣΤΑ", 20.00, 150.00, False),
            ("ΜΙΣΘΩΜΑ ΑΠΟΘΗΚΗΣ", 150.00, 800.00, False),
            ("ΕΝΟΙΚΙΟ ΘΕΣΗΣ ΣΤΑΘΜΕΥΣΗΣ", 50.00, 150.00, False),
        ],
        "vat": "0.24",
    },
    "OFFICE_SUPPLIES": {
        "shops": [
            "ΒΙΒΛΙΟΠΩΛΕΙΟ ΓΡΑΦΙΚΗ ΥΛΗ ΑΛΦΑ",
            "ΧΑΡΤΙΚΑ ΚΑΙ ΓΡΑΦΙΚΑ ΒΗΤΑ",
            "ΕΙΔΗ ΓΡΑΦΕΙΟΥ ΣΤΥΛΟ",
            "ΧΑΡΤΟΠΩΛΕΙΟ ΜΕΤΡΟ",
            "ΑΝΑΛΩΣΙΜΑ ΓΡΑΦΕΙΟΥ ΙΚΕ",
            "ΓΡΑΦΙΚΗ ΥΛΗ ΣΧΟΛΕΙΟ",
        ],
        "items": [
            ("ΧΑΡΤΙ Α4 500 ΦΥΛΛΑ", 4.00, 7.00, False),
            ("ΜΕΛΑΝΙ ΕΚΤΥΠΩΤΗ", 15.00, 60.00, False),
            ("ΣΤΥΛΟ ΔΙΑΡΚΕΙΑΣ", 0.50, 2.00, False),
            ("ΝΤΟΣΙΕ ΑΡΧΕΙΟΘΕΤΗΣΗΣ", 2.00, 6.00, False),
            ("ΣΥΡΡΑΠΤΙΚΟ", 4.00, 15.00, False),
            ("TONER LASER", 40.00, 120.00, False),
            ("ΦΑΚΕΛΟΙ ΑΛΛΗΛΟΓΡΑΦΙΑΣ", 2.00, 8.00, False),
            ("ΜΠΛΟΚ ΣΗΜΕΙΩΣΕΩΝ", 1.00, 4.00, False),
        ],
        "vat": "0.24",
    },
    "TRANSPORT": {
        "shops": [
            "ΤΑΞΙ ΑΡ. ΚΥΚΛ. ΙΤΧ 4521",
            "ΚΤΕΛ ΝΟΜΟΥ ΑΤΤΙΚΗΣ",
            "ΑΚΤΟΠΛΟΪΑ ΑΙΓΑΙΟΥ",
            "ΣΤΑΘΜΕΥΣΗ ΚΕΝΤΡΟΥ PARKING",
            "ΔΙΟΔΙΑ ΑΥΤΟΚΙΝΗΤΟΔΡΟΜΟΥ",
            "ΜΕΤΑΦΟΡΙΚΗ ΤΑΧΥ COURIER",
            "ΕΝΟΙΚΙΑΣΕΙΣ ΑΥΤΟΚΙΝΗΤΩΝ ΝΟΤΟΣ",
        ],
        "items": [
            ("ΚΟΥΡΣΑ ΤΑΞΙ", 5.00, 40.00, False),
            ("ΕΙΣΙΤΗΡΙΟ ΛΕΩΦΟΡΕΙΟΥ", 2.00, 25.00, False),
            ("ΕΙΣΙΤΗΡΙΟ ΠΛΟΙΟΥ", 20.00, 80.00, False),
            ("ΣΤΑΘΜΕΥΣΗ ΩΡΕΣ", 2.00, 15.00, False),
            ("ΔΙΟΔΙΑ", 2.00, 10.00, False),
            ("ΑΠΟΣΤΟΛΗ ΔΕΜΑΤΟΣ", 3.00, 15.00, False),
            ("ΜΙΣΘΩΣΗ ΟΧΗΜΑΤΟΣ ΗΜΕΡΑ", 30.00, 90.00, False),
        ],
        "vat": "0.24",
    },
    "PROFESSIONAL_SERVICES": {
        "shops": [
            "ΛΟΓΙΣΤΙΚΟ ΓΡΑΦΕΙΟ ΙΩΑΝΝΟΥ",
            "ΔΙΚΗΓΟΡΙΚΟ ΓΡΑΦΕΙΟ ΣΤΕΦΑΝΟΥ",
            "ΣΥΜΒΟΥΛΟΙ ΕΠΙΧΕΙΡΗΣΕΩΝ ΚΑΠΠΑ",
            "ΤΕΧΝΙΚΟ ΓΡΑΦΕΙΟ ΜΗΧΑΝΙΚΩΝ",
            "ΓΡΑΦΙΣΤΙΚΟ ΣΤΟΥΝΤΙΟ ΧΡΩΜΑ",
            "ΥΠΗΡΕΣΙΕΣ ΠΛΗΡΟΦΟΡΙΚΗΣ ΔΙΚΤΥΟ",
        ],
        "items": [
            ("ΑΜΟΙΒΗ ΛΟΓΙΣΤΙΚΗΣ ΥΠΟΣΤΗΡΙΞΗΣ", 80.00, 300.00, False),
            ("ΝΟΜΙΚΗ ΣΥΜΒΟΥΛΗ", 100.00, 500.00, False),
            ("ΣΥΝΤΑΞΗ ΣΥΜΒΑΣΗΣ", 150.00, 400.00, False),
            ("ΥΠΗΡΕΣΙΕΣ ΣΧΕΔΙΑΣΜΟΥ", 100.00, 600.00, False),
            ("ΤΕΧΝΙΚΗ ΥΠΟΣΤΗΡΙΞΗ ΩΡΕΣ", 40.00, 200.00, False),
            ("ΜΕΛΕΤΗ ΜΗΧΑΝΙΚΟΥ", 200.00, 900.00, False),
        ],
        "vat": "0.24",
    },
    "EQUIPMENT": {
        "shops": [
            "ΗΛΕΚΤΡΟΝΙΚΑ ΤΕΧΝΟΠΟΛΙΣ",
            "ΚΑΤΑΣΤΗΜΑ ΥΠΟΛΟΓΙΣΤΩΝ BYTE",
            "ΕΠΑΓΓΕΛΜΑΤΙΚΟΣ ΕΞΟΠΛΙΣΜΟΣ ΚΟΥΖΙΝΑΣ",
            "ΕΠΙΠΛΑ ΓΡΑΦΕΙΟΥ ΕΡΓΟΝΟΜΙΑ",
            "ΣΙΔΗΡΙΚΑ ΕΡΓΑΛΕΙΑ ΜΑΣΤΟΡΑΣ",
            "ΤΑΜΕΙΑΚΕΣ ΜΗΧΑΝΕΣ POS ΙΚΕ",
        ],
        "items": [
            ("ΦΟΡΗΤΟΣ ΥΠΟΛΟΓΙΣΤΗΣ", 450.00, 1500.00, False),
            ("ΟΘΟΝΗ 27 ΙΝΤΣΩΝ", 150.00, 400.00, False),
            ("ΕΚΤΥΠΩΤΗΣ ΠΟΛΥΜΗΧΑΝΗΜΑ", 120.00, 400.00, False),
            ("ΚΑΡΕΚΛΑ ΓΡΑΦΕΙΟΥ", 80.00, 300.00, False),
            ("ΨΥΓΕΙΟ ΒΙΤΡΙΝΑ", 600.00, 2000.00, False),
            ("ΔΡΑΠΑΝΟ ΜΠΑΤΑΡΙΑΣ", 60.00, 200.00, False),
            ("ΤΑΜΕΙΑΚΗ ΜΗΧΑΝΗ", 200.00, 450.00, False),
            ("ΜΗΧΑΝΗ ΕΣΠΡΕΣΟ ΕΠΑΓΓΕΛΜΑΤΙΚΗ", 800.00, 3000.00, False),
        ],
        "vat": "0.24",
    },
}

CITIES = ["ΑΘΗΝΑ", "ΘΕΣΣΑΛΟΝΙΚΗ", "ΠΑΤΡΑ", "ΗΡΑΚΛΕΙΟ", "ΛΑΡΙΣΑ", "ΒΟΛΟΣ", "ΙΩΑΝΝΙΝΑ", "ΧΑΝΙΑ", "ΚΑΛΑΜΑΤΑ", "ΣΕΡΡΕΣ"]
STREETS = ["ΕΡΜΟΥ", "ΑΘΗΝΑΣ", "ΤΣΙΜΙΣΚΗ", "ΚΟΡΙΝΘΟΥ", "ΑΓΙΟΥ ΝΙΚΟΛΑΟΥ", "ΕΘΝ. ΑΝΤΙΣΤΑΣΕΩΣ", "25ΗΣ ΜΑΡΤΙΟΥ"]
TAX_OFFICES = ["Α' ΑΘΗΝΩΝ", "ΙΓ' ΑΘΗΝΩΝ", "Δ' ΘΕΣΣΑΛΟΝΙΚΗΣ", "ΠΑΤΡΩΝ", "ΗΡΑΚΛΕΙΟΥ", "ΛΑΡΙΣΑΣ", "ΦΑΕ ΑΘΗΝΩΝ"]

GENERIC_ITEMS = ["ΔΙΑΦΟΡΑ ΕΙΔΗ", "ΕΙΔΗ ΚΑΤΑΣΤΗΜΑΤΟΣ", "ΠΩΛΗΣΗ", "ΥΠΗΡΕΣΙΕΣ", "ΤΜΗΜΑ 1", "ΤΜΗΜΑ 2"]
GENERIC_SHARE = 0.25

# OCR confuses these; adding a few such errors teaches the model to cope with them
_OCR_CONFUSIONS = {"Ο": "0", "0": "Ο", "Ι": "1", "1": "Ι", "Ε": "Ξ", "Η": "Ν", "Π": "Τ", "Ρ": "Ρ", "Β": "8", "5": "S"}

CENT = Decimal("0.01")


@dataclass(frozen=True)
class Receipt:
    text: str
    category: str
    shop: str
    afm: str
    issue_date: date
    net: Decimal
    vat: Decimal
    total: Decimal


def random_afm(rng: random.Random) -> str:
    first = "".join(str(rng.randint(0, 9)) for _ in range(8))
    check = sum(int(d) << (8 - i) for i, d in enumerate(first)) % 11 % 10
    return first + str(check)


def _money(value: Decimal, english: bool) -> str:
    """1234.5 as 1.234,50 (Greek) or 1,234.50 (English)."""
    text = f"{value:,.2f}"
    return text if english else text.replace(",", "_").replace(".", ",").replace("_", ".")


def _ocr_noise(text: str, rng: random.Random, rate: float) -> str:
    return "".join(_OCR_CONFUSIONS.get(c, c) if rng.random() < rate else c for c in text)


def make_receipt(category: str, shop: str, rng: random.Random, noise: float = 0.02) -> Receipt:
    spec = CATEGORIES[category]
    vat_rate = Decimal(spec["vat"])
    afm = random_afm(rng)
    issued = date(2025, 1, 1) + timedelta(days=rng.randint(0, 680))
    english = rng.random() < 0.15

    lines = [shop, f"{rng.choice(STREETS)} {rng.randint(1, 180)}, {rng.choice(CITIES)}"]
    lines.append(
        rng.choice([f"Α.Φ.Μ.: {afm}", f"ΑΦΜ {afm} ΔΟΥ {rng.choice(TAX_OFFICES)}", f"AFM: {afm}", f"ΑΦΜ:{afm}"])
    )
    if rng.random() < 0.3:
        lines.append(f"ΤΗΛ. 2{rng.randint(10, 99)}{rng.randint(1000000, 9999999)}")
    lines.append(
        rng.choice(["ΑΠΟΔΕΙΞΗ ΛΙΑΝΙΚΗΣ ΠΩΛΗΣΗΣ", "ΑΠΟΔΕΙΞΗ ΠΑΡΟΧΗΣ ΥΠΗΡΕΣΙΩΝ", "ΤΙΜΟΛΟΓΙΟ ΠΩΛΗΣΗΣ", "ΑΠΟΔΕΙΞΗ"])
    )
    date_text = issued.strftime(rng.choice(["%d/%m/%Y", "%d-%m-%Y", "%d/%m/%y", "%d.%m.%Y"]))
    lines.append(
        rng.choice(
            [
                f"ΗΜΕΡΟΜΗΝΙΑ: {date_text}",
                f"ΗΜ/ΝΙΑ {date_text} {rng.randint(7, 22):02d}:{rng.randint(0, 59):02d}",
                date_text,
            ]
        )
    )

    items = rng.sample(spec["items"], k=min(len(spec["items"]), rng.randint(1, 4)))
    if rng.random() < GENERIC_SHARE:
        # Many tills print only a generic line; then the shop's name is the only clue to the category
        items = [(rng.choice(GENERIC_ITEMS), low, high, False) for _, low, high, _ in items[:1]]

    total = Decimal("0")
    for name, low, high, per_litre in items:
        price = Decimal(str(round(rng.uniform(low, high), 2)))
        quantity = Decimal(str(round(rng.uniform(10, 60), 2))) if per_litre else Decimal(rng.randint(1, 3))
        amount = (price * quantity).quantize(CENT, ROUND_HALF_UP)
        total += amount
        lines.append(f"{name} {quantity} X {_money(price, english)} {_money(amount, english)}")

    # Prices on Greek receipts include VAT; the net and VAT are worked back from the total
    net = (total / (1 + vat_rate)).quantize(CENT, ROUND_HALF_UP)
    vat = total - net
    percent = f"{int(vat_rate * 100)}%"
    if rng.random() < 0.8:
        lines.append(rng.choice([f"ΚΑΘΑΡΗ ΑΞΙΑ {_money(net, english)}", f"ΑΞΙΑ ΠΡΟ ΦΠΑ {_money(net, english)}"]))
    lines.append(rng.choice([f"ΦΠΑ {percent} {_money(vat, english)}", f"Φ.Π.Α. {percent} {_money(vat, english)}"]))
    lines.append(
        rng.choice(
            [
                f"ΣΥΝΟΛΟ {_money(total, english)}",
                f"ΠΛΗΡΩΤΕΟ ΠΟΣΟ {_money(total, english)}",
                f"ΓΕΝΙΚΟ ΣΥΝΟΛΟ € {_money(total, english)}",
            ]
        )
    )
    lines.append(rng.choice(["ΜΕΤΡΗΤΑ", "ΚΑΡΤΑ", "ΠΛΗΡΩΜΗ ΜΕ ΚΑΡΤΑ"]))
    lines.append("ΕΥΧΑΡΙΣΤΟΥΜΕ ΠΟΛΥ")

    text = "\n".join(lines)
    if rng.random() < 0.3:
        text = text.lower()
    return Receipt(_ocr_noise(text, rng, noise), category, shop, afm, issued, net, vat, total)


def generate(per_category: int, seed: int = 7, noise: float = 0.02) -> list[Receipt]:
    rng = random.Random(seed)
    receipts = []
    for category, spec in CATEGORIES.items():
        for i in range(per_category):
            receipts.append(make_receipt(category, spec["shops"][i % len(spec["shops"])], rng, noise))
    rng.shuffle(receipts)
    return receipts
