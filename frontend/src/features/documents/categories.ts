/** The expense categories document AI suggests, by the code it uses, with their Greek names. */
export const categoryLabels: Record<string, string> = {
  FUEL: 'Καύσιμα',
  GROCERIES: 'Τρόφιμα και σούπερ μάρκετ',
  RESTAURANT: 'Εστίαση',
  UTILITIES: 'Ρεύμα, νερό, φυσικό αέριο',
  TELECOM: 'Τηλεφωνία και internet',
  RENT: 'Ενοίκιο',
  OFFICE_SUPPLIES: 'Αναλώσιμα γραφείου',
  TRANSPORT: 'Μεταφορές',
  PROFESSIONAL_SERVICES: 'Αμοιβές τρίτων',
  EQUIPMENT: 'Εξοπλισμός',
}

export function categoryLabel(code: string): string {
  return categoryLabels[code] ?? code
}
