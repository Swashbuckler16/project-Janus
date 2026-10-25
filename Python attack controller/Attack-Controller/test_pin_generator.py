import unittest
from pin_generator import generate_pins


class TestPinGenerator(unittest.TestCase):

    def test_range_generation(self):
        pins = list(generate_pins(0, 5))
        expected = ["0000", "0001", "0002", "0003", "0004", "0005"]
        self.assertEqual(pins, expected)

    def test_zero_padding(self):
        pins = list(generate_pins(42, 42))
        self.assertEqual(pins, ["0042"])

    def test_full_keyspace_count(self):
        pins = list(generate_pins(0, 9999))
        self.assertEqual(len(pins), 10000)
        self.assertEqual(pins[0], "0000")
        self.assertEqual(pins[-1], "9999")


if __name__ == "__main__":
    unittest.main()
