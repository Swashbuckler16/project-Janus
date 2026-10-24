import csv
from pathlib import Path
from datetime import datetime


RESULTS_DIR = Path("Results")
RESULTS_DIR.mkdir(exist_ok=True)


def create_log_file():
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    filename = RESULTS_DIR / f"experiment_{timestamp}.csv"

    file = open(
        filename,
        "w",
        newline="",
        encoding="utf-8"
    )

    writer = csv.writer(file)

    writer.writerow([
        "timestamp",
        "pin",
        "success",
        "server_attempt",
        "response_time_ms",
        "network_time_ms"
    ])

    return file, writer, filename