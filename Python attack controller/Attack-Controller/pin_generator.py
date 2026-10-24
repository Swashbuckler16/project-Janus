def generate_pins(start=0, end=9999):
    """
    Generate 4-digit numeric PIN candidates.

    Example:
    0    -> 0000
    1    -> 0001
    42   -> 0042
    9999 -> 9999
    """

    for number in range(start, end + 1):
        yield f"{number:04d}"


if __name__ == "__main__":

    print("Testing PIN generator")
    print("-" * 30)

    for pin in generate_pins(0, 9):
        print(pin)