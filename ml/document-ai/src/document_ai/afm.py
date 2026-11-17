"""The Greek tax number (ΑΦΜ): nine digits, the last a check digit over the first eight."""


def is_valid_afm(value: str) -> bool:
    if len(value) != 9 or not value.isdigit() or value == "000000000":
        return False
    total = sum(int(digit) << (8 - i) for i, digit in enumerate(value[:8]))
    return total % 11 % 10 == int(value[8])
